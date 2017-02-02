(ns cljs.user
  (:require
    [untangled-spec.runner :as runner]
    [untangled-spec.tests-to-run]))

(enable-console-print!)

(defonce runner (runner/test-runner
                  {:ns-regex #"untangled-spec.*-spec"
                   :test-paths ["test"]}))
(def on-load #(runner/run-tests (:test/runner runner)))
