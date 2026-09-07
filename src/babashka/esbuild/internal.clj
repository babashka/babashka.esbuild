(ns babashka.esbuild.internal
  {:no-doc true}
  (:require [babashka.ffi :as ffi :refer [defcfn]]
            [babashka.fs :as fs]
            [babashka.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def natives-version
  (if-let [r (io/resource "babashka/esbuild/version")]
    (str/trim (slurp r))
    "dev"))

(defn lib-name []
  (let [os (str/lower-case (System/getProperty "os.name"))
        arch (System/getProperty "os.arch")
        arch (if (#{"aarch64" "arm64"} arch) "aarch64" "x86_64")]
    (cond (str/includes? os "mac") (str "darwin-" arch "/libesbuild.dylib")
          (str/includes? os "win") (str "windows-" arch "/esbuild.dll")
          :else (str "linux-" arch "/libesbuild.so"))))

(defn extract-once
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

(defonce lib
  (ffi/load-library (or (not-empty (System/getenv "BABASHKA_ESBUILD_LIB")) (extract-once))))

(defcfn -version "esbuild_version" [] :pointer)
(defcfn -transform "esbuild_transform" [:string :string] :pointer)
(defcfn -build "esbuild_build" [:string] :pointer)
(defcfn -free "esbuild_free" [:pointer] :void)

(defn take-result [p]
  (try (json/read-str (ffi/ptr->string p))
       (finally (-free p))))

(defn camel [s]
  (let [[head & tail] (str/split s #"-")]
    (apply str head (map str/capitalize tail))))

(defn encode [opts]
  (json/write-str
   (reduce-kv (fn [m k v]
                (assoc m (camel (name k)) (if (keyword? v) (name v) v)))
              {} opts)))

(defn check! [{:keys [errors] :as result}]
  (when (seq errors)
    (throw (ex-info (:text (first errors)) {:type :babashka.esbuild/error :errors errors})))
  (cond-> (dissoc result :errors)
    (empty? (:warnings result)) (dissoc :warnings)))
