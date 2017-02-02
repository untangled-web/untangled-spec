(ns untangled-spec.assertions-spec
  (:require [untangled-spec.core :refer-macros [specification assertions]]))

(specification "clojureSCRIPT tests" :focused
  (assertions
    :cljs => 9999999
    ))

(specification "assertions blocks work on cljs"
  (assertions
    "throws arrow can catch"
    (assert false "foobar") =throws=> (js/Error #"ooba")
    "throws arrow can catch js/Objects"
    (throw #js {}) =throws=> (js/Object)))
