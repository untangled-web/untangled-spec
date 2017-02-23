(ns untangled-spec.router
  (:require
    [clojure.set :as set]
    [com.stuartsierra.component :as cp]
    [om.next :as om]
    [pushy.core :as pushy]
    [untangled-spec.renderer :as renderer]
    [untangled-spec.selectors :as sel]
    [untangled.client.mutations :as m])
  (:import
    (goog.Uri QueryData)))

(defn parse-fragment [path]
  (let [data (new QueryData path)]
    {:filter (some-> (.get data "filter") keyword)
     :selectors (some->> (.get data "selectors") sel/parse-selectors)}))

(defn assoc-fragment! [history k v]
  (let [data (new goog.Uri.QueryData (pushy/get-token history))]
    (.set data (name k) v)
    (pushy/set-token! history
      ;; so we dont get an ugly escaped url
      (.toDecodedString data))))

(defn setup! [tx!]
  (let [history (with-redefs [pushy/update-history!
                              #(doto %
                                 (.setUseFragment true)
                                 (.setPathPrefix "")
                                 (.setEnabled true))]
                  (pushy/pushy tx! parse-fragment))]
    (pushy/start! history)
    history))

(defmethod m/mutate `set-page-filter [{:keys [state ast]} k {:keys [filter]}]
  {:action #(swap! state assoc :report/filter
              (or (and (nil? filter) :all)
                  (and (contains? renderer/filters filter) filter)
                  (do (js/console.warn "INVALID FILTER: " (str filter)) :all)))})

(defmethod m/mutate `sel/change-active [{:keys [state]} _ {:keys [selectors]}]
  {:action #(swap! state assoc-in [:selectors :active] selectors)
   :remote true})

(defmethod m/mutate `sel/set-selectors-from-url [{:keys [state ast]} k {:keys [prev-selectors]}]
  (let [url-selectors (some->> js/window.location
                        (new goog.Uri) (.getFragment)
                        (new QueryData)
                        (#(.get % "selectors"))
                        sel/parse-selectors)]
    {:action #(swap! state assoc-in [:selectors :active]
                (set/intersection
                  (get-in @state [:selectors :available])
                  (or url-selectors (get-in @state [:selectors :default]))))
     :remote (some-> (if url-selectors
                       (when (not= url-selectors prev-selectors)
                         (-> ast
                           (assoc-in [:params :selectors] url-selectors)))
                       (when-let [default-selectors (get-in @state [:selectors :default])]
                         (when (not= default-selectors prev-selectors)
                           (-> ast
                             (assoc-in [:params :selectors] default-selectors)))))
               (assoc :key `sel/change-active))}))

(defrecord Router []
  cp/Lifecycle
  (start [this]
    (let [{:keys [reconciler]} (-> this :test/renderer :app)
          history (setup!
                    (fn on-route-change [{:keys [filter selectors]}]
                      (when filter
                        (om/transact! reconciler
                          `[(set-page-filter
                              ~{:filter filter})]))
                      (om/transact! reconciler
                        `[(sel/set-selectors-from-url
                            ~{:prev-selectors (get-in @(om/app-state reconciler) [:selectors :active])})])))]
      (defmethod m/mutate `renderer/set-filter [{:keys [state]} _ {:keys [filter]}]
        {:action #(assoc-fragment! history :filter (name filter))})
      (defmethod m/mutate `sel/set-selector [{:keys [state ast]} _ {:keys [selector checked?]}]
        {:action #(do (swap! state update-in [:selectors :active]
                         (if checked? conj disj) selector)
                    (assoc-fragment! history :selectors
                      (get-in @state [:selectors :active])))
         :remote (-> ast (assoc :key `sel/change-active)
                   (assoc :params {:selectors (get-in @state [:selectors :active])}))})

      this))
  (stop [this]
    (remove-method m/mutate `renderer/set-filter)
    (remove-method m/mutate `sel/set-selector)
    this))

(defn make-router []
  (cp/using
    (map->Router {})
    [:test/renderer]))
