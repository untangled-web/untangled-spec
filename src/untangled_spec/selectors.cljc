(ns ^:figwheel-no-load untangled-spec.selectors
  (:require
    #?(:cljs [cljs.reader :refer [read-string]])
    [clojure.set :as set]
    [clojure.spec :as s]
    [untangled-spec.spec :as us]
    #?(:clj [clojure.tools.namespace.repl :as tools-ns-repl])))

#?(:clj (tools-ns-repl/disable-reload!))

(defonce selectors (atom {:current nil :default nil}))

(s/def :selector/active? boolean?)
(s/def :selector/id keyword?)
(s/def ::selector (s/keys :req [:selector/id :selector/active?]))
(s/def ::selectors (s/coll-of ::selector :kind vector? :into []))
(s/def ::shorthand (s/coll-of keyword? :kind set? :into #{}))
(s/def ::default ::shorthand)
(s/def ::available ::shorthand)
(s/def ::initial-selectors (s/keys :req-un [::available] :opt-un [::default]))
(s/def ::test-selectors (s/nilable (s/and (s/conformer set vec) ::shorthand)))

(s/fdef parse-selectors
  :args (s/cat :selectors-str string?)
  :ret ::shorthand)
(defn parse-selectors [selectors-str]
  (read-string selectors-str))

(s/fdef to-string
  :args (s/cat :selectors ::selectors)
  :ret string?)
(defn to-string [selectors]
  (str
    (into #{}
      (comp (filter :selector/active?) (map :selector/id))
      selectors)))

(s/fdef get-current-selectors
  :ret ::selectors)
(defn get-current-selectors []
  (:current @selectors))

(s/fdef initialize-selectors!
  :args (s/cat :initial-selectors ::initial-selectors))
(defn initialize-selectors! [{:keys [available default]
                              :or {default #{::none}}}]
  (swap! selectors assoc :current
    (mapv (fn [sel] {:selector/id sel :selector/active? (contains? default sel)})
      (conj available ::none)))
  (swap! selectors assoc :default default)
  true)

(s/fdef set-selectors*
  :args (s/cat
          :current-selectors ::selectors
          :new-selectors ::shorthand)
  :ret ::selectors)
(defn set-selectors* [current-selectors selected?]
  (mapv (fn [{:as sel :keys [selector/id]}]
          (assoc sel :selector/active? (contains? selected? id)))
    current-selectors))

(defn set-selectors! [test-selectors]
  (swap! selectors update :current set-selectors*
    (or test-selectors (:default @selectors))))

(s/fdef set-selector*
  :args (s/cat
          :current-selectors ::selectors
          :new-selector ::selector)
  :ret ::selectors)
(defn set-selector* [current-selectors {:keys [selector/id selector/active?]}]
  (mapv (fn [sel]
          (cond-> sel (= (:selector/id sel) id)
            (assoc :selector/active? active?)))
    current-selectors))

(defn set-selector! [selector]
  (swap! selectors update :current set-selectors* selector))

(s/fdef selected-for?*
  :args (s/cat
          :current-selectors ::selectors
          :test-selectors ::test-selectors)
  :ret boolean?)
(defn selected-for?* [current-selectors test-selectors]
  (boolean
    (some (comp (if (empty? test-selectors)
                  #{::none} test-selectors)
            :selector/id)
          (filter :selector/active? current-selectors))))

(defn selected-for? [test-selectors]
  (selected-for?* (:current @selectors) test-selectors))
