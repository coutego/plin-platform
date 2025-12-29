(ns plinpt.p-debug.beans-ui
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plinpt.p-debug.common :as common]))

(defn- bean-detail [k instance definition format-parts]
  (let [expanded? (r/atom false)
        doc (:debug/doc definition)
        source (:debug/source definition)
        api-meta (or (:api definition) 
                     (:api (meta definition))
                     (:api (meta source)))
        is-reagent? (:debug/reagent-component definition)
        is-fn? (fn? instance)
        is-value? (not (or is-reagent? is-fn?))
        
        {:keys [prefix body suffix]} (format-parts k)]
    (fn []
      [:div {:class "border border-gray-200 rounded-lg hover:shadow-md transition-shadow bg-white overflow-hidden"}
       ;; Header (Clickable)
       [:div {:class "p-3 cursor-pointer hover:bg-gray-50 flex items-start gap-2"
              :on-click #(swap! expanded? not)}
        
        ;; Chevron Icon
        [:div {:class (str "mt-1 text-gray-400 transition-transform duration-200 flex-shrink-0 "
                           (if @expanded? "rotate-90" ""))}
         [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]
        
        ;; Type Icon
        [:div {:class (str "mt-1 flex-shrink-0 "
                           (cond
                             is-reagent? "text-purple-500"
                             is-fn?      "text-blue-500"
                             :else       "text-gray-400"))}
         (cond
           is-reagent? [common/icon-component]
           is-fn?      [common/icon-function]
           :else       [common/icon-value])]
        
        [:div {:class "flex-1 min-w-0"}
         ;; Key and Type
         [:div {:class "flex justify-between items-start mb-1"}
          [:div {:class "font-mono text-sm bg-blue-50 px-2 py-1 rounded break-all"
                 :title (str k)} ;; Show full key on hover
           ;; Render parts: prefix (gray), body (bold blue), suffix (gray)
           (when (seq prefix) [:span {:class "text-gray-400"} prefix])
           [:span {:class "text-blue-800 font-bold"} body]
           (when (seq suffix) [:span {:class "text-gray-400"} suffix])]
           
          [:span {:class "text-xs text-gray-400 ml-2 whitespace-nowrap flex-shrink-0"}
           (cond is-reagent? "Reagent Component"
                 is-fn? "Function"
                 :else (type instance))]]
         
         ;; Documentation
         (when doc
           [:div {:class "text-sm text-gray-600 italic"} doc])]]
       
       ;; Expanded Content
       (when @expanded?
         [:div {:class "p-3 border-t border-gray-100 bg-gray-50"}
          
          ;; Definition (Source)
          (when source
            [:div {:class "mb-3"}
             [:div {:class "text-xs font-bold text-gray-500 mb-1"} "Definition:"]
             [:pre {:class "bg-gray-800 text-gray-300 p-2 rounded text-xs overflow-x-auto whitespace-pre-wrap"}
              (pr-str source)]])
          
          ;; Actions / Value
          [:div
           (when is-value?
             [:div
              [:div {:class "text-xs font-bold text-gray-500 mb-1"} "Value:"]
              [common/ui-error-boundary
               [common/render-result instance]]])
           
           ;; Function Caller
           (when (and is-fn? (not is-reagent?))
             [common/function-caller instance api-meta])
           
           ;; Component Renderer
           (when is-reagent?
             [common/component-renderer instance api-meta])]])])))

(defn main-view [container all-plugins]
  (let [filter-text (r/atom "")]
    (fn [container all-plugins]
      (let [definitions (get container :plin.core/definitions {})
            ft @filter-text
            filtered-container (if (empty? ft)
                                 container
                                 (filter (fn [[k _]] (str/includes? (str k) ft)) container))
            ;; Calculate formatter based on all container keys
            format-parts (common/make-id-parts-formatter (keys container))]
        [:div {:class "flex flex-col h-full bg-white"}
         [:div {:class "p-4 border-b border-gray-200 bg-gray-50 flex justify-between items-center"}
          [:div
           [:h3 {:class "font-bold text-lg"} "Container Beans"]
           [:div {:class "text-xs text-gray-500"} (str (count container) " beans loaded")]]
          
          [:input {:class "border rounded px-3 py-1 text-sm w-64"
                   :placeholder "Filter beans..."
                   :value @filter-text
                   :on-change #(reset! filter-text (-> % .-target .-value))}]]
         
         [:div {:class "flex-1 overflow-y-auto p-4"}
          [:div {:class "grid grid-cols-1 gap-4"}
           (if (empty? filtered-container)
             [:div {:class "text-gray-500 italic text-center py-10"} "No beans found matching filter."]
             (for [[k v] (sort-by first filtered-container)]
               ^{:key k}
               [bean-detail k v (get definitions k) format-parts]))]]]))))
