(ns untangled-spec.runner
  #?(:cljs
     (:require-macros
       [untangled-spec.runner :refer [define-assert-exprs!]]))
  (:require
    [clojure.test :as t]
    [cljs.test #?@(:cljs (:include-macros true))]
    [com.stuartsierra.component :as cp]
    [untangled-spec.assertions :as ae]
    [untangled-spec.reporter :as reporter]
    #?@(:cljs ([untangled-spec.renderer :as renderer]))
    #?@(:clj  ([figwheel-sidecar.system :as fsys]
               [om.next.server :as oms]
               [ring.util.response :as resp]
               [untangled-spec.impl.macros :as im]
               [untangled-spec.watch :as watch]
               [untangled.server.core :as usc]
               [untangled.websockets.protocols :as ws]
               [untangled.websockets.components.channel-server :as wcs]))))

#?(:clj
   (defmacro define-assert-exprs! []
     (let [test-ns (im/if-cljs &env "cljs.test" "clojure.test")
           do-report (symbol test-ns "do-report")
           t-assert-expr (im/if-cljs &env cljs.test/assert-expr clojure.test/assert-expr)
           do-assert-expr
           (fn [args]
             (let [[msg form] (cond-> args (im/cljs-env? &env) rest)]
               `(~do-report ~(ae/assert-expr msg form))))]
       (defmethod t-assert-expr '=       eq-ae     [& args] (do-assert-expr args))
       (defmethod t-assert-expr 'exec    fn-ae     [& args] (do-assert-expr args))
       (defmethod t-assert-expr 'throws? throws-ae [& args] (do-assert-expr args))
       nil)))
(define-assert-exprs!)

#?(:clj
   (defn- send-render-tests-msg
     ([system tr cid]
      (let [cs (:channel-server system)]
        (ws/push cs cid `untangled-spec.renderer/render-tests tr)))
     ([system tr]
      (->> system :channel-server
        :connected-cids deref :any
        (mapv (partial send-render-tests-msg system tr))))))

#?(:clj
   (defrecord ChannelListener [channel-server]
     ws/WSListener
     (client-dropped [this cs cid]
       (prn :dropped cid))
     (client-added [this cs cid]
       (prn :added cid)
       (send-render-tests-msg
         this {:test-report (reporter/get-test-report (:test/reporter this))} cid))

     cp/Lifecycle
     (start [this]
       (wcs/add-listener wcs/listeners this)
       this)
     (stop [this]
       (wcs/remove-listener wcs/listeners this)
       this)))

#?(:clj
   (defn- make-channel-listener []
     (cp/using
       (map->ChannelListener {})
       [:channel-server :test/reporter])))

(defn- render-tests [{:keys [test/reporter] :as system}]
  (let [render* #?(:cljs renderer/render-tests :clj send-render-tests-msg)]
    (render* system {:test-report (reporter/get-test-report reporter)})))

(defn- run-tests [system]
  (reporter/with-untangled-reporting
    system render-tests
    ((:test! system))))

(defrecord TestRunner [opts]
  cp/Lifecycle
  (start [this]
    (run-tests this)
    #?(:clj (watch/on-changes #(run-tests this)))
    this)
  (stop [this] this))

(defn- make-test-runner [opts test!]
  (cp/using
    (map->TestRunner {:opts opts :test! test!})
    [:test/reporter #?(:cljs :test/renderer :clj :channel-server)]))

(defn test-runner* [opts test!]
  (cp/start
    #?(:cljs (cp/system-map
               :test/renderer (renderer/make-test-renderer {})
               :test/runner (make-test-runner opts test!)
               :test/reporter (reporter/make-test-reporter))
       :clj (let [api-read (fn [& args] (prn :read args))
                  api-mutate (fn [& args] (prn :mutate args))]
              (usc/make-untangled-server
                :config-path "config/untangled-spec.edn"
                :parser (oms/parser {:read api-read :mutate api-mutate})
                :components {:channel-server (wcs/make-channel-server)
                             :channel-listener (make-channel-listener)
                             :test/runner (make-test-runner opts test!)
                             :test/reporter (reporter/make-test-reporter)}
                :extra-routes {:routes   ["/" {"chsk" :web-socket
                                               "server-tests.html" :server-tests}]
                               :handlers {:web-socket wcs/route-handlers
                                          :server-tests (fn [{:keys [request]} _match]
                                                          (prn :match _match)
                                                          (resp/resource-response "server-tests.html"
                                                            {:root "public"}))}})))))

#?(:clj
   (defmacro test-runner [opts]
     (let [t-prefix (im/if-cljs &env "cljs.test" "clojure.test")
           run-all-tests (symbol t-prefix "run-all-tests")
           empty-env (symbol t-prefix "empty-env")]
       `(test-runner* ~opts
          (fn []
            (~run-all-tests
              ~(:ns-regex opts)
              ~@(im/if-cljs &env `[(~empty-env ::TestRunner)] [])))))))

#?(:cljs (defn test-renderer [opts]
           (cp/start (renderer/make-test-renderer opts))))
