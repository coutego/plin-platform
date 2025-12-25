(ns plinpt.p-alasql-db
  (:require [plin.core :as plin]
            [plinpt.i-db :as idb]
            [plinpt.p-alasql-db.core :as core]))

(def plugin
  (plin/plugin
   {:doc "AlaSQL Database Implementation."
    :deps [idb/plugin]
    
    ;; We override the handler for ::idb/init to execute our logic
    :extensions [{:key ::idb/init :handler core/process-init}]
    
    :beans
    {::idb/executor
     ^{:doc "AlaSQL Executor"}
     [:= core/executor]
     
     ::idb/reset
     ^{:doc "Resets the AlaSQL DB"}
     [:= core/reset-db!]}}))
