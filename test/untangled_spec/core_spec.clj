(ns untangled-spec.core-spec
  (:require
    [clojure.test :as t :refer [is]]
    [untangled-spec.contains :refer [*contains?]]
    [untangled-spec.core
     :refer [specification behavior when-mocking assertions]
     :as core]
    [untangled-spec.selectors :as sel]))

(specification "adds methods to clojure.test/assert-expr"
  (assertions
    (methods t/assert-expr)
    =fn=> (*contains? '[= exec throws?] :keys)))
(specification "var-name-from-string"
  (assertions
    "allows the following"
    (core/var-name-from-string "asdfASDF1234!#$%&*|:<>?")
    =fn=> #(not (re-find #"\-" (str %)))
    "converts the rest to dashes"
    (core/var-name-from-string "\\\"@^()[]{};',/  ∂¨∫øƒ∑Ó‡ﬁ€⁄ª•¶§¡˙√ß")
    =fn=> #(re-matches #"__\-+__" (str %))))

(specification "uncaught errors are gracefully handled & reported"
  (assertions
    (let [test-var (specification "ERROR INTENTIONAL" :should-fail
                     (assert false))
          reports (atom [])]
      (binding [t/report #(swap! reports conj %)]
        (with-redefs [sel/selected-for? (constantly true)]
          (test-var)))
      (into []
        (comp (filter (comp #{:error :fail} :type))
          (map #(select-keys % [:type :expected :message :actual])))
         @reports))
    => [{:type :fail
         :expected "IT TO NOT THROW!"
         :message "ERROR INTENTIONAL"
         :actual "java.lang.AssertionError: Assert failed: false"}]))
