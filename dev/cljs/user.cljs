(ns cljs.user
  (:require
    [untangled-spec.runner :as runner]
    [untangled-spec.tests-to-run]))

(enable-console-print!)

(defonce runner (runner/test-runner))
(def on-load #(runner/run-tests runner))
