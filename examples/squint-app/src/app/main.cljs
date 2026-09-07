(ns app.main
  (:require [app.util :as util]))

(def orders
  [{:country "NL" :amount 25.0}
   {:country "NL" :amount 54.0}
   {:country "DE" :amount 19.0}])

(defn main []
  (println (util/greet "babashka"))
  (println (util/totals orders)))

(main)
