(ns untangled-spec.dom.edn-renderer-spec
  (:require
    [untangled-spec.dom.edn-renderer :as ednr]
    [untangled-spec.core :refer [specification behavior assertions]]))

(specification "html-edn"
  (behavior "gh-14 -> no react warnings about not a plain object"
    ))
