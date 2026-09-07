(ns build
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.github.babashka/esbuild)
(def version "0.1.0")
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

(defn clean [_]
  (b/delete {:path "target"})
  (println "deleted target"))

(defn jar [_]
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/babashka/babashka.esbuild"
                      :tag (str "v" version)}
                :pom-data
                [[:description "Use esbuild from babashka and the JVM"]
                 [:url "https://github.com/babashka/babashka.esbuild"]
                 [:licenses
                  [:license
                   [:name "MIT License"]
                   [:url "https://opensource.org/license/mit/"]]]]})
  (b/jar {:class-dir class-dir :jar-file jar-file})
  (println "wrote" jar-file (str "(" (size jar-file) ")")))

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
