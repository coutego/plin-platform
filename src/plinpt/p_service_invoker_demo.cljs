(ns plinpt.p-service-invoker-demo
  (:require [plin.core :as plin]
            [plinpt.i-service-invoker :as invoker]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-tracer :as i-tracer]
            [plinpt.i-db :as idb]
            [plinpt.p-service-invoker-demo.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Demo implementation of the Service Invoker with AlaSQL."
    :deps [invoker/plugin i-tracer/plugin idev/plugin idb/plugin]

    :contributions
    {::idev/plugins [{:id :p-service-invoker-demo
                      :description "Demo Service Invoker."
                      :responsibilities "Executes handlers locally against AlaSQL."
                      :type :infrastructure}]}

    :beans
    {::invoker/db-executor
     ^{:doc "AlaSQL DB Executor (Traced)."
       :api {:args [["sql" "SELECT 1" :string] ["params" [] :vector]] :ret "Promise<vector|map>"}}
     [core/make-wrapped-executor ::i-tracer/api ::idb/executor]

     ::reset-db
     ^{:doc "Function to reset the database to its initial state."
       :api {:args [] :ret :any}}
     [core/make-robust-reset ::idb/reset]

     ::invoker/invoke
     ^{:doc "Demo implementation of the service invoker."
       :api {:args [["endpoint" :auth/list :keyword] ["payload" {} :map]] :ret "Promise<any>"}}
     [core/make-wrapped-invoker ::i-tracer/api ::invoker/handlers ::invoker/db-executor]}}))
