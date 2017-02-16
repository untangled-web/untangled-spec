(ns cljs.user
  (:require
    [untangled-spec.tests-to-run]
    [untangled-spec.suite :as ts]
    [untangled-spec.selectors :as sel]))

(enable-console-print!)

(ts/def-test-suite on-load #"untangled-spec\..*-spec"
  {:default #{::sel/none :focused}
   :available #{:focused :unit :integration}})
