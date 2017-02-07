(ns untangled-spec.router
  (:require
    [cljs.reader :refer [read-string]]
    [com.stuartsierra.component :as cp]
    [om.next :as om]
    [pushy.core :as pushy]
    [untangled-spec.renderer :as renderer]
    [untangled.client.mutations :as m])
  (:import
    (goog.Uri.QueryData)))

(defn parse-fragment [path]
  (let [data (new goog.Uri.QueryData path)]
    {:filter (some-> (.get data "filter")
               keyword)
     :selectors (some->> (.get data "selectors")
                  read-string
                  (mapv keyword))}))

(defn assoc-fragment! [history k v]
  (let [data (new goog.Uri.QueryData (pushy/get-token history))]
    (.set data (name k) v)
    (pushy/set-token! history
      (.toString data))))

(defn setup! [tx!]
  (let [history (with-redefs [pushy/update-history!
                              #(doto %
                                 (.setUseFragment true)
                                 (.setPathPrefix "")
                                 (.setEnabled true))]
                  (pushy/pushy tx! parse-fragment))]
    (pushy/start! history)
    history))

(defrecord Router []
  cp/Lifecycle
  (start [this]
    (let [{:keys [reconciler]} (-> this :test/renderer :app)
          history (setup!
                    (fn on-route-change [{:as new-route :keys [filter selectors]}]
                      (om/transact! reconciler
                        `[(renderer/set-page-filter
                            ~{:filter filter})
                          (run-tests/with-new-selectors
                            ~{:selectors selectors})])))]
      (defmethod m/mutate `renderer/set-filter [{:keys [state]} _ {:keys [filter]}]
        {:action #(assoc-fragment! history :filter (name filter))}))
    this)
  (stop [this]
    (remove-method m/mutate `renderer/set-filter)
    this))

(defn make-router []
  (cp/using
    (map->Router {})
    [:test/renderer]))
