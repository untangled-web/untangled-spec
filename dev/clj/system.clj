(ns clj.system
  (:require
    [clojure.tools.namespace.repl :as tools-ns-repl]
    [com.stuartsierra.component :as cp]
    [figwheel-sidecar.system :as fsys]))

(tools-ns-repl/disable-reload!)

(tools-ns-repl/set-refresh-dirs "src" "test" "dev")

(defonce system (atom nil))

(defonce figwheel (atom nil))

(defn start-figwheel
  "Start Figwheel on the given builds, or defaults to build-ids in `figwheel-config`."
  ([]
   (let [props (System/getProperties)
         figwheel-config (fsys/fetch-config)
         all-builds (->> figwheel-config :data :all-builds (mapv :id))]
     (start-figwheel (keys (select-keys props all-builds)))))
  ([build-ids]
   (let [figwheel-config (fsys/fetch-config)
         default-build-ids (-> figwheel-config :data :build-ids)
         build-ids (if (empty? build-ids) default-build-ids build-ids)
         preferred-config (assoc-in figwheel-config [:data :build-ids] build-ids)]
     (reset! figwheel (cp/system-map
                        :css-watcher (fsys/css-watcher {:watch-paths ["resources/public/css"]})
                        :figwheel-system (fsys/figwheel-system preferred-config)))
     (println "STARTING FIGWHEEL ON BUILDS: " build-ids)
     (swap! figwheel cp/start)
     (fsys/cljs-repl (:figwheel-system @figwheel)))))
