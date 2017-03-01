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
     "Creates a renderer for server (clojure) tests when using `test-suite`.

      WARNING: You should not need to use this directly, instead you should be starting a server `test-suite`
      and going to `localhost:PORT/untangled-spec-server-tests.html`."
     [& [opts]]
     (cp/start
       (cp/system-map
         :test/renderer (renderer/make-test-renderer opts)
         :test/router (router/make-router)))))

#?(:clj
   (defmacro def-test-suite
     "For use in defining a browser (cljs) test suite. Defines a function `suite-name` to re-run tests.

      WARNING: You should also be defining a cljsbuild that emits your client tests to `js/test/test.js`.

      NOTE: Will fail if used outside of clojurescript files."
     [suite-name regex selectors]
     (when-not (im/cljs-env? &env)
       (throw (ex-info "CANNOT BE USED FOR ANYTHING BUT CLOJURESCRIPT" {})))
     `(do
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
     "For use in defining a server (clojure) test suite. Returns a system that can be `start`-ed and `stop`-ed.

      WARNING: It is up to you to manage the lifecycle of the returned system,
      ie: make sure you don't accidentally reload away a started system without stopping it first!
      (See: clojure.tools.namespace.repl/disable-reload!)

      NOTE: Will fail if used outside of clojure files."
     [{:as opts :keys [test-paths]} selectors]
     (when (im/cljs-env? &env)
       (throw (ex-info "CANNOT BE USED FOR ANYTHING BUT CLOJURE" {})))
     `(do
        (defonce _# (sel/initialize-selectors! ~selectors))
        (runner/test-runner ~(merge opts {:selectors selectors})
          (fn []
            (let [test-nss#
                  (mapcat (comp tools-ns-find/find-namespaces-in-dir io/file)
                    ~test-paths)]
              (apply require test-nss#)
              (apply clojure.test/run-tests test-nss#)))))))
