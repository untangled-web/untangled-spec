(ns untangled-spec.suite
  #?(:cljs (:require-macros
             [untangled-spec.suite]))
  (:require
    [clojure.core.async :as a]
    [com.stuartsierra.component :as cp]
    [untangled-spec.runner :as runner]
    #?@(:cljs ([untangled-spec.renderer :as renderer]
               [untangled-spec.router :as router]))
    #?@(:clj ([clojure.java.io :as io]
              [clojure.tools.namespace.find :as tools-ns-find]
              [untangled-spec.impl.macros :as im]))))

#?(:cljs
   (defn test-renderer
     "For explicit use in creating a renderer for server tests"
     [& [opts]]
     (cp/start (cp/system-map
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
        (defonce selectors-chan# (cljs.core.async/chan 10))
        (defonce renderer#
          (test-renderer
            {:selectors-chan selectors-chan#
             :with-websockets? false}))
        (def test-system# (runner/test-runner
                            {:ns-regex ~regex
                             :renderer renderer#
                             :selectors-chan selectors-chan#
                             :selectors ~selectors}
                            (fn []
                              (cljs.test/run-all-tests ~regex
                                (cljs.test/empty-env ::TestRunner)))))
        (defn ~suite-name [& _#]
          (runner/run-tests (:test/runner test-system#))))))

#?(:clj
   (defmacro test-suite
     "For use in defining a server (clojure) test suite.
      Returns a system that can be `start`-ed and `stop`-ed."
     [test-paths selectors]
     (when (im/cljs-env? &env)
       (throw (ex-info "CANNOT BE USED FOR ANYTHING BUT CLOJURE" {})))
     `(runner/test-runner ~{:test-paths test-paths
                            :selectors selectors}
        (fn []
          (let [nss-in-dirs#
                (partial mapcat
                  (comp tools-ns-find/find-namespaces-in-dir io/file))]
            (apply clojure.test/run-tests
              (nss-in-dirs# ~test-paths)))))))
