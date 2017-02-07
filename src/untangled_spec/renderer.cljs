(ns untangled-spec.renderer
  (:require-macros
    [cljs.core.async.macros :as a])
  (:require
    [clojure.core.async :as a]
    [clojure.string :as str]
    [com.stuartsierra.component :as cp]
    [goog.dom :as gdom]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [pushy.core :as pushy]
    [untangled.client.core :as uc]
    [untangled.client.mutations :as m]
    [untangled-spec.dom.edn-renderer :refer [html-edn]]
    [untangled-spec.diff :as diff]
    [untangled.websockets.networking :as wn]))

(enable-console-print!)

(defn itemclass [{:keys [failed error passed manual]}]
  (str "test-"
    (cond
      (pos? failed) "failed"
      (pos? error) "error"
      (pos? passed) "passed"
      (pos? manual) "manual"
      :else "pending")))

(defn color-favicon-data-url [color]
  (let [cvs (.createElement js/document "canvas")]
    (set! (.-width cvs) 16)
    (set! (.-height cvs) 16)
    (let [ctx (.getContext cvs "2d")]
      (set! (.-fillStyle ctx) color)
      (.fillRect ctx 0 0 16 16))
    (.toDataURL cvs)))

(defn change-favicon-to-color [color]
  (let [icon (.getElementById js/document "favicon")]
    (set! (.-href icon) (color-favicon-data-url color))))

(defn has-status? [p]
  (fn has-status?* [x]
    (or (p (:status x))
        (and
          (seq (:test-items x))
          (seq (filter has-status?* (:test-items x)))))))

(def filters
  (let [report-as (fn [status] #(update % :status select-keys [status]))
        no-test-results #(dissoc % :test-results)]
    {:all (map identity)
     :failing (filter (comp #(some pos? %) (juxt :failed :error) :status))
     :manual  (comp (filter (has-status? #(-> % :manual pos?)))
                (map no-test-results)
                (map (report-as :manual)))
     :passing (comp (filter (comp pos? :passed :status))
                (map (report-as :passed)))
     :pending (comp (filter (has-status? #(->> % vals (apply +) zero?)))
                (map no-test-results)
                (map (report-as :pending)))}))

(defui ^:once Foldable
  Object
  (initLocalState [this] {:folded? true})
  (render [this]
    (let [{:keys [folded?]} (om/get-state this)
          {:keys [render]} (om/props this)
          {:keys [title value classes]} (render folded?)]
      (dom/div nil
        (dom/a #js {:href "javascript:void(0);"
                    :className classes
                    :onClick #(om/update-state! this update :folded? not)}
          (if folded? \u25BA \u25BC)
          (if folded?
            (str (apply str (take 40 title))
              (when (< 40 (count title)) "..."))
            (str title)))
        (dom/div #js {:className (when folded? "hidden")}
          value)))))
(def ui-foldable (om/factory Foldable {:keyfn #(gensym "foldable")}))

(defui ^:once ResultLine
  Object
  (render [this]
    (let [{:keys [title value stack type]} (om/props this)]
      (dom/tr nil
        (dom/td #js {:className (str "test-result-title "
                                  (name type))}
          title)
        (dom/td #js {:className "test-result"}
          (dom/code nil
            (ui-foldable
              {:render (fn [folded?]
                         {:title (if stack (str value)
                                   (if folded? (str value) title))
                          :value (if stack stack (if-not folded? (html-edn value)))
                          :classes (if stack "stack")})})))))))
(def ui-result-line (om/factory ResultLine {:keyfn #(gensym "result-line")}))

(defui ^:once HumanDiffLines
  Object
  (render [this]
    (let [d (om/props this)
          {:keys [exp got path]} (diff/extract d)]
      (dom/table #js {:className "human-diff-lines"}
        (dom/tbody nil
          (when (seq path)
            (dom/tr #js {:className "path"}
              (dom/td nil "at: ")
              (dom/td nil (str path))))
          (dom/tr #js {:className "expected"}
            (dom/td nil "exp: ")
            (dom/td nil (html-edn exp)))
          (dom/tr #js {:className "actual"}
            (dom/td nil "got: ")
            (dom/td nil (html-edn got))))))))
(def ui-human-diff-lines (om/factory HumanDiffLines {:keyfn #(gensym "human-diff-lines")}))

(defui ^:once HumanDiff
  Object
  (render [this]
    (let [{:keys [diff actual]} (om/props this)
          [fst rst] (split-at 2 diff)]
      (->> (dom/div nil
             (when (associative? actual)
               (ui-foldable {:render (fn [folded?]
                                       {:title "DIFF"
                                        :value (html-edn actual diff)})}))
             (mapv ui-human-diff-lines fst)
             (if (seq rst)
               (ui-foldable {:render
                             (fn [folded?]
                               {:title "& more"
                                :value (mapv ui-human-diff-lines rst)
                                :classes ""})})))
        (dom/td nil)
        (dom/tr #js {:className "human-diff"}
          (dom/td nil "DIFFS:"))))))
(def ui-human-diff (om/factory HumanDiff {:keyfn #(gensym "human-diff")}))

(defui ^:once TestResult
  Object
  (render [this]
    (let [{:keys [where message extra actual expected stack diff]} (om/props this)]
      (->> (dom/tbody nil
             (dom/tr nil
               (dom/td #js {:className "test-result-title"}
                 "Where: ")
               (dom/td #js {:className "test-result"}
                 (str/replace (str where)
                   #"G__\d+" "")))
             (when message
               (ui-result-line {:type :normal
                                :title "ASSERTION: "
                                :value message}))
             (ui-result-line {:type :normal
                              :title "Actual: "
                              :value actual
                              :stack stack})
             (ui-result-line {:type :normal
                              :title "Expected: "
                              :value expected})
             (when extra
               (ui-result-line {:type :normal
                                :title "Message: "
                                :value extra}))
             (when diff
               (ui-human-diff {:actual actual
                               :diff diff})))
        (dom/table nil)
        (dom/li nil)))))
(def ui-test-result (om/factory TestResult {:keyfn :id}))

(declare ui-test-item)

(defui ^:once TestItem
  Object
  (render [this]
    (let [{:keys [current-filter] :as test-item-data} (om/props this)]
      (dom/li #js {:className "test-item "}
        (dom/div nil
          (dom/span #js {:className (itemclass (:status test-item-data))}
            (:name test-item-data))
          (dom/ul #js {:className "test-list"}
            (mapv ui-test-result
              (:test-results test-item-data)))
          (dom/ul #js {:className "test-list"}
            (sequence
              (comp (filters current-filter)
                (map #(assoc % :current-filter current-filter))
                (map ui-test-item))
              (:test-items test-item-data))))))))
(def ui-test-item (om/factory TestItem {:keyfn :id}))

(defui ^:once TestNamespace
  Object
  (initLocalState [this] {:folded? false})
  (render
    [this]
    (let [{:keys [current-filter] :as tests-by-namespace} (om/props this)
          {:keys [folded?]} (om/get-state this)]
      (dom/li #js {:className "test-item"}
        (dom/div #js {:className "test-namespace"}
          (dom/a #js {:href "javascript:void(0)"
                      :style #js {:textDecoration "none"} ;; TODO: refactor to css
                      :onClick #(om/update-state! this update :folded? not)}
            (dom/h2 #js {:className (itemclass (:status tests-by-namespace))}
              (if folded? \u25BA \u25BC)
              " Testing " (str (:name tests-by-namespace))))
          (dom/ul #js {:className (if folded? "hidden" "test-list")}
            (sequence (comp (filters current-filter)
                        (map #(assoc % :current-filter current-filter))
                        (map ui-test-item))
              (:test-items tests-by-namespace))))))))
(def ui-test-namespace (om/factory TestNamespace {:keyfn :name}))

(defui ^:once FilterSelector
  Object
  (render [this]
    (let [{:keys [this-filter current-filter]} (om/props this)]
      (dom/a #js {:href "javascript:void(0)"
                  :onClick #(om/transact! this `[(set-filter ~{:filter this-filter})])
                  :className (if (= this-filter current-filter)
                               "selected" "")}
        (name this-filter)))))
(def ui-filter-selector (om/factory FilterSelector {:keyfn identity}))

(defui ^:once Filters
  Object
  (render [this]
    (let [{:keys [current-filter]} (om/props this)]
      (dom/div #js {:name "filters" :className "filter-controls"}
        (dom/label #js {:htmlFor "filters"} "Filter: ")
        (sequence
          (comp (map #(hash-map
                        :this-filter %
                        :current-filter current-filter))
            (map ui-filter-selector))
          (keys filters))))))
(def ui-filters (om/factory Filters {}))

(defui ^:once TestCount
  Object
  (render [this]
    (let [{:keys [passed failed error namespaces]} (om/props this)
          total (+ passed failed error)]
      (if (pos? (+ failed error))
        (change-favicon-to-color "#d00")
        (change-favicon-to-color "#0d0"))
      (dom/div #js {:className "test-count"}
        (dom/h2 nil
          (str "Tested " (count namespaces) " namespaces containing "
            total  " assertions. "
            passed " passed "
            failed " failed "
            error  " errors"))))))
(def ui-test-count (om/factory TestCount {:keyfn identity}))

(defui ^:once TestReport
  static om/IQuery
  (query [this] [:ui/react-key :test-report :report/filter])
  Object
  (render [this]
    (let [{:keys [test-report ui/react-key] current-filter :report/filter} (om/props this)]
      (dom/section #js {:key react-key :className "test-report"}
        (ui-filters {:current-filter current-filter})
        (dom/ul #js {:className "test-list"}
          (sequence
            (comp
              (filters current-filter)
              (map #(assoc % :current-filter current-filter))
              (map ui-test-namespace))
            (sort-by :name (:namespaces test-report))))
        (ui-test-count test-report)))))

(defmethod m/mutate `render-tests [{:keys [state]} _ new-report]
  {:action #(swap! state merge new-report)})

(defn render-tests [system test-report]
  (let [app (:app (:test/renderer (:test/renderer system)))]
    (om/transact! (om/app-root (:reconciler app))
      `[(render-tests ~test-report)])))

(defmethod wn/push-received `render-tests
  [{:keys [reconciler]} {test-report :msg}]
  (om/transact! (om/app-root reconciler)
    `[(render-tests ~test-report)]))

(defmethod m/mutate `set-page-filter [{:keys [state ast]} k {:keys [filter]}]
  {:action #(swap! state assoc :report/filter
              (or (and (contains? filters filter)
                    filter)
                  (and (js/console.warn "INVALID FILTER: " (str filter))
                    :all)))})

(defrecord TestRenderer [root target with-websockets? selectors-chan]
  cp/Lifecycle
  (start [this]
    (defmethod m/mutate 'run-tests/with-new-selectors
      [_ _ {:keys [selectors]}]
      ;;TODO each :action should update ui accordingly
      (if with-websockets?
        {:remote true}
        {:action #(when selectors (a/go (a/>! selectors-chan selectors)))}))
    (let [app (uc/new-untangled-client
                :networking (and with-websockets?
                              (wn/make-channel-client "/chsk"
                                :global-error-callback (constantly nil)))
                :initial-state {:report/filter :all})]
      (assoc this :app (uc/mount app root target))))
  (stop [this]
    (remove-method m/mutate 'run-tests/with-new-selectors)
    (assoc this :app nil)))

(defn make-test-renderer [opts]
  (let [opts (merge {:root TestReport
                     :target "spec-report"
                     :with-websockets? true}
               opts)]
    (map->TestRenderer opts)))
