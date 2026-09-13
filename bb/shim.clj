(ns shim
  (:import [java.io File]
           [java.nio.file Files]
           [java.security MessageDigest]))

(defn source-hash [dir]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (doseq [f ["shim.go" "go.mod" "go.sum"]]
      (.update md (Files/readAllBytes (.toPath (File. (str dir) f)))))
    (subs (format "%064x" (BigInteger. 1 (.digest md))) 0 16)))
