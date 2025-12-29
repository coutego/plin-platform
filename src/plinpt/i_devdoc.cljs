(ns plinpt.i-devdoc
  (:require [plin.core :as plin]))

(defn- process-sections [db values]
  (update db :beans assoc ::sections [:= (vec (apply concat values))]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for technical documentation, allowing contributions of documentation sections and defining the main UI."
    :deps []
    
    :contributions
    {::plugins [{:id :i-devdoc
                 :description "Interface for Technical Documentation."
                 :responsibilities "Defines extension points for documentation sections and plugin catalog."
                 :type :infrastructure}]}

    :extensions
    [{:key ::sections
      :handler process-sections
      :doc "List of technical documentation sections. Schema: {:title string :content reagent-component}"}
     {:key ::plugins
      :handler (plin/collect-data ::plugins)
      :doc "List of plugins to document. Schema: {:id keyword :description string :responsibilities string :type :infrastructure|:feature :doc-functional string :doc-technical string}"}]

    :beans
    {::sections
     ^{:doc "List of documentation sections."
       :api {:ret :vector}}
     [:= []]

     ::plugins
     ^{:doc "List of registered plugins for documentation."
       :api {:ret :vector}}
     [:= []]

     ::ui
     ^{:doc "Main documentation page component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= [:div "Documentation"]]}}))
