(ns ^:figwheel-no-load untangled-spec.selectors
  (:require
    [clojure.set :as set]
    [clojure.spec :as s]
    #?(:clj [clojure.tools.namespace.repl :as tools-ns-repl])))

#?(:clj (tools-ns-repl/disable-reload!))

(defonce active-selectors
  (atom []))

(s/def ::available (s/coll-of keyword? :kind set?))
(s/def ::default ::available)
(s/def ::selectors (s/keys :req-un [::available] :opt-un [::default]))
(s/def ::active-selectors ::available)
(s/def ::test-selectors (s/nilable ::available))

(s/fdef set-selectors*
  :args (s/cat
          :available-selectors ::active-selectors
          :test-selectors ::test-selectors)
  :ret ::active-selectors)
(defn set-selectors* [available-selectors test-selectors]
  (set/intersection available-selectors test-selectors))

(defn set-selectors! [available-selectors test-selectors]
  (reset! active-selectors
    (set-selectors* available-selectors test-selectors)))

(s/fdef selected-for?*
  :args (s/cat
          :active-selectors ::active-selectors
          :test-selectors ::test-selectors)
  :ret boolean?)
(defn selected-for?* [active-selectors test-selectors]
  (boolean
    (seq (set/intersection active-selectors
           (if (empty? test-selectors)
             #{::none} test-selectors)))))

(defn selected-for? [test-selectors]
  (selected-for?* @active-selectors test-selectors))
