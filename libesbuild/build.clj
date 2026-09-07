(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.github.babashka/libesbuild)
(def version "0.28.2-1")
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_] (b/delete {:path "target"}))

(defn jar [_]
  (b/copy-dir {:src-dirs ["resources"] :target-dir class-dir})
  (b/copy-file {:src "LICENSE-esbuild.md"
                :target (str class-dir "/META-INF/licenses/esbuild/LICENSE.md")})
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs []
                :scm {:url "https://github.com/babashka/babashka.esbuild"
                      :tag version}})
  (b/jar {:class-dir class-dir :jar-file (format "target/%s-%s.jar" (name lib) version)}))

(defn deploy [_]
  (let [jar-file (format "target/%s-%s.jar" (name lib) version)]
    (dd/deploy {:installer :remote
                :artifact jar-file
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})})))
