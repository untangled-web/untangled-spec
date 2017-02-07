(ns ^:figwheel-no-load untangled-spec.selectors
  (:require
    [clojure.set :as set]
    [clojure.spec :as s]
    #?(:clj [clojure.tools.namespace.repl :as tools-ns-repl])))

#?(:clj (tools-ns-repl/disable-reload!))

(defonce active-selectors
  (atom {}))

(defn get-selectors! []
  @active-selectors)

(s/def ::active-selectors
  (s/map-of keyword? ifn?))
(s/def ::selectors
  (s/nilable (s/coll-of keyword?)))

(s/fdef set-selectors*
  :args (s/cat
          :available-selectors ::active-selectors
          :selectors ::selectors)
  :ret ::active-selectors)
(defn set-selectors* [available-selectors selectors]
  (select-keys available-selectors selectors))

(defn set-selectors! [{available-selectors :selectors} selectors]
  (reset! active-selectors
    (set-selectors* available-selectors selectors)))

(s/fdef selected-for?*
  :args (s/cat
          :active-selectors ::active-selectors
          :selectors ::selectors)
  :ret boolean?)
(defn selected-for?* [active-selectors selectors]
  (boolean
    (or (empty? active-selectors)
        (and (seq selectors)
          ((apply every-pred (vals active-selectors))
           (zipmap selectors (repeat true)))))))

(defn selected-for? [selectors]
  (selected-for?* @active-selectors selectors))
