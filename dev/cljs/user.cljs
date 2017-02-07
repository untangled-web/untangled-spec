(ns cljs.user
  (:require
    [clojure.core.async :as a]
    [untangled-spec.suite :as suite]
    [untangled-spec.tests-to-run]))

(enable-console-print!)

(suite/def-test-suite untangled-spec #"untangled-spec.*-spec"
  {:default (complement :integration)
   :integration :integration
   :focused :focused})

(def on-load untangled-spec)
