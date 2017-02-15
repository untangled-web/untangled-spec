(ns untangled-spec.stub-spec
  (:require
    [clojure.spec :as s]
    [untangled-spec.stub :as stub
     #?@(:cljs [:include-macros true])]
    [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
     [specification behavior provided assertions]]
    #?(:clj [clojure.test :refer [is]])
    #?(:cljs [cljs.test :refer-macros [is]]))
  #?(:clj
     (:import clojure.lang.ExceptionInfo)))

(defn make-simple-script []
  (stub/make-script "something"
    [(stub/make-step 'stub 1 [])]))

(specification "increment-script-call-count"
  (behavior "finds and increments the correct step"
    (let [script (make-simple-script)]

      (stub/increment-script-call-count script 0)

      (is (= 1 (get-in @script [:steps 0 :times]))))))

(specification "step-complete"
  (let [script (make-simple-script)]
    (behavior "is false when call count is less than expected count"
      (is (not (stub/step-complete script 0))))
    (stub/increment-script-call-count script 0)
    (behavior "is true when call count reaches expected count"
      (is (stub/step-complete script 0)))))

(defn make-call-script [to-call & {:keys [literals N]
                                   :or {N 1, literals []}}]
  (stub/scripted-stub
    (stub/make-script "something"
      [(stub/make-step to-call N literals)])))

(specification "scripted-stub"
  (behavior "calls the stub function"
    (let [detector (atom false)
          sstub (make-call-script (fn [] (reset! detector true)))]
      (sstub), (is (= true @detector))))

  (behavior "verifies the stub fn is called with the correct literals"
    (let [sstub (make-call-script
                   (fn [n x] [(inc n) x])
                   :literals [41 ::stub/any]
                   :N :many)]
      (assertions
        (sstub 41 :foo) => [42 :foo]
        (sstub 2 :whatever) =throws=> (ExceptionInfo #"called with wrong arguments")
        (try (sstub 2 :evil)
          (catch ExceptionInfo e (ex-data e)))
        => {:args [2 :evil]
            :expected-literals [41 ::stub/any]})))

  (behavior "returns whatever the stub function returns"
    (let [sstub (make-call-script (fn [] 42))]
      (assertions (sstub) => 42)))

  (behavior "throws an exception if the function is invoked more than programmed"
    (let [sstub (make-call-script (fn [] 42))]
      (sstub) ; first call
      (assertions
        (try (sstub 1 2 3) (catch ExceptionInfo e (ex-data e)))
        => {:max-calls 1
            :args '(1 2 3)})))

  (behavior "throws whatever exception the function throws"
    (let [sstub (make-call-script (fn [] (throw (ex-info "BUMMER" {}))))]
      (assertions
        (sstub) =throws=> (ExceptionInfo))))

  (behavior "only moves to the next script step if the call count for the current step reaches the programmed amount"
    (let [a-count (atom 0)
          b-count (atom 0)
          script (stub/make-script "something"
                   [(stub/make-step (fn [] (swap! a-count inc)) 2 [])
                    (stub/make-step (fn [] (swap! b-count inc)) 1 nil)])
          sstub (stub/scripted-stub script)]
      (assertions
        (repeatedly 3 (fn [] (sstub) [@a-count @b-count]))
        => [[1 0] [2 0] [2 1]])))

  (behavior "records the call argument history"
    (let [script (stub/make-script "something"
                   [(stub/make-step (fn [& args] args) 2 nil)
                    (stub/make-step (fn [& args] args) 1 nil)])
          sstub (stub/scripted-stub script)]
      (sstub 1 2) (sstub 3 4), (sstub :a :b)
      (assertions
        (:history @script) => [[1 2] [3 4] [:a :b]]
        (map :history (:steps @script)) => [[[1 2] [3 4]] [[:a :b]]])))

  (behavior "validates arguments using clojure.spec fdef if found"
    (s/fdef under-test
      :args (s/cat :x number?)
      :ret keyword?
      :fn #(= (str (:x (:args %))) (name (:ret %))))
    (defn under-test [x] x)
    (s/fdef no-args-fn
      :ret keyword?
      :fn #(throw (ex-info "SHOULD NOT HAPPEN" %)))
    (defn no-args-fn [x] x)
    (s/fdef no-ret-fn
      :args (s/cat :x number?)
      :fn #(throw (ex-info "SHOULD NOT HAPPEN" %)))
    (defn no-ret-fn [x] x)
    (let [sstub (fn [ret & [?var]]
                  (stub/scripted-stub
                    (stub/make-script (or ?var (var under-test))
                      [(stub/make-step (constantly ret) 1 nil)])))]
      (assertions
        "validates :args if it exists"
        ((sstub :w/e) "x") =throws=> (ExceptionInfo #"predicate: number\?")
        "validates :ret if it exists"
        ((sstub "not kw") 1) =throws=> (ExceptionInfo #"predicate: keyword\?")
        "validates :fn if it AND :args & :ret exist"
        ((sstub :not-1) 1) =throws=> (ExceptionInfo #"predicate:.*name.*ret")
        ((sstub :not-1 (var no-args-fn)) "1") => :not-1
        ((sstub :not-1 (var no-ret-fn)) 1) => :not-1))))

(specification "validate-target-function-counts"
  (behavior "returns nil if a target function has been called enough times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 5 :times 5}]})]]
      (assertions
        (stub/validate-target-function-counts script-atoms)
        =fn=> some?)))
  (behavior "throws an exception when a target function has not been called enough times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times 5}]})]]
      (assertions
        (stub/validate-target-function-counts script-atoms)
        =throws=> (ExceptionInfo))))

  (behavior "returns nil if a target function has been called enough times with :many specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times :many}]})]]
      (assertions
        (stub/validate-target-function-counts script-atoms)
        =fn=> some?)))

  (behavior "throws an exception if a function has not been called at all with :many was specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times :many}]})]]
      (assertions
        (stub/validate-target-function-counts script-atoms)
        =throws=> (ExceptionInfo))))

  (behavior "returns nil all the function have been called the specified number of times"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 1 :times 1}]})]]
      (assertions
        (stub/validate-target-function-counts script-atoms)
        =fn=> some?)))

  (behavior "throws an exception if the second function has not been called at all with :many was specified"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 0 :times 1}]})]]
      (assertions
        (stub/validate-target-function-counts script-atoms)
        =throws=> (ExceptionInfo))))

  (behavior "stubs record history, will show the script when it fails to validate"
    (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                        (atom {:function "fun2" :steps [{:ncalled 1 :times 2}]})]]
      (assertions
        (stub/validate-target-function-counts script-atoms)
        =throws=> (ExceptionInfo #""
                    #(assertions
                       (ex-data %) => {:function "fun2"
                                       :steps [{:ncalled 1 :times 2}]}))))))
