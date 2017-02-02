(ns ^:figwheel-no-load untangled-spec.selectors
  (:require
    [clojure.set :as set]
    #?(:clj [clojure.tools.namespace.repl :as tools-ns-repl])))

#?(:clj (tools-ns-repl/disable-reload!))

(defonce active-selectors
  (atom #{}))

(defn get-selectors! []
  @active-selectors)

(defn set-selectors! [{:keys []} selectors]
  ;;TODO new selectors must be declared in test-runner opts
  ;; otherwise the selector will be ignored (ie tests w/ that sel will always run)
  (reset! active-selectors
    (or (and selectors
          (sequential? selectors)
          (set selectors))
        #{})))

(defn selected-for? [selectors]
  (let [ret (or (empty? @active-selectors)
                (seq (set/intersection @active-selectors (set selectors))))]
    (prn :selected-for? @active-selectors selectors '=> (boolean ret))
    ret))
