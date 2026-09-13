(ns versions
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]))

(defn file [dir]
  (fs/file dir "version.edn"))

(defn read-edn [dir]
  (edn/read-string (slurp (file dir))))

(defn esbuild [dir]
  (let [{:keys [major minor release]} (read-edn dir)]
    (str major "." minor "." release)))

(defn libesbuild [dir]
  (let [{:keys [esbuild shim]} (read-edn dir)]
    (str esbuild "-" shim)))
