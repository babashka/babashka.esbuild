(ns shim
  (:require [babashka.fs :as fs])
  (:import [java.security MessageDigest]))

(defn source-hash [dir]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (doseq [f ["shim.go" "go.mod" "go.sum"]]
      (.update md (fs/read-all-bytes (fs/path dir f))))
    (subs (format "%064x" (BigInteger. 1 (.digest md))) 0 16)))
