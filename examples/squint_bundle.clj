;; Compiles ClojureScript with squint and bundles it with esbuild into one
;; JavaScript file. No Node.js build tooling and no npm install.
;;
;;   bb examples/squint_bundle.clj
;;
;; The squint git checkout carries the compiler and the JavaScript runtime, so
;; :alias points esbuild at src/squint for the squint-cljs imports.

(ns squint-bundle)

(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {io.github.squint-cljs/squint
                        {:git/sha "e83befd03153b842d45cd25a77fefb915f120eb7"}}})

(require '[babashka.esbuild :as esbuild]
         '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[squint.compiler :as squint])

(def src-dir "examples/squint-app/src")
(def out-dir (fs/create-temp-dir {:prefix "squint-app"}))

(def squint-js
  "src/squint of the squint checkout, where core.js and its friends live."
  (str (fs/parent (fs/path (io/resource "squint/core.js")))))

(defn file->ns
  "app/util.cljs under src-dir becomes app.util."
  [file]
  (-> (str (fs/relativize src-dir file))
      (str/replace #"\.cljs$" "")
      (str/replace fs/file-separator ".")))

(defn compile-cljs
  "Compiles every .cljs file under src-dir to one flat directory of .mjs files
  named after the namespace, so a require resolves to a sibling file."
  []
  (doseq [file (fs/glob src-dir "**.cljs")
          :let [target (fs/path out-dir (str (file->ns file) ".mjs"))]]
    (spit (fs/file target)
          (squint/compile-string (slurp (fs/file file))
                                 {:resolve-ns (fn [ns] (str "./" ns ".mjs"))}))))

(compile-cljs)
(println "compiled:" (mapv #(str (fs/file-name %)) (fs/glob out-dir "*.mjs")))

(def bundle
  (-> (esbuild/build {:entry-points [(str (fs/path out-dir "app.main.mjs"))]
                      :bundle true
                      :format :esm
                      :target :es2020
                      :minify true
                      :alias {"squint-cljs" squint-js}})
      :outputs first :contents))

(println "bundle:" (count bundle) "bytes")
(spit "bundle.mjs" bundle)
(println "node bundle.mjs:")
(print (:out (p/shell {:out :string} "node" "bundle.mjs")))
