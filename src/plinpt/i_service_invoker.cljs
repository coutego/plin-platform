(ns plinpt.i-service-invoker
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface for the Service Invoker, providing a unified way to call services (local or remote)."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-service-invoker
                      :description "Service Invoker Interface."
                      :responsibilities "Defines the invoke bean and handler registry."
                      :type :infrastructure}]}

    :extensions
    [{:key ::handlers
      :doc "Registry of service handlers. Format: [{:endpoint :keyword :handler fn}]"
      :handler (plin/collect-all ::handlers)}]

    :beans
    {::invoke
     ^{:doc "Function to invoke a service endpoint. Returns Promise."
       :api {:args [["endpoint" :auth/list :keyword] ["payload" {} :map]] :ret "Promise"}}
     [:= (fn [endpoint payload]
           (js/Promise.reject (ex-info "No invoker implementation loaded" {:endpoint endpoint})))]

     ::db-executor
     ^{:doc "Function to execute DB commands. Returns Promise."
       :api {:args [["sql" "SELECT 1" :string] ["params" [] :vector]] :ret "Promise"}}
     [:= (fn [sql params]
           (js/Promise.reject (ex-info "No DB executor loaded" {})))]

     ::reset-db
     ^{:doc "Function to reset the database to its initial state. Returns Promise."
       :api {:args [] :ret "Promise"}}
     [:= (fn []
           (js/Promise.reject (ex-info "No reset-db implementation loaded" {})))]

     ::handlers
     ^{:doc "Collected map of endpoints to handler functions."
       :api {:ret :vector}}
     [:= []]}}))
