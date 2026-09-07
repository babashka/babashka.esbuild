(ns babashka.esbuild
  "Bundle and transform JavaScript, TypeScript, JSX and CSS with esbuild."
  (:require [babashka.ffi :as ffi :refer [defcfn]]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private natives-version "0.28.2-1")

(defn- lib-name []
  (let [os (str/lower-case (System/getProperty "os.name"))
        arch (System/getProperty "os.arch")
        arch (if (#{"aarch64" "arm64"} arch) "aarch64" "x86_64")]
    (cond (str/includes? os "mac") (str "darwin-" arch "/libesbuild.dylib")
          (str/includes? os "win") (str "windows-" arch "/esbuild.dll")
          :else (str "linux-" arch "/libesbuild.so"))))

(defn- extract-once
  "Copies the native library out of the jar into the cache and returns its path.
  A version in the path makes a new release a new directory."
  []
  (let [name (lib-name)
        file (fs/file (fs/xdg-cache-home) "babashka" "esbuild" natives-version name)]
    (when-not (fs/exists? file)
      (fs/create-dirs (fs/parent file))
      (let [resource (io/resource (str "babashka/esbuild/" name))
            tmp (fs/create-temp-file {:dir (fs/parent file) :suffix ".tmp"})]
        (when-not resource
          (throw (ex-info (str "No esbuild native library for this platform: " name) {:lib name})))
        (with-open [in (io/input-stream resource)]
          (io/copy in (fs/file tmp)))
        (fs/move tmp file {:replace-existing true})))
    (str file)))

#_{:clj-kondo/ignore [:unused-private-var]}
(defonce ^:private lib
  (ffi/load-library (or (not-empty (System/getenv "BABASHKA_ESBUILD_LIB")) (extract-once))))

(defcfn ^:private -version "esbuild_version" [] :pointer)
(defcfn ^:private -transform "esbuild_transform" [:string :string] :pointer)
(defcfn ^:private -build "esbuild_build" [:string] :pointer)
(defcfn ^:private -free "esbuild_free" [:pointer] :void)

(defn- take-result [p]
  (try (json/parse-string (ffi/ptr->string p) true)
       (finally (-free p))))

(defn- camel [s]
  (let [[head & tail] (str/split s #"-")]
    (apply str head (map str/capitalize tail))))

(defn- encode [opts]
  (json/generate-string
   (reduce-kv (fn [m k v]
                (assoc m (camel (name k)) (if (keyword? v) (name v) v)))
              {} opts)))

(defn- check! [{:keys [errors] :as result}]
  (when (seq errors)
    (throw (ex-info (:text (first errors)) {:type ::error :errors errors})))
  (cond-> (dissoc result :errors)
    (empty? (:warnings result)) (dissoc :warnings)))

(defn version
  "Returns the esbuild version this library binds."
  []
  (let [p (-version)]
    (try (ffi/ptr->string p) (finally (-free p)))))

(defn transform
  "Transforms one string of source and returns {:code s}, with :map and
  :warnings when they are present. Throws on a syntax error, with the
  messages under :errors in the ex-data.

  Options are esbuild transform options as kebab-case keywords with keyword
  values: :loader, :format, :target, :platform, :sourcemap, :jsx, :minify,
  :sourcefile and :define."
  ([source] (transform source nil))
  ([source opts]
   (-> (take-result (-transform source (encode opts))) check!)))

(defn build
  "Bundles :entry-points and returns {:outputs [{:path p :contents s}]}, with
  :warnings when they are present. Throws on a build error, with the messages
  under :errors in the ex-data.

  Writes to disk when :write is true, otherwise returns the output in memory.
  Options are esbuild build options as kebab-case keywords: :entry-points,
  :bundle, :outfile, :outdir, :splitting, :external, :write and the transform
  options above."
  [opts]
  (let [result (-> (take-result (-build (encode (merge {:write false} opts))))
                   check!)]
    (-> result (assoc :outputs (:outputFiles result)) (dissoc :outputFiles))))
