(ns untangled-spec.suite
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
   (defn test-renderer [opts]
     (cp/start (cp/system-map
                 :test/renderer (renderer/make-test-renderer opts)
                 :test/router (router/make-router)))))

#?(:clj
   (defmacro def-test-suite [suite-name regex selectors]
     (when (not (im/cljs-env? &env))
       (throw (ex-info "CANNOT BE USED FOR ANYTHING BUT CLOJURESCRIPT" {})))
     (let [t-prefix (im/if-cljs &env "cljs.test" "clojure.test")
           run-all-tests (symbol t-prefix "run-all-tests")
           empty-env (symbol t-prefix "empty-env")]
       `(do
          (defonce selectors-chan# (a/chan 10))
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
                                (~run-all-tests ~regex
                                  (~empty-env ::TestRunner)))))
          (defn ~suite-name [& _#]
            (runner/run-tests (:test/runner test-system#)))))))

#?(:clj
   (let []
     (defmacro test-suite [test-paths selectors]
       (when (im/cljs-env? &env)
         (throw (ex-info "CANNOT BE USED FOR ANYTHING BUT CLOJURE" {})))
       (let [t-prefix (im/if-cljs &env "cljs.test" "clojure.test")
             run-tests (symbol t-prefix "run-tests")]
         `(runner/test-runner ~{:test-paths test-paths
                                :selectors selectors}
            (fn []
              (let [nss-in-dirs#
                    (partial mapcat
                      (comp tools-ns-find/find-namespaces-in-dir io/file))]
                (apply ~run-tests
                  (nss-in-dirs# ~test-paths)))))))))
