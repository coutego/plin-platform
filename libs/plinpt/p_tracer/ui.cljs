(ns plinpt.p-tracer.ui
  (:require [reagent.core :as r]
            [plinpt.p-ui-components.core :as ui]
            [plinpt.p-tracer.core :as core]))

(defn trace-item [t]
  (let [expanded? (r/atom false)]
    (fn [t] ;; Form-2: Inner function accepts args
      (let [duration (if (:end t)
                       (- (.getTime (:end t)) (.getTime (:start t)))
                       "...")]
        [:div.border-b.border-gray-200.last:border-0
         [:div.flex.items-center.justify-between.p-3.hover:bg-gray-50.cursor-pointer
          {:on-click #(swap! expanded? not)}
          [:div.flex.items-center.gap-3
           [:span.font-mono.text-xs.text-gray-500 (-> (:start t) .toTimeString (subs 0 8))]
           [:span.font-bold.text-blue-600 (:service t)]
           (when (seq (:sqls t))
             [:span.text-xs.bg-blue-100.text-blue-800.px-2.rounded (count (:sqls t)) " SQL"])]
          [:div.text-xs.text-gray-500 (str duration "ms")]]
         
         (when @expanded?
           [:div.bg-gray-50.p-3.text-sm.space-y-2
            [:div
             [:div.text-xs.font-bold.text-gray-500.uppercase "Arguments"]
             [:pre.text-xs.overflow-auto.bg-gray-100.p-2.rounded.mt-1 (str (:args t))]]
            
            (when (seq (:sqls t))
              [:div
               [:div.text-xs.font-bold.text-gray-500.uppercase.mb-1 "SQL Execution"]
               [:div.space-y-2
                (for [[idx sql-entry] (map-indexed vector (:sqls t))]
                  ^{:key idx}
                  [:div.bg-white.border.p-2.rounded.shadow-sm
                   [:div.font-mono.text-xs.text-green-700 (:sql sql-entry)]
                   [:div.text-xs.text-gray-500.mt-1 (str "Params: " (:params sql-entry))]])]])
            
            [:div
             [:div.text-xs.font-bold.text-gray-500.uppercase "Result"]
             [:pre.text-xs.overflow-auto.bg-gray-100.p-2.rounded.mt-1 
              (if (:result t) (str (:result t)) "Pending/None")]]])]))))

(defn tracer-dashboard []
  (let [{:keys [traces enabled? active-endpoints]} @core/state
        known-endpoints (into active-endpoints (map :service traces))]
    [:div.h-full.flex.flex-col
     [ui/page-header "Service Tracer" "Inspect service calls and SQL execution"
      [:div.flex.gap-2
       [:button.px-3.py-1.rounded.text-sm.font-medium
        {:class (if enabled? "bg-green-100 text-green-800" "bg-red-100 text-red-800")
         :on-click #(swap! core/state update :enabled? not)}
        (if enabled? "Recording On" "Recording Paused")]
       [:button.px-3.py-1.bg-gray-200.rounded.text-sm.hover:bg-gray-300
        {:on-click core/clear-traces!} "Clear"]]]
     
     [:div.flex.flex-1.overflow-hidden
      [:div.w-64.bg-white.border-r.p-4.overflow-y-auto
       [:h3.text-xs.font-bold.text-gray-500.uppercase.mb-3 "Filter Endpoints"]
       (if (empty? known-endpoints)
         [:div.text-sm.text-gray-400.italic "No endpoints seen yet."]
         [:div.space-y-1
          (for [ep (sort known-endpoints)]
            ^{:key ep}
            [:label.flex.items-center.space-x-2.cursor-pointer
             [:input {:type "checkbox"
                      :checked (or (empty? active-endpoints) (contains? active-endpoints ep))
                      :on-change #(core/toggle-endpoint! ep)
                      :class "rounded text-blue-600 focus:ring-blue-500"}]
             [:span.text-sm.truncate {:title ep} ep]])])]
      
      [:div.flex-1.overflow-y-auto.p-4.bg-gray-50
       (if (empty? traces)
         [:div.text-center.text-gray-400.mt-10 "No traces recorded."]
         [:div.bg-white.rounded.shadow.border
          (for [t (reverse traces)]
            ^{:key (:id t)}
            [trace-item t])])]]]))

(defn make-route [ui]
  {:path "/tracer"
   :label "Tracer"
   :component ui})
