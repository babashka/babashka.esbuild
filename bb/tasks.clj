(ns tasks
  (:require [babashka.tasks :refer [shell]]
            [clojure.string :as str]
            [natives]
            [versions]))

(defn- bump-libesbuild! []
  (let [esbuild (subs (natives/esbuild-version) 1)
        current (versions/read-edn "libesbuild")]
    (spit (versions/file "libesbuild")
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
                                    :desc "Bump the shim number, then pin, commit and push the new version"}}
                      :restrict true}}
  [{:keys [bump]}]
  (when bump
    (bump-libesbuild!))
  (shell {:dir "libesbuild"} "clojure -T:build deploy")
  (when bump
    (let [version (versions/libesbuild "libesbuild")]
      (pin-libesbuild! version)
      (shell "git add libesbuild/version.edn deps.edn")
      (shell "git commit -m" (str "libesbuild " version))
      (shell "git push"))))

(defn publish-esbuild
  "Deploys babashka.esbuild to Clojars."
  {:org.babashka/cli {:spec {:bump {:coerce :boolean
                                    :desc "Bump the release number, then commit and push the new version"}}
                      :restrict true}}
  [{:keys [bump]}]
  (when bump
    (spit (versions/file ".") (update (versions/read-edn ".") :release inc)))
  (shell "clojure -T:build deploy")
  (when bump
    (shell "git add version.edn")
    (shell "git commit -m" (versions/esbuild "."))
    (shell "git push")))
