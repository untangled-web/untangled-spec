(ns untangled-spec.runner
  #?(:cljs
     (:require-macros
       [untangled-spec.runner :refer [define-assert-exprs!]]))
  (:require
    [clojure.test :as t]
    [cljs.test #?@(:cljs (:include-macros true))]
    [com.stuartsierra.component :as cp]
    [untangled-spec.assertions :as ae]
    [untangled-spec.reporter :as report]
    #?@(:cljs ([untangled-spec.renderer :as renderer]))
    #?@(:clj  ([om.next.server :as oms]
               [untangled-spec.impl.macros :as im]
               #_#_#_[untangled.server.core :as usc]
               [untangled.websockets.protocols :as ws]
               [untangled.websockets.components.channel-server :as wcs]))))

#?(:clj (defmacro define-assert-exprs! []
          (let [prefix (im/if-cljs &env "cljs.test" "clojure.test")
                do-report (symbol prefix "do-report")
                t-assert-expr (im/if-cljs &env cljs.test/assert-expr clojure.test/assert-expr)
                do-assert-expr
                (fn [[msg form :as args]]
                  `(~do-report ~(ae/assert-expr msg form)))]
            (defmethod t-assert-expr '= [& args] (do-assert-expr args))
            (defmethod t-assert-expr 'exec [& args] (do-assert-expr args))
            (defmethod t-assert-expr 'throws? [& args] (do-assert-expr args)))))
(define-assert-exprs!)

#?(:clj
   nil #_(defrecord ChannelListener [channel-server]
     wcs/WSListener
     (client-dropped [this ws-net cid]
       (prn :dropped cid))
     (client-added [this ws-net cid]
       (prn :added cid))

     cp/Lifecycle
     (start [this]
       (wcs/add-listener channel-server this)
       this)
     (stop [this]
       (wcs/remove-listener channel-server this)
       this)))

#?(:clj
   nil #_(defn- make-channel-listener []
     (cp/using
       (map->ChannelWrapper {})
       [:channel-server])))

(defn render-tests [system]
  #?(:cljs (renderer/render-tests system {:test-report @(:state (:test/runner system))})
     :clj (assert false "WIP TODO FIXME"
            #_(mapv #(ws/push (:ws-net FIXME) %
                       `renderer/render-tests
                       {:test-report @(:state FIXME)})
                (:any @(:connected-uids (:ws-net FIXME)))))))

(defn run-tests [system]
  (report/with-untangled-reporting system render-tests
    (t/run-all-tests #".*-spec"
      #?(:cljs (t/empty-env ::TestRunner)))))

(defrecord TestRunner [state path]
  cp/Lifecycle
  (start [this] this)
  (stop [this]
    ;; TODO: is this necessary?
    (reset! state (report/make-testreport))
    (reset! path  [])
    this))

(defn- make-test-runner []
  (map->TestRunner
    {:state (atom (report/make-testreport))
     :path  (atom [])}))

(defn test-runner []
  (cp/start
    #?(:cljs (cp/system-map
               :test/renderer (renderer/make-test-renderer
                                #(run-tests (assoc % :test/renderer %)))
               :test/runner (make-test-runner))
       :clj (assert false "WIP TODO FIXME"
              ;; TODO: make-untangled-server here
              ))))
