(ns babashka.esbuild-test
  (:require [babashka.esbuild :as esbuild]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest version-test
  (is (str/starts-with? (esbuild/version) "v")))

(deftest transform-test
  (testing "typescript loses its types"
    (is (= {:code "const x = 1;\n"}
           (esbuild/transform "const x: number = 1" {:loader :ts}))))
  (testing "minify shortens names"
    (is (= {:code "let f=e=>e*2;\n"}
           (esbuild/transform "let f = (x: number): number => x*2"
                              {:loader :ts :minify true :target :es2020}))))
  (testing "jsx compiles to createElement"
    (is (str/includes? (:code (esbuild/transform "<h1>hi</h1>" {:loader :jsx}))
                       "React.createElement")))
  (testing "define replaces an identifier"
    (is (= {:code "console.log(\"prod\");\n"}
           (esbuild/transform "console.log(MODE)" {:define {"MODE" "\"prod\""}}))))
  (testing "no options needed"
    (is (= {:code "let x = 1;\n"} (esbuild/transform "let x = 1")))))

(deftest transform-error-test
  (testing "a syntax error throws with the messages as data"
    (let [e (try (esbuild/transform "let = ;") (catch Exception e e))]
      (is (= "Unexpected \";\"" (ex-message e)))
      (is (= :babashka.esbuild/error (:type (ex-data e))))
      (is (= {:text "Unexpected \";\"" :file "<stdin>" :line 1 :column 6}
             (first (:errors (ex-data e))))))))

(deftest build-test
  (let [dir (fs/create-temp-dir {:prefix "esbuild-test"})
        entry (fs/file dir "main.js")]
    (spit (fs/file dir "util.js") "export const two = () => 2\n")
    (spit entry "import { two } from './util.js'\nconsole.log(two())\n")
    (testing "a bundle comes back in memory"
      (let [{:keys [outputs]} (esbuild/build {:entry-points [(str entry)]
                                              :bundle true :format :esm :minify true})]
        (is (= 1 (count outputs)))
        (is (= "var o=()=>2;console.log(o());\n" (:contents (first outputs))))))
    (testing "write true puts the bundle on disk"
      (let [out (fs/file dir "out.js")]
        (esbuild/build {:entry-points [(str entry)] :bundle true
                        :outfile (str out) :write true})
        (is (fs/exists? out))
        (is (str/includes? (slurp out) "console.log"))))))

(deftest build-error-test
  (let [dir (fs/create-temp-dir {:prefix "esbuild-test"})
        entry (fs/file dir "main.js")]
    (spit entry "import { x } from './missing.js'\n")
    (let [e (try (esbuild/build {:entry-points [(str entry)] :bundle true})
                 (catch Exception e e))]
      (is (str/includes? (ex-message e) "Could not resolve"))
      (is (= :babashka.esbuild/error (:type (ex-data e)))))))
