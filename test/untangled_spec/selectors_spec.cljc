(ns untangled-spec.selectors-spec
  (:require
    [clojure.spec :as s]
    [clojure.spec.test :as st]
    [untangled-spec.core :refer [specification behavior component assertions]]
    [untangled-spec.selectors :as sel]))

(st/instrument)

(specification "selectors" :focused
  (component "set-selectors"
    (assertions
      (sel/set-selectors* #{:a :b :focused} #{:focused}) => #{:focused}))

  (component "selected-for?"
    (assertions
      "if there are no selectors on the test, only selected if ::sel/none is active"
      (sel/selected-for?* #{} nil) => false
      (sel/selected-for?* #{} #{}) => false
      (sel/selected-for?* #{::sel/none} nil) => true
      (sel/selected-for?* #{::sel/none} #{}) => true

      "active selectors only apply on tests that have the selector"
      (sel/selected-for?* #{:focused} #{}) => false
      (sel/selected-for?* #{:focused} nil) => false
      (sel/selected-for?* #{:focused} #{:focused}) => true

      "only selected if it's an active selector"
      (sel/selected-for?* #{} #{:focused}) => false
      (sel/selected-for?* #{:focused} #{:focused}) => true
      (sel/selected-for?* #{:focused} #{(keyword (gensym))}) => false

      "must pass at least one active selector"
      (sel/selected-for?* #{:unit :focused} #{:focused}) => true
      (sel/selected-for?* #{:unit :focused} #{:qa}) => false)))
