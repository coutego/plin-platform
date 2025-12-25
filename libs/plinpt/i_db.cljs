(ns plinpt.i-db
  (:require [plin.core :as plin]))

(defn- process-init [db values]
  (assoc db ::init-defs (vec (flatten values))))

(def plugin
  (plin/plugin
   {:doc "Interface for Database interactions."
    
    :extensions
    [{:key ::init
      :doc "Contributes database initialization logic (schema and data).
            Expected format: {:id :keyword :schema [\"SQL\"...] :data [\"SQL\"...] :deps [:id...]}"
      :handler process-init}]

    :beans
    {::executor
     ^{:doc "Function to execute SQL: (fn [sql params] -> Promise)"
       :api {:args [["sql" "SELECT 1" :string] ["params" [] :vector]] :ret "Promise"}}
     [:= (fn [_ _] (js/Promise.reject "No DB implementation found"))]

     ::reset
     ^{:doc "Function to reset the database."
       :api {:args [] :ret :any}}
     [:= (fn [] (js/console.warn "No DB reset implementation found"))]}}))
