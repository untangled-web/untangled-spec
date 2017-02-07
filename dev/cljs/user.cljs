(ns cljs.user
  (:require
    [clojure.core.async :as a]
    [untangled-spec.runner :as runner]
    [untangled-spec.tests-to-run]))

(enable-console-print!)

;;TODO: into a macro
(defonce selectors-chan (a/chan 10))
(defonce renderer (runner/test-renderer {:selectors-chan selectors-chan :with-websockets? false}))
(def runner (runner/test-runner
              {:ns-regex #"untangled-spec.*-spec"
               :renderer renderer
               :selectors-chan selectors-chan
               :selectors {:default (complement :integration)
                           :integration :integration
                           :focused :focused}
               :test-paths ["test"]}))
(def on-load #(runner/run-tests (:test/runner runner)))
