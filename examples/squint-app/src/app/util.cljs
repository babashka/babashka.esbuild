(ns app.util)

(defn greet [name]
  (str "hello " name))

(defn totals [orders]
  (->> orders
       (group-by :country)
       (map (fn [[country os]] [country (reduce + (map :amount os))]))
       (into {})))
