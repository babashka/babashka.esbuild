(ns tasks
  (:require [babashka.fs :as fs]
            [babashka.tasks :refer [shell]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [natives]))

(def version-file (fs/file "libesbuild" "version.edn"))

(defn- read-version []
  (edn/read-string (slurp version-file)))

(defn- bump-libesbuild! []
  (let [esbuild (subs (natives/esbuild-version) 1)
        current (read-version)]
    (spit version-file
          (if (= esbuild (:esbuild current))
            (update current :shim inc)
            {:esbuild esbuild :shim 1}))))

(defn- pin-libesbuild! [version]
  (spit "deps.edn"
        (str/replace (slurp "deps.edn")
                     #"(io\.github\.babashka/libesbuild \{:mvn/version \")[^\"]+"
                     (str "$1" version))))

(defn publish-libesbuild
  "Deploys libesbuild to Clojars."
  {:org.babashka/cli {:spec {:bump {:coerce :boolean
                                    :desc "Bump the shim number, then pin, commit and push the new version"}}}}
  [{:keys [bump]}]
  (when bump
    (bump-libesbuild!))
  (shell {:dir "libesbuild"} "clojure -T:build deploy")
  (when bump
    (let [{:keys [esbuild shim]} (read-version)
          version (str esbuild "-" shim)]
      (pin-libesbuild! version)
      (shell "git add libesbuild/version.edn deps.edn")
      (shell "git commit -m" (str "libesbuild " version))
      (shell "git push"))))
