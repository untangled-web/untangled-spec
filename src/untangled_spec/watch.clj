(ns untangled-spec.watch
  (:require
    [clojure.tools.namespace.dir :as tools-ns-dir]
    [clojure.tools.namespace.find :refer [clj]]
    [clojure.tools.namespace.track :as tools-ns-track]
    [com.stuartsierra.component :as cp]))

(defn- make-change-tracker [watch-dirs]
  (tools-ns-dir/scan-dirs (tools-ns-track/tracker) watch-dirs {:platform clj}))

(let [prev-failed (atom nil)]
  (defn- scan-for-changes [tracker watch-dirs]
    (try (let [new-tracker (tools-ns-dir/scan-dirs tracker watch-dirs {:platform clj})]
           (reset! prev-failed false)
           new-tracker)
         (catch Exception e
           (when-not @prev-failed
             (println e))
           (reset! prev-failed true)
           ;; return the same tracker so we dont try to run tests
           tracker))))

(defmacro async [& body]
  `(let [ns# *ns*]
     (.start
       (Thread.
         (fn []
           (binding [*ns* ns#]
             ~@body))))))

(defn something-changed? [new-tracker curr-tracker]
  (not= new-tracker curr-tracker))

(defrecord ChangeListener [watching? watch-dirs run-tests]
  cp/Lifecycle
  (start [this]
    (async
      (loop [tracker (make-change-tracker watch-dirs)]
        (let [new-tracker (scan-for-changes tracker watch-dirs)]
          (when @watching?
            (when (something-changed? new-tracker tracker)
              (try (run-tests (:test/runner this))
                (catch Exception e (.printStackTrace e))))
            (do (Thread/sleep 200)
              (recur (dissoc new-tracker
                             ::tools-ns-track/load
                             ::tools-ns-track/unload)))))))
    this)
  (stop [this]
    (reset! watching? false)
    this))

(defn on-change-listener [{:keys [source-paths test-paths]} run-tests]
  (cp/using
    (map->ChangeListener
      {:watching? (atom true)
       :watch-dirs (concat source-paths test-paths)
       :run-tests run-tests})
    [:test/runner]))
