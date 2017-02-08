(ns untangled-spec.reporters.suite
  (:require
    [untangled-spec.suite :as suite]))

(defmacro deftest-all-suite [suite-name regex & [selectors]]
  `(suite/def-test-suite ~suite-name ~regex ~(or selectors {})))
