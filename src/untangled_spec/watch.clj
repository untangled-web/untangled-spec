(ns untangled-spec.watch
  (:require
    [clojure.tools.namespace.dir :as tools-ns-dir]
    [clojure.tools.namespace.track :as tools-ns-track]
    [com.stuartsierra.component :as cp]))

(defn- make-change-tracker []
  (tools-ns-track/tracker))

(let [prev-failed (atom nil)]
  (defn- scan-for-changes [tracker watch-dirs]
    (try (let [new-tracker (apply tools-ns-dir/scan tracker watch-dirs)]
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

(defrecord ChangeListener [watching? run-tests]
  cp/Lifecycle
  (start [this]
    (async
      (loop [tracker (make-change-tracker)]
        (let [new-tracker (scan-for-changes tracker [])
              something-changed? (not= new-tracker tracker)]
          (when @watching?
            (when something-changed?
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

(defn on-change-listener [run-tests]
  (cp/using
    (map->ChangeListener
      {:watching? (atom true)
       :run-tests run-tests})
    [:test/runner]))
