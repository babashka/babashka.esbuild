(ns babashka.esbuild
  "Bundle and transform JavaScript, TypeScript, JSX and CSS with esbuild."
  (:require [babashka.esbuild.internal :as internal]
            [babashka.ffi :as ffi]))

(defn version
  "Returns the esbuild version this library binds."
  []
  (let [p (internal/-version)]
    (try (ffi/ptr->string p) (finally (internal/-free p)))))

(defn transform
  "Transforms one string of source and returns {:code s}, with :map and
  :warnings when they are present. Throws on a syntax error, with the
  messages under :errors in the ex-data.

  Options are esbuild transform options as kebab-case keywords with keyword
  values: :loader, :format, :target, :platform, :sourcemap, :jsx, :minify,
  :sourcefile and :define."
  ([source] (transform source nil))
  ([source opts]
   (-> (internal/take-result (internal/-transform source (internal/encode opts)))
       internal/check!)))

(defn build
  "Bundles :entry-points and returns {:outputs [{:path p :contents s}]}, with
  :warnings when they are present. Throws on a build error, with the messages
  under :errors in the ex-data.

  Writes to disk when :write is true, otherwise returns the output in memory.
  Options are esbuild build options as kebab-case keywords: :entry-points,
  :bundle, :outfile, :outdir, :splitting, :external, :alias, :write and the
  transform options above."
  [opts]
  (let [result (-> (internal/take-result
                    (internal/-build (internal/encode (merge {:write false} opts))))
                   internal/check!)]
    (-> result (assoc :outputs (:outputFiles result)) (dissoc :outputFiles))))
