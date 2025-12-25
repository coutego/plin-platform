(ns plinpt.i-homepage
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(defn- process-guest-content [db values]
  (let [content (last values)
        comp-ref (:component content)]
    (if (keyword? comp-ref)
      ;; If the component is a bean reference (keyword), inject it
      (update db :beans assoc ::guest-content 
              {:constructor [(fn [c] (assoc content :component c)) comp-ref]})
      ;; Otherwise treat it as a literal value
      (update db :beans assoc ::guest-content [:= content]))))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for the home page, allowing contributions of features, actions, metrics, and guest content."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-homepage
                      :description "Interface for the Home Page."
                      :responsibilities "Defines extension points for features, metrics, and actions."
                      :type :infrastructure}]}

    :extensions
    [{:key ::features
      :handler (plin/collect-data ::features)
      :doc "List of features to display on home page. Schema: {:title string :description string :icon component :color-class string :href string :order int :required-perm keyword}"}
     {:key ::planning-action
      :handler (plin/collect-last ::planning-action)
      :doc "Primary action button. Schema: {:label string :href string}"}
     {:key ::reports-action
      :handler (plin/collect-last ::reports-action)
      :doc "Secondary action button. Schema: {:label string :href string}"}
     {:key ::metrics
      :handler (plin/collect-all ::metrics)
      :doc "List of bean keys for metric components to display on dashboard."}
     {:key ::guest-content
      :handler process-guest-content
      :doc "Configuration for the home page when user is not logged in. Schema: {:component component-fn :show-header? boolean}"}]

    :beans
    {::ui
     ^{:doc "Main Home Page Component"
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= [:div "Home Page"]]

     ::features 
     ^{:doc "List of feature definitions."
       :api {:ret :vector}}
     [:= []]
     
     ::planning-action 
     ^{:doc "Primary action definition."
       :api {:ret :map}}
     [:= nil]
     
     ::reports-action 
     ^{:doc "Secondary action definition."
       :api {:ret :map}}
     [:= nil]
     
     ::metrics 
     ^{:doc "List of metric component keys."
       :api {:ret :vector}}
     [:= []]
     
     ::guest-content 
     ^{:doc "Configuration for guest view."
       :api {:ret :map}}
     [:= {:component nil :show-header? true}]}}))
