(ns cljs.user
  (:require
    [clojure.spec.test :as st]
    [untangled-spec.tests-to-run]
    [untangled-spec.suite :as ts]
    [untangled-spec.selectors :as sel]))

(enable-console-print!)

;;optional, but can be helpful
(st/instrument)

;;define on-load as a fn that re-runs (and renders) the tests
;;for use by figwheel's :on-jsload
(ts/def-test-suite on-load #"untangled-spec\..*-spec"
  {:default #{::sel/none :focused}
   :available #{:focused :should-fail}})
