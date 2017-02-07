(ns untangled-spec.selectors-spec
  (:require
    [clojure.spec :as s]
    [clojure.spec.test :as st]
    [untangled-spec.core :refer [specification behavior assertions]]
    [untangled-spec.selectors :as sel]))

(st/instrument)

(specification "selectors"
  (assertions
    (sel/set-selectors* {:a :a :b :b :focused :focused} [:focused])
    => {:focused :focused}

    ;; base case
    (sel/selected-for?* {} nil)
    => true
    (sel/selected-for?* {} [])
    => true

    ;; not selected if there are active selectors but none declared
    (sel/selected-for?* {:focused :focused} [])
    => false

    ;; always selected if there are no active selectors
    (sel/selected-for?* {} [:focused])
    => true
    (sel/selected-for?* {} [(keyword (gensym))])
    => true

    ;; selected if passes an active selector
    (sel/selected-for?* {:focused :focused} [:focused])
    => true
    (sel/selected-for?* {:focused :focused} [(keyword (gensym "SELECTOR"))])
    => false

    ;; active selector values can be any `ifn?`
    (sel/selected-for?* {:default (complement :integration)} [:unit])
    => true
    (sel/selected-for?* {:default (complement :integration)} [:integration])
    => false

    ;; must pass all active selectors
    (sel/selected-for?* {:a :a :b :b} [:a])
    => false
    (sel/selected-for?* {:a :a :b :b} [:a :b])
    => true))
