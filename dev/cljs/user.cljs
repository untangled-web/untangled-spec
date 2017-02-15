(ns cljs.user
  (:require
    [untangled-spec.tests-to-run]
    [untangled-spec.suite :as ts]))

(enable-console-print!)

(ts/def-test-suite on-load #"untangled-spec\..*-spec"
  {:default (complement :integration)
   :integration :integration
   :focused :focused})
