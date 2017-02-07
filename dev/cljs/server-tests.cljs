(ns cljs.server-tests
  (:require
    [untangled-spec.suite :as suite]
    [untangled-spec.tests-to-run]))

(enable-console-print!)

(defonce runner
  (suite/test-renderer {}))
