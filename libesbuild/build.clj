(ns build
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.github.babashka/libesbuild)
(def version "0.28.2-2")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn- size [f]
  (let [n (.length (java.io.File. (str f)))]
    (if (< n 1048576)
      (format "%.0f KB" (/ n 1024.0))
      (format "%.1f MB" (/ n 1048576.0)))))

(defn- m2-dir []
  (str (java.io.File. (System/getProperty "user.home")
                      (str ".m2/repository/" (str/replace (namespace lib) "." "/")
                           "/" (name lib) "/" version))))

(def esbuild-version
  (str "v" (first (str/split version #"-"))))

(def shared-libraries
  #{"babashka/esbuild/darwin-aarch64/libesbuild.dylib"
    "babashka/esbuild/darwin-x86_64/libesbuild.dylib"
    "babashka/esbuild/linux-aarch64/libesbuild.so"
    "babashka/esbuild/linux-x86_64/libesbuild.so"
    "babashka/esbuild/windows-x86_64/esbuild.dll"})

(defn- built-from-esbuild?
  "The esbuild version is linked into each shared library, so a stale binary
  from an earlier release does not carry the one this jar claims."
  [in]
  (with-open [in in]
    (str/includes? (slurp in :encoding "ISO-8859-1") esbuild-version)))

(defn- natives-ready? []
  (every? (fn [n]
            (let [f (java.io.File. (str "resources/" n))]
              (and (.exists f)
                   (pos? (.length f))
                   (built-from-esbuild? (java.io.FileInputStream. f)))))
          shared-libraries))

(defn- ensure-natives! []
  (when-not (natives-ready?)
    (println "shims missing or stale, running bb natives --all")
    (let [{:keys [exit]} (b/process {:command-args ["bb" "natives" "--all"] :dir ".."})]
      (when-not (zero? exit)
        (throw (ex-info "bb natives --all failed" {:exit exit}))))))

(defn- verify-jar!
  "Clojars keeps every version it accepts, so a jar that is missing a platform
  must not leave this machine."
  []
  (when-not (.exists (java.io.File. jar-file))
    (throw (ex-info (str jar-file " does not exist. Run clojure -T:build jar")
                    {:jar jar-file})))
  (with-open [jar (java.util.jar.JarFile. (java.io.File. jar-file))]
    (let [entries (into {} (map (juxt #(.getName ^java.util.jar.JarEntry %)
                                      #(.getSize ^java.util.jar.JarEntry %)))
                        (enumeration-seq (.entries jar)))
          under (fn [n] (and (str/starts-with? n "babashka/esbuild/")
                             (not (str/ends-with? n "/"))))
          unexpected (sort (remove (conj shared-libraries "babashka/esbuild/version")
                                   (filter under (keys entries))))
          missing (sort (remove entries shared-libraries))
          empty-ones (sort (filter #(zero? (get entries % 0)) shared-libraries))]
      (when (seq missing)
        (throw (ex-info (str "jar is missing " (count missing) " of "
                             (count shared-libraries) " shared libraries: "
                             (str/join ", " missing)
                             "\nRun bb natives --all")
                        {:missing missing})))
      (when (seq empty-ones)
        (throw (ex-info (str "jar has empty shared libraries: "
                             (str/join ", " empty-ones))
                        {:empty empty-ones})))
      (when (seq unexpected)
        (throw (ex-info (str "jar carries files it should not: "
                             (str/join ", " unexpected)
                             "\nDelete libesbuild/resources and run bb natives --all")
                        {:unexpected unexpected})))
      (let [stale (sort (remove #(built-from-esbuild?
                                  (.getInputStream jar (.getEntry jar %)))
                                shared-libraries))]
        (when (seq stale)
          (throw (ex-info (str "these shared libraries were not built from esbuild "
                               esbuild-version ": " (str/join ", " stale)
                               "\nDelete libesbuild/resources and run bb natives --all")
                          {:stale stale :expected esbuild-version})))))))

(defn clean [_]
  (b/delete {:path "target"})
  (println "deleted target"))

(defn jar [_]
  (ensure-natives!)
  (b/copy-dir {:src-dirs ["resources"] :target-dir class-dir})
  (b/copy-file {:src "LICENSE-esbuild.md"
                :target (str class-dir "/META-INF/licenses/esbuild/LICENSE.md")})
  (spit (java.io.File. class-dir "babashka/esbuild/version") version)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs []
                :scm {:url "https://github.com/babashka/babashka.esbuild"
                      :tag version}
                :pom-data
                [[:description "The esbuild shared libraries for babashka.esbuild"]
                 [:url "https://github.com/babashka/babashka.esbuild"]
                 [:licenses
                  [:license
                   [:name "MIT License"]
                   [:url "https://opensource.org/license/mit/"]]]]})
  (b/jar {:class-dir class-dir :jar-file jar-file})
  (verify-jar!)
  (println "wrote" jar-file (str "(" (size jar-file) ")"))
  (println "platforms:" (count shared-libraries) "of" (count shared-libraries)))

(defn install [_]
  (jar nil)
  (b/install {:basis @basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir})
  (println "installed" (str lib) version "to" (m2-dir)))

(defn deploy [_]
  (jar nil)
  (println "deploying" (str lib) version "to Clojars")
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})})
  (println "deployed" (str lib) version))
