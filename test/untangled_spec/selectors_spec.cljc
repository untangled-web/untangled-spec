(ns untangled-spec.selectors-spec
  (:require
    [clojure.spec :as s]
    [clojure.spec.test :as st]
    [untangled-spec.core :refer [specification behavior component assertions]]
    [untangled-spec.selectors :as sel]))

(defn longform [active & [inactive]]
  (vec
    (concat
      (map #(hash-map :selector/id % :selector/active? true) active)
      (map #(hash-map :selector/id % :selector/active? false) inactive))))

(specification "selectors" :focused
  (component "set-selectors"
    (assertions
      (sel/set-selectors* (longform #{:a :b :focused})
        #{:focused})
      =>  (longform #{:focused} #{:a :b})))

  (component "selected-for?"
    (assertions
      "if there are no selectors on the test, only selected if ::sel/none is active"
      (sel/selected-for?* [] nil) => false
      (sel/selected-for?* [] #{}) => false
      (sel/selected-for?* (longform #{::sel/none}) nil) => true
      (sel/selected-for?* (longform #{::sel/none}) #{}) => true

      "active selectors only apply on tests that have the selector"
      (sel/selected-for?* (longform #{:focused}) #{}) => false
      (sel/selected-for?* (longform #{:focused}) nil) => false
      (sel/selected-for?* (longform #{:focused}) #{:focused}) => true

      "only selected if it's an active selector"
      (sel/selected-for?* (longform #{}) #{:focused}) => false
      (sel/selected-for?* (longform #{:focused}) #{:focused}) => true
      (sel/selected-for?* (longform #{:focused}) #{(keyword (gensym))}) => false
      (sel/selected-for?* (longform #{:focused} #{:fakeused}) #{:fakeused}) => false

      "must pass at least one active selector"
      (sel/selected-for?* (longform #{:unit :focused}) #{:focused}) => true
      (sel/selected-for?* (longform #{:unit :focused}) #{:qa}) => false)))
