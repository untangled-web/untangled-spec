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
                t-assert-expr (im/if-cljs &env cljs.test/assert-expr clojure.test/assert-expr)]
            (defmethod t-assert-expr '= [_ msg form]
              `(~do-report ~(ae/assert-expr msg form)))
            (defmethod t-assert-expr 'exec [_ msg form]
              `(~do-report ~(ae/assert-expr msg form)))
            (defmethod t-assert-expr 'throws? [_ msg form]
              `(~do-report ~(ae/assert-expr msg form))))))
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

(defn render-tests [this]
  #?(:cljs (renderer/render-tests {:test-report @(:state this)})
     :clj nil #_(mapv #(ws/push (:ws-net this) %
                   `renderer/render-tests
                   {:test-report @(:state this)})
            (:any @(:connected-uids (:ws-net this))))))

(defn install-untangled-reporting! [this]
  (defmethod t/report :default [t]
    (case (:type t)
      :pass (report/pass this t)
      :error (report/error this t)
      :fail (report/fail this t)
      :begin-test-ns (report/begin-namespace this t)
      :end-test-ns (report/end-namespace this t)
      :begin-specification (report/begin-specification this t)
      :end-specification (report/end-specification this t)
      :begin-behavior (report/begin-behavior this t)
      :end-behavior (report/end-behavior this t)
      :begin-manual (report/begin-manual this t)
      :end-manual (report/end-manual this t)
      :begin-provided (report/begin-provided this t)
      :end-provided (report/end-provided this t)
      :summary (do (report/summary this t)
                 (render-tests this))
      :ok)))

(defn run-tests [this]
  (t/run-all-tests #".*-spec"
    #?(:cljs (t/empty-env ::TestRunner))))

(defrecord TestRunner [state path]
  cp/Lifecycle
  (start [this]
    (install-untangled-reporting! this)
    (run-tests this)
    this)
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
  #?(:cljs (make-test-runner)
     :clj (assert false "WIP TODO FIXME")
     ;; TODO: make-untangled-server here
     ))
