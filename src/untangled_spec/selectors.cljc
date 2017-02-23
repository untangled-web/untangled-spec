(ns ^:figwheel-no-load untangled-spec.selectors
  (:require
    #?(:cljs [cljs.reader :refer [read-string]])
    [clojure.set :as set]
    [clojure.spec :as s]
    [untangled-spec.spec :as us]
    #?(:clj [clojure.tools.namespace.repl :as tools-ns-repl])))

#?(:clj (tools-ns-repl/disable-reload!))

(defonce initial-selectors (atom nil))
(defonce available-selectors (atom nil))
(defonce active-selectors (atom nil))

(s/def ::selector keyword?)
(s/def ::available (s/coll-of ::selector :kind set? :into #{}))
(s/def ::default ::available)
(s/def ::selectors (s/keys :req-un [::available] :opt-un [::default]))
(s/def ::active-selectors ::available)
(s/def ::test-selectors (s/nilable (s/and (s/conformer set vec) ::available)))

(defn parse-selectors [selectors-str]
  (read-string selectors-str))

(s/fdef initialize-selectors!
  :args (s/cat :initial-selectors ::selectors))
(defn initialize-selectors! [{:as initial
                              :keys [available default]
                              :or {default #{::none}}}]
  (reset! initial-selectors (update initial :available conj ::none))
  (reset! available-selectors (conj available ::none))
  (when-not @active-selectors
    (reset! active-selectors default))
  true)

(s/fdef set-selectors*
  :args (s/cat
          :available-selectors ::active-selectors
          :test-selectors ::test-selectors)
  :ret ::active-selectors)
(defn set-selectors* [available-selectors test-selectors]
  (set/intersection available-selectors test-selectors))

(defn set-selectors! [test-selectors]
  (reset! active-selectors
    (set-selectors* @available-selectors test-selectors)))

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
