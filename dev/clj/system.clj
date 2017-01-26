(ns clj.system
  (:require
    [clojure.tools.namespace.repl :as tools-ns-repl]))

(tools-ns-repl/disable-reload!)

(defonce system (atom nil))
