(ns clj.user
  (:require
    [clj.system :refer [figwheel system start-figwheel]]
    [clojure.tools.namespace.repl :as tools-ns-repl]
    [com.stuartsierra.component :as cp]
    [untangled-spec.suite :as suite]
    [untangled-spec.selectors :as sel]))

;; SERVER TESTS

(defn refresh [& args]
  {:pre [(not @system)]}
  (apply tools-ns-repl/refresh args))

(defn start []
  (reset! system
    (suite/test-suite {:source-paths ["src"] :test-paths ["test"]}
      {:default #{::sel/none :focused}
       :available #{:focused :should-fail}})))

(defn stop []
  (when @system
    (swap! system cp/stop))
  (reset! system nil))

(defn reset [] (stop) (refresh :after 'clj.user/start))

(defn engage [& build-ids]
  (stop) (start) (start-figwheel build-ids))
