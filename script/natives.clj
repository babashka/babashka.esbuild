(ns natives
  "Builds the esbuild shim into libesbuild/resources.

  bb natives        builds for this machine
  bb natives --all  cross compiles every platform, needs zig"
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def targets
  "Every platform in the libesbuild jar. The darwin C compilers come with
  Xcode, the others with zig. A glibc version pins the oldest Linux the
  library loads on."
  [{:platform "darwin-aarch64" :lib "libesbuild.dylib" :goos "darwin" :goarch "arm64"
    :cc ["clang" "-arch" "arm64"]}
   {:platform "darwin-x86_64" :lib "libesbuild.dylib" :goos "darwin" :goarch "amd64"
    :cc ["clang" "-arch" "x86_64"]}
   {:platform "linux-aarch64" :lib "libesbuild.so" :goos "linux" :goarch "arm64"
    :cc ["zig" "cc" "-target" "aarch64-linux-gnu.2.17"]}
   {:platform "linux-x86_64" :lib "libesbuild.so" :goos "linux" :goarch "amd64"
    :cc ["zig" "cc" "-target" "x86_64-linux-gnu.2.17"]}
   {:platform "windows-x86_64" :lib "esbuild.dll" :goos "windows" :goarch "amd64"
    :cc ["zig" "cc" "-target" "x86_64-windows-gnu"]}])

(def dir "libesbuild")

(defn esbuild-version []
  (str/trim (:out (p/shell {:out :string :dir dir}
                           "go list -m -f {{.Version}} github.com/evanw/esbuild"))))

(defn host-platform []
  (let [os (str/lower-case (System/getProperty "os.name"))
        arch (System/getProperty "os.arch")]
    (str (cond (str/includes? os "mac") "darwin"
               (str/includes? os "win") "windows"
               :else "linux")
         "-"
         (if (#{"aarch64" "arm64"} arch) "aarch64" "x86_64"))))

(defn build-target [{:keys [platform lib goos goarch cc]} version]
  (let [out (fs/path "resources" "babashka" "esbuild" platform lib)
        host? (= platform (host-platform))]
    (fs/delete-tree (fs/parent (fs/path dir out)))
    (fs/create-dirs (fs/parent (fs/path dir out)))
    (println "building" platform)
    (p/shell {:dir dir
              :extra-env (cond-> {"CGO_ENABLED" "1" "GOOS" goos "GOARCH" goarch}
                           (not host?) (assoc "CC" (str/join " " cc)))}
             "go" "build" "-buildmode=c-shared"
             "-ldflags" (str "-s -w -X main.esbuildVersion=" version)
             "-o" (str out) "shim.go")
    (fs/delete-if-exists (fs/path dir (str/replace (str out) #"\.\w+$" ".h")))
    (println " " (format "%.1f MB" (/ (fs/size (fs/path dir out)) 1048576.0)))))

(defn build
  "Builds the esbuild shim into libesbuild/resources."
  {:org.babashka/cli {:spec {:all {:desc "Cross compile every platform, needs zig"
                                   :coerce :boolean}}}}
  [{:keys [all]}]
  (when (and all (not (fs/which "zig")))
    (println "--all needs zig for the linux and windows targets: brew install zig")
    (System/exit 1))
  (let [version (esbuild-version)
        chosen (if all targets (filter #(= (host-platform) (:platform %)) targets))]
    (println "esbuild" version)
    (run! #(build-target % version) chosen)))
