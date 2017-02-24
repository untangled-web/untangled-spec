(ns untangled-spec.suite
  #?(:cljs (:require-macros
             [untangled-spec.suite]))
  (:require
    [com.stuartsierra.component :as cp]
    [untangled-spec.runner :as runner]
    [untangled-spec.selectors :as sel]
    #?@(:cljs ([untangled-spec.renderer :as renderer]
               [untangled-spec.router :as router]))
    #?@(:clj ([clojure.java.io :as io]
              [clojure.tools.namespace.find :as tools-ns-find]
              [untangled-spec.impl.macros :as im]))))

#?(:cljs
   (defn test-renderer
     "For explicit use in creating a renderer for server tests"
     [& [opts]]
     (cp/start
       (cp/system-map
         :test/renderer (renderer/make-test-renderer opts)
         :test/router (router/make-router)))))

#?(:clj
   (defmacro def-test-suite
     "For use in defining a browser (cljs) test suite.
      Defs a function to re-run-tests onto `suite-name`"
     [suite-name regex selectors]
     (when-not (im/cljs-env? &env)
       (throw (ex-info "CANNOT BE USED FOR ANYTHING BUT CLOJURESCRIPT" {})))
     `(do
        ;;TODO is this necessary, or can it be put back in runner/start ?
        (defonce _# (sel/initialize-selectors! ~selectors))
        (defonce renderer#
          (test-renderer
            {:with-websockets? false}))
        (def test-system#
          (runner/test-runner {:ns-regex ~regex}
            (fn []
              (cljs.test/run-all-tests ~regex
                (cljs.test/empty-env ::TestRunner)))
            renderer#))
        (defn ~suite-name [& _#]
          (runner/run-tests (:test/runner test-system#))))))

#?(:clj
   (defmacro test-suite
     "For use in defining a server (clojure) test suite.
      Returns a system that can be `start`-ed and `stop`-ed."
     [{:as opts :keys [test-paths]} selectors]
     (when (im/cljs-env? &env)
       (throw (ex-info "CANNOT BE USED FOR ANYTHING BUT CLOJURE" {})))
     `(do (defonce _# (sel/initialize-selectors! ~selectors))
        (runner/test-runner ~(merge opts {:selectors selectors})
          (fn []
            (let [test-nss#
                  (mapcat (comp tools-ns-find/find-namespaces-in-dir io/file)
                    ~test-paths)]
              (apply require test-nss#)
              (apply clojure.test/run-tests test-nss#)))))))
