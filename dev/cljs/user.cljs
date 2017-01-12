(ns cljs.user
  (:require
    [com.stuartsierra.component :as cp]
    [untangled-spec.runner :as runner]
    [untangled-spec.tests-to-run]))

(enable-console-print!)

(defonce spec-report (cp/start (runner/test-runner)))
(def on-load #(runner/run-tests spec-report))
