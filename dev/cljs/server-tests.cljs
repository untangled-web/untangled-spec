(ns cljs.server-tests
  (:require
    [untangled-spec.runner :as runner]
    [untangled-spec.tests-to-run]))

(enable-console-print!)

(defonce runner
  (runner/test-renderer {}))
