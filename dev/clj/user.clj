(ns clj.user
  (:require
    [clj.system :refer [system]]
    [clojure.tools.namespace.repl :as tools-ns-repl]
    [com.stuartsierra.component :as cp]
    [figwheel-sidecar.system :as fsys]
    [untangled-spec.runner :as runner]
    ))

(def figwheel-config (fsys/fetch-config))
(def figwheel (atom nil))

(defn start-figwheel
  "Start Figwheel on the given builds, or defaults to build-ids in `figwheel-config`."
  ([]
   (let [props (System/getProperties)
         all-builds (->> figwheel-config :data :all-builds (mapv :id))]
     (start-figwheel (keys (select-keys props all-builds)))))
  ([build-ids]
   (let [default-build-ids (-> figwheel-config :data :build-ids)
         build-ids (if (empty? build-ids) default-build-ids build-ids)
         preferred-config (assoc-in figwheel-config [:data :build-ids] build-ids)]
     (reset! figwheel (cp/system-map
                        :css-watcher (fsys/css-watcher {:watch-paths ["resources/public/css"]})
                        :figwheel-system (fsys/figwheel-system preferred-config)))
     (println "STARTING FIGWHEEL ON BUILDS: " build-ids)
     (swap! figwheel cp/start)
     (fsys/cljs-repl (:figwheel-system @figwheel)))))

;; SERVER TESTS

(tools-ns-repl/set-refresh-dirs "src" "dev" "test")

(defn refresh [& args]
  {:pre [(not @system)]}
  (apply tools-ns-repl/refresh args))

(defn start []
  (reset! system
    (runner/test-runner
      {:test-paths ["test"]
       :ns-regex #"untangled-spec.*-spec"})))

(defn stop []
  (when @system
    (swap! system cp/stop))
  (reset! system nil))

(defn reset [] (stop) (refresh :after 'clj.user/start))

(defn engage [& build-ids]
  (stop) (start) (start-figwheel build-ids))
