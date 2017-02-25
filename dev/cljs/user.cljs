(ns cljs.user
  (:require
    [clojure.spec.test :as st]
    [untangled-spec.tests-to-run]
    [untangled-spec.suite :as ts]
    [untangled-spec.selectors :as sel]))

(enable-console-print!)

(st/instrument)

(ts/def-test-suite on-load #"untangled-spec\..*-spec"
  {:default #{::sel/none :focused}
   :available #{:focused :should-fail}})
