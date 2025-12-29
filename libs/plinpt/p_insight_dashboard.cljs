(ns plinpt.p-insight-dashboard
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-application :as iapp]
            [plinpt.i-app-shell :as iapp-shell]
            [plinpt.p-insight-shell.core :as shell]))

;; --- Chart Initialization ---
(defn init-budget-chart! []
  (when (and js/window.ApexCharts (.getElementById js/document "budgetChart"))
    (let [options (clj->js {:series [{:name "Budget"
                                      :data [10.2 10.4 10.35 10.6 10.75 10.7 10.82 10.9 11.02 11.1 11.18 11.26]}]
                            :chart {:type "area"
                                    :height 130
                                    :toolbar {:show false}
                                    :fontFamily "Inter, sans-serif"
                                    :zoom {:enabled false}}
                            :colors ["#0057B8"]
                            :fill {:type "gradient"
                                   :gradient {:shadeIntensity 1
                                              :opacityFrom 0.3
                                              :opacityTo 0.05
                                              :stops [0 90 100]}}
                            :dataLabels {:enabled false}
                            :stroke {:curve "smooth" :width 2}
                            :xaxis {:categories ["J" "F" "M" "A" "M" "J" "J" "A" "S" "O" "N" "D"]
                                    :axisBorder {:show false}
                                    :axisTicks {:show false}
                                    :labels {:style {:colors "#94a3b8" :fontSize "10px"}}}
                            :yaxis {:show false}
                            :grid {:show true
                                   :borderColor "#f1f5f9"
                                   :padding {:top 0 :right 0 :bottom 0 :left -10}}
                            :tooltip {:theme "light" :x {:show false}}})]
      (-> (js/ApexCharts. (.getElementById js/document "budgetChart") options)
          (.render)))))

(defn init-arch-chart! []
  (when (and js/window.ApexCharts (.getElementById js/document "archChart"))
    (let [options (clj->js {:series [71 29 0]
                            :labels ["Build" "Reuse" "Buy"]
                            :chart {:type "donut"
                                    :height 160
                                    :fontFamily "Inter, sans-serif"}
                            :plotOptions {:pie {:donut {:size "65%"
                                                        :labels {:show false}}}}
                            :colors ["#0f172a" "#0057B8" "#94a3b8"]
                            :dataLabels {:enabled false}
                            :legend {:position "bottom"
                                     :fontSize "11px"
                                     :markers {:radius 12}
                                     :itemMargin {:horizontal 5 :vertical 0}}
                            :stroke {:show false}})]
      (-> (js/ApexCharts. (.getElementById js/document "archChart") options)
          (.render)))))

(defn init-code-chart! []
  (when (and js/window.ApexCharts (.getElementById js/document "codeChart"))
    (let [options (clj->js {:series [{:data [540 88 44 24 4]}]
                            :chart {:type "bar"
                                    :height 80
                                    :toolbar {:show false}
                                    :fontFamily "Inter, sans-serif"
                                    :sparkline {:enabled true}}
                            :plotOptions {:bar {:borderRadius 4
                                                :horizontal true
                                                :barHeight "60%"
                                                :distributed true}}
                            :colors ["#0f172a" "#334155" "#475569" "#64748b" "#94a3b8"]
                            :dataLabels {:enabled false}
                            :tooltip {:theme "light"
                                      :y {:formatter (fn [val] (str val "k lines"))}}
                            :xaxis {:categories ["Java" "SQL" "JSON" "YAML" "Docker"]}})]
      (-> (js/ApexCharts. (.getElementById js/document "codeChart") options)
          (.render)))))

;; --- Dashboard Components ---
(defn kpi-card [title value subtitle icon-bg-class icon]
  [:div {:class "bg-white rounded-2xl p-4 border border-slate-200/60 shadow-sm flex flex-col justify-between"}
   [:div {:class "flex justify-between items-start mb-2"}
    [:span {:class "text-xs font-semibold text-slate-500 uppercase tracking-wide"} title]
    [:span {:class (str "p-1.5 rounded-lg " icon-bg-class)}
     icon]]
   [:div
    [:div {:class "text-2xl font-bold text-slate-900"} value]
    [:div {:class "text-xs text-slate-500 mt-1"} subtitle]]])

(defn is-summary-widget []
  [:div {:class "bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden"}
   ;; Header
   [:div {:class "px-5 py-4 border-b border-slate-100 flex justify-between items-start"}
    [:div {:class "flex items-start gap-3"}
     [:div {:class "h-10 w-10 rounded-xl bg-gradient-to-br from-ec-blue to-ec-blue2 flex items-center justify-center text-white shadow-md shadow-blue-900/10"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"}]]]
     [:div
      [:h3 {:class "text-base font-bold text-slate-900"} "IS-2021 OFIS — OFIS"]
      [:p {:class "text-xs text-slate-500"} "Organic Farming Information System"]]]
    [:div {:class "flex gap-2"}
     [:span {:class "px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-100 text-xs font-medium flex items-center gap-1"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-3 w-3" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M5 13l4 4L19 7"}]]
      " Operational"]]]
   
   ;; Content Area
   [:div {:class "p-5"}
    ;; Badges Row (Plugin Injection Point)
    [:div {:class "flex flex-wrap gap-2 mb-6"}
     [:span {:class "px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 border border-slate-200 text-xs font-medium"} "Owner: AGRI"]
     ;; JASSPER Plugin Badge
     [:span {:class "px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-100 text-xs font-medium flex items-center gap-1" :title "Injected by JASSPER Plugin"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-3 w-3" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"}]]
      "Hosting: Baseline"]
     ;; GOVIS Plugin Badge
     [:span {:class "px-2.5 py-1 rounded-full bg-blue-50 text-blue-700 border border-blue-100 text-xs font-medium flex items-center gap-1" :title "Injected by GOVIS Plugin"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-3 w-3" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]
      "PIR/PC OK"]
     ;; Code Insights Plugin Badge
     [:span {:class "px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 border border-slate-200 text-xs font-medium flex items-center gap-1" :title "Injected by Code Insights Plugin"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-3 w-3" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]]
      "37 Repos"]]
    
    [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
     ;; Standard Fields
     [:div {:class "bg-slate-50 rounded-2xl p-4 border border-slate-200/60"}
      [:h4 {:class "text-xs font-bold text-slate-400 uppercase tracking-wider mb-3"} "Key Roles"]
      [:div {:class "space-y-3"}
       [:div {:class "flex justify-between items-center text-sm"}
        [:div {:class "flex items-center gap-2 text-slate-500"}
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"}]]
         [:span "System Owner"]]
        [:span {:class "font-medium text-slate-900"} "John DOE"]]
       [:div {:class "flex justify-between items-center text-sm"}
        [:div {:class "flex items-center gap-2 text-slate-500"}
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"}]]
         [:span "Business Mgr"]]
        [:span {:class "font-medium text-slate-900"} "Ulrich LAMME"]]]]
     
     ;; Injected Component: Code Health
     [:div {:class "rounded-2xl border border-slate-200 bg-gradient-to-br from-white to-slate-50 p-4 relative overflow-hidden group"}
      [:div {:class "absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity"}
       [:span {:class "text-[9px] bg-white border border-slate-200 px-1 py-0.5 rounded text-slate-400"} "Plugin: Code"]]
      [:div {:class "flex justify-between items-center mb-2"}
       [:h4 {:class "text-xs font-semibold text-slate-700"} "Code Health"]
       [:span {:class "px-2 py-0.5 bg-amber-50 text-amber-700 text-[10px] font-bold rounded-full border border-amber-100 flex items-center gap-1"}
        [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-3 w-3" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"}]]
        " Hotspots"]]
      [:div {:id "codeChart" :class "h-20 w-full"}]]]]])

(defn portfolio-table []
  [:div {:class "bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden"}
   [:div {:class "px-5 py-4 border-b border-slate-100 flex justify-between items-center"}
    [:h3 {:class "font-bold text-slate-800 text-sm"} "Portfolio List"]
    [:button {:class "text-xs font-medium text-ec-blue hover:text-ec-blue2"} "View All →"]]
   [:div {:class "overflow-x-auto"}
    [:table {:class "w-full text-left text-xs"}
     [:thead {:class "bg-slate-50 text-slate-500 font-semibold border-b border-slate-200"}
      [:tr
       [:th {:class "px-5 py-3"} "System"]
       [:th {:class "px-5 py-3"} "Status"]
       [:th {:class "px-5 py-3"} "Next Milestone"]
       [:th {:class "px-5 py-3 text-right"} "Action"]]]
     [:tbody {:class "divide-y divide-slate-100"}
      [:tr {:class "hover:bg-slate-50/80 transition-colors"}
       [:td {:class "px-5 py-3"}
        [:div {:class "font-semibold text-slate-900"} "IS-1529 AGREX"]
        [:div {:class "text-[11px] text-slate-500"} "Agri Budget Mgmt"]]
       [:td {:class "px-5 py-3"}
        [:span {:class "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-600/20"} "Operational"]]
       [:td {:class "px-5 py-3 text-slate-600"}
        "TCO Update "
        [:span {:class "text-slate-400 block text-[10px]"} "27/12/2025"]]
       [:td {:class "px-5 py-3 text-right"}
        [:button {:class "text-slate-400 hover:text-ec-blue transition-colors"}
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]]
      [:tr {:class "hover:bg-slate-50/80 transition-colors bg-red-50/30"}
       [:td {:class "px-5 py-3"}
        [:div {:class "font-semibold text-slate-900"} "IS-2021 OFIS"]
        [:div {:class "text-[11px] text-slate-500"} "Organic Farming"]]
       [:td {:class "px-5 py-3"}
        [:span {:class "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium bg-red-50 text-red-700 ring-1 ring-inset ring-red-600/10"} "Critical"]]
       [:td {:class "px-5 py-3 text-red-700 font-medium"}
        "MEP Prod "
        [:span {:class "text-red-500 block text-[10px]"} "Overdue"]]
       [:td {:class "px-5 py-3 text-right"}
        [:button {:class "text-slate-400 hover:text-ec-blue transition-colors"}
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]]
      [:tr {:class "hover:bg-slate-50/80 transition-colors"}
       [:td {:class "px-5 py-3"}
        [:div {:class "font-semibold text-slate-900"} "IS-1998 CAP-SP"]
        [:div {:class "text-[11px] text-slate-500"} "CAP Portal"]]
       [:td {:class "px-5 py-3"}
        [:span {:class "inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium bg-amber-50 text-amber-700 ring-1 ring-inset ring-amber-600/20"} "Review"]]
       [:td {:class "px-5 py-3 text-slate-600"}
        "Security Plan "
        [:span {:class "text-slate-400 block text-[10px]"} "15/01/2026"]]
       [:td {:class "px-5 py-3 text-right"}
        [:button {:class "text-slate-400 hover:text-ec-blue transition-colors"}
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]]]]]])

(defn financials-card []
  [:div {:class "bg-white rounded-2xl border border-slate-200 shadow-sm p-5"}
   [:div {:class "flex items-center justify-between mb-4"}
    [:div {:class "flex items-center gap-2"}
     [:div {:class "h-8 w-8 rounded-lg bg-slate-900 text-white flex items-center justify-center"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"}]]]
     [:div
      [:div {:class "text-sm font-semibold text-slate-900"} "Financials"]
      [:div {:class "text-[10px] text-slate-500"} "Trend & Measures"]]]
    [:span {:class "px-2 py-0.5 rounded text-[10px] font-medium bg-amber-50 text-amber-700"} "Mock"]]
   [:div {:id "budgetChart" :class "h-32 w-full mb-3"}]
   [:div {:class "space-y-2"}
    [:div {:class "flex justify-between items-center p-2 rounded-xl bg-slate-50"}
     [:span {:class "text-[11px] text-slate-500"} "Planned (2025)"]
     [:span {:class "text-xs font-bold text-slate-900"} "€ 25,895,444"]]
    [:div {:class "flex justify-between items-center p-2 rounded-xl bg-slate-50"}
     [:span {:class "text-[11px] text-slate-500"} "Current Year"]
     [:span {:class "text-xs font-bold text-slate-900"} "€ 2,483,112"]]]])

(defn architecture-card []
  [:div {:class "bg-white rounded-2xl border border-slate-200 shadow-sm p-5"}
   [:div {:class "flex items-center gap-2 mb-4"}
    [:div {:class "h-8 w-8 rounded-lg bg-slate-900 text-white flex items-center justify-center"}
     [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
      [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"}]]]
    [:div
     [:div {:class "text-sm font-semibold text-slate-900"} "Architecture"]
     [:div {:class "text-[10px] text-slate-500"} "Reuse vs Buy vs Build"]]]
   [:div {:id "archChart" :class "h-40 flex justify-center"}]
   [:div {:class "mt-4 text-[11px] text-slate-500 bg-slate-50 p-3 rounded-xl border border-slate-100"}
    "Build-heavy profile (71%). Consider highlighting reusable building blocks."]])

(defn action-center []
  [:div {:class "bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden"}
   [:div {:class "px-5 py-4 border-b border-slate-100 flex items-center justify-between"}
    [:div {:class "flex items-center gap-2"}
     [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4 text-slate-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
      [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M13 10V3L4 14h7v7l9-11h-7z"}]]
     [:h3 {:class "font-bold text-slate-800 text-sm"} "Action Center"]]]
   [:div {:class "p-4 space-y-2"}
    [:button {:class "w-full text-left p-3 rounded-2xl border border-slate-200 bg-white hover:bg-slate-50 transition-colors group flex items-start gap-3"}
     [:div {:class "h-8 w-8 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]]
     [:div
      [:div {:class "text-xs font-bold text-slate-900 group-hover:text-ec-blue"} "Upload Document"]
      [:div {:class "text-[10px] text-slate-500 mt-0.5"} "Attach PIR/PC gov docs"]]]
    [:button {:class "w-full text-left p-3 rounded-2xl border border-slate-200 bg-white hover:bg-slate-50 transition-colors group flex items-start gap-3"}
     [:div {:class "h-8 w-8 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0"}
      [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 19l9 2-9-18-9 18 9-2zm0 0v-8"}]]]
     [:div
      [:div {:class "text-xs font-bold text-slate-900 group-hover:text-ec-blue"} "Start MEP"]
      [:div {:class "text-[10px] text-slate-500 mt-0.5"} "Deployment workflow"]]]]])

(defn dashboard-page []
  (r/create-class
   {:component-did-mount
    (fn [_]
      ;; Initialize charts after component mounts
      (js/setTimeout
       (fn []
         (init-budget-chart!)
         (init-arch-chart!)
         (init-code-chart!))
       100))
    
    :reagent-render
    (fn []
      [:div
       ;; Page Title Area
       [:div {:class "mb-8 flex flex-col md:flex-row md:items-end justify-between gap-4"}
        [:div
         [:h2 {:class "text-2xl font-bold text-slate-900 tracking-tight"} "AGRI IT Portfolio"]
         [:p {:class "text-sm text-slate-500 mt-1"} "High-level view of all managed Information Systems and assets."]]
        [:div {:class "flex items-center gap-2"}
         [:button {:class "inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors shadow-sm"}
          [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4 text-slate-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]
          "Export Report"]]]

       ;; KPI Cards
       [:div {:class "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8"}
        [kpi-card "Systems" "42" [:span [:span {:class "text-emerald-600 font-medium"} "+2"] " this quarter"]
         "bg-blue-50 text-ec-blue" [shell/icon-dashboard]]
        [kpi-card "Operational" "36" [:span [:span {:class "text-slate-700 font-medium"} "6"] " under change"]
         "bg-emerald-50 text-emerald-600" 
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"}]]]
        [kpi-card "ITMP Items" "128" [:span [:span {:class "text-amber-600 font-medium"} "9"] " due in 30 days"]
         "bg-amber-50 text-amber-600"
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"}]]]
        [kpi-card "Security" "Valid" "Next reviews 2027"
         "bg-blue-50 text-blue-600"
         [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"}]]]]

       ;; Main Content Grid
       [:div {:class "grid grid-cols-1 xl:grid-cols-3 gap-6"}
        ;; Left Column (2/3)
        [:div {:class "xl:col-span-2 space-y-6"}
         [is-summary-widget]
         [portfolio-table]]
        
        ;; Right Column (1/3)
        [:div {:class "space-y-6"}
         [financials-card]
         [architecture-card]
         [action-center]]]])}))

(def plugin
  (plin/plugin
   {:doc "INSIGHT Dashboard content plugin providing sample dashboard page and navigation."
    :deps [iapp/plugin iapp-shell/plugin]
    
    :contributions
    {::iapp/nav-items 
     [;; Root Items
      {:id :dashboard :label "Dashboard" :route "dashboard" :order 1
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"}]]}
      
      ;; AGRI Systems
      {:id :mep :label "MEP" :route "/agri/mep" :section "AGRI Systems" :order 10
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]}
      {:id :itmp :label "ITMP Planning" :route "/agri/itmp" :section "AGRI Systems" :order 11
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"}]]}

      ;; EC Systems
      {:id :jassper :label "JASSPER" :route "/ec/jassper" :section "EC Systems" :order 20
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M3.055 11H5a2 2 0 012 2v1a2 2 0 002 2 2 2 0 012 2v2.945M8 3.935V5.5A2.5 2.5 0 0010.5 8h.5a2 2 0 012 2 2 2 0 104 0 2 2 0 012-2h1.064M15 20.488V18a2 2 0 012-2h3.064M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]]}
      {:id :ares :label "ARES" :route "/ec/ares" :section "EC Systems" :order 21
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]}

      ;; Admin
      {:id :auth :label "Authorisation" :route "/admin/auth" :section "Admin" :order 30
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"}]]}

      ;; Development (Drill-down)
      {:id :dev-tools :label "Development Tools" :route "/dev/tools" :section "Development" :order 40
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z"}]]}
      
      {:id :tracer :parent-id :dev-tools :label "Service Tracer" :route "tracer" :order 1
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M13 10V3L4 14h7v7l9-11h-7z"}]]}
      {:id :loader :parent-id :dev-tools :label "Dynamic Loader" :route "loader" :order 2
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"}]]}
      
      {:id :db-mgmt :parent-id :dev-tools :label "DB Management" :route "db" :order 3
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"}]]}
      
      {:id :db-admin :parent-id :db-mgmt :label "DB Administration" :route "admin" :order 1}
      {:id :db-data :parent-id :db-mgmt :label "Data Management" :route "data" :order 2}
      {:id :db-schema :parent-id :db-mgmt :label "Schema Visualization" :route "schema" :order 3}

      {:id :sys-debug :label "System Debugger" :route "debugger" :section "Development" :order 41
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]]}
      
      {:id :debug-manage :parent-id :sys-debug :label "Manage" :route "manage" :section "Plugins" :order 1}
      {:id :debug-dep :parent-id :sys-debug :label "Dependency Graph" :route "deps" :section "Plugins" :order 2}
      {:id :debug-config :parent-id :sys-debug :label "Configuration" :route "config" :section "Plugins" :order 3}
      
      {:id :bean-inspector :parent-id :sys-debug :label "Inspector" :route "inspector" :section "Container (Beans)" :order 10}
      {:id :bean-dep :parent-id :sys-debug :label "Dependency Graph" :route "bean-deps" :section "Container (Beans)" :order 11}
      {:id :bean-config :parent-id :sys-debug :label "Configuration" :route "bean-config" :section "Container (Beans)" :order 12}

      ;; Tools
      {:id :excel :label "Excel" :route "/tools/excel" :section "Tools" :order 50
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]}
      
      ;; Settings
      {:id :settings :label "Settings" :route "/settings" :section "Settings" :order 60
       :icon [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 text-slate-400 group-hover:text-slate-600 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"}]
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"}]]}]
     
     ::iapp/routes [{:path "/dashboard" :component dashboard-page :label "Dashboard"}
                    {:path "/" :component dashboard-page :label "Home"}]}
    
    :beans {}}))
