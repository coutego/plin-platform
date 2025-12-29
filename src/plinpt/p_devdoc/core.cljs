(ns plinpt.p-devdoc.core
  (:require [reagent.core :as r]))

(defn highlight-code [code]
  (let [tokens (re-seq #"(\"[^\"]*\")|(;.*)|(:[\w\-\.\/]+)|(\b\d+\b)|([\[\]\{\}\(\)])|(\s+)|([^\"\s;:\d\[\]\{\}\(\)]+)" code)]
    [:pre {:class "bg-gray-800 text-gray-100 p-3 rounded text-xs overflow-x-auto font-mono"}
     (for [[idx [match str-lit comment kw num bracket space other]] (map-indexed vector tokens)]
       ^{:key idx}
       (cond
         str-lit [:span {:class "text-green-400"} match]
         comment [:span {:class "text-gray-500 italic"} match]
         kw      [:span {:class "text-purple-400"} match]
         num     [:span {:class "text-orange-400"} match]
         bracket [:span {:class "text-yellow-500"} match]
         space   [:span match]
         other   (if (re-matches #"^def|^defn|^let|^if|^when|^cond|^assoc|^update|^->|^apply|^map|^reduce" match)
                   [:span {:class "text-blue-400 font-bold"} match]
                   [:span {:class "text-gray-200"} match])
         :else   [:span match]))]))

(defn configure-marked []
  (when (and js/window.marked js/window.hljs)
    (js/marked.setOptions
     #js {:highlight (fn [code lang]
                       (if (and lang (js/hljs.getLanguage lang))
                         (try
                           (.-value (js/hljs.highlight code #js {:language lang}))
                           (catch :default _
                             code))
                         (try
                           (.-value (js/hljs.highlightAuto code))
                           (catch :default _
                             code))))})))

(defonce _init-marked (configure-marked))

(defn markdown-view [content]
  (if (and js/window.marked content)
    [:div {:class "prose prose-blue max-w-none"
           :dangerouslySetInnerHTML {:__html (js/marked.parse content)}}]
    [:div {:class "whitespace-pre-wrap font-mono text-sm bg-gray-50 p-4 rounded"}
     (or content "No documentation available.")]))

(defn plugin-detail-view [plugin on-back]
  (let [show-tech (r/atom false)]
    (fn [plugin on-back]
      [:div {:class "bg-white rounded-lg shadow-sm border border-gray-200"}
       ;; Header
       [:div {:class "p-6 border-b border-gray-200 flex justify-between items-start"}
        [:div
         [:div {:class "flex items-center gap-3"}
          [:button {:class "text-gray-400 hover:text-gray-600" :on-click on-back}
           [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 19l-7-7m0 0l7-7m-7 7h18"}]]]
          [:h2 {:class "text-2xl font-bold text-gray-900"} (str (:id plugin))]]
         [:p {:class "mt-1 text-gray-500"} (:description plugin)]]
        
        [:span {:class (str "px-3 py-1 rounded-full text-xs font-medium uppercase tracking-wide "
                            (if (= (:type plugin) :infrastructure)
                              "bg-gray-100 text-gray-800"
                              "bg-blue-100 text-blue-800"))}
         (name (:type plugin))]]

       ;; Functional Docs
       [:div {:class "p-6"}
        [markdown-view (:doc-functional plugin)]]

       ;; Technical Docs Toggle
       [:div {:class "border-t border-gray-200"}
        [:button {:class "w-full flex items-center justify-between p-4 bg-gray-50 hover:bg-gray-100 transition-colors text-left"
                  :on-click #(swap! show-tech not)}
         [:span {:class "font-semibold text-gray-700"} "Technical Implementation"]
         [:svg {:class (str "w-5 h-5 text-gray-500 transform transition-transform "
                            (if @show-tech "rotate-180" ""))
                :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19 9l-7 7-7-7"}]]]]

       ;; Technical Docs Content
       (when @show-tech
         [:div {:class "p-6 bg-gray-50 border-t border-gray-200"}
          [markdown-view (:doc-technical plugin)]])])))

(defn- plugin-table [plugins on-select]
  [:div {:class "overflow-x-auto"}
   [:table {:class "min-w-full divide-y divide-gray-200"}
    [:thead {:class "bg-gray-50"}
     [:tr
      [:th {:class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"} "Plugin ID"]
      [:th {:class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"} "Description"]
      [:th {:class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"} "Key Responsibilities"]
      [:th {:class "px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider"} "Actions"]]]
    [:tbody {:class "bg-white divide-y divide-gray-200"}
     (if (empty? plugins)
       [:tr
        [:td {:class "px-6 py-4 whitespace-nowrap text-sm text-gray-500" :col-span 4} "No plugins registered."]]
       (for [p (sort-by :id plugins)]
         ^{:key (:id p)}
         [:tr {:class "hover:bg-gray-50 transition-colors"}
          [:td {:class "px-6 py-4 whitespace-nowrap font-mono text-sm text-blue-600"} (str (:id p))]
          [:td {:class "px-6 py-4 text-sm text-gray-500"} (:description p)]
          [:td {:class "px-6 py-4 text-sm text-gray-500"} (:responsibilities p)]
          [:td {:class "px-6 py-4 whitespace-nowrap text-right text-sm font-medium"}
           [:button {:class "text-blue-600 hover:text-blue-900"
                     :on-click #(on-select p)}
            "View Docs"]]]))]]])

(defn design-doc-view [plugins]
  (let [selected-plugin (r/atom nil)]
    (fn [plugins]
      (let [infra (filter #(= (:type %) :infrastructure) plugins)
            features (filter #(= (:type %) :feature) plugins)]
        (if @selected-plugin
          [plugin-detail-view @selected-plugin #(reset! selected-plugin nil)]
          
          [:div {:class "space-y-8 text-gray-800"}
           ;; Title
           [:div
            [:h1 {:class "text-3xl font-bold text-gray-900"} "PLIN Platform - Architecture & Guide"]
            [:p {:class "mt-2 text-lg text-gray-600"}
             "A data-driven, modular architecture for ClojureScript applications based on Dependency Injection and Plugins."]]

           ;; Section 1: Core Concepts
           [:section
            [:h2 {:class "text-2xl font-semibold text-gray-900 mb-4"} "1. Core Concepts"]
            [:div {:class "grid grid-cols-1 md:grid-cols-3 gap-6"}
             [:div {:class "bg-blue-50 p-4 rounded-lg border border-blue-100"}
              [:h3 {:class "font-bold text-blue-900 mb-2"} "Plugins"]
              [:p {:class "text-sm text-blue-800"} "The unit of modularity. Defined in " [:code "plin.edn"] ". A plugin can define Beans, Extensions, and Contributions."]]
             [:div {:class "bg-green-50 p-4 rounded-lg border border-green-100"}
              [:h3 {:class "font-bold text-green-900 mb-2"} "Beans (DI)"]
              [:p {:class "text-sm text-green-800"} "The unit of logic. Functions, values, or components managed by the container. They can be injected into other beans."]]
             [:div {:class "bg-purple-50 p-4 rounded-lg border border-purple-100"}
              [:h3 {:class "font-bold text-purple-900 mb-2"} "Extensions"]
              [:p {:class "text-sm text-purple-800"} "The unit of composition. Plugins define extension points (contracts) that other plugins can fulfill."]]]]

           ;; Section 2: Interface vs Implementation
           [:section
            [:h2 {:class "text-2xl font-semibold text-gray-900 mb-4"} "2. Interface vs. Implementation"]
            [:p {:class "mb-4"} "To ensure loose coupling, PLIN separates contracts from logic."]
            [:ul {:class "list-disc pl-5 space-y-2 mb-4"}
             [:li [:strong "Interface Plugins (i-*)"] ": Define the contract (extension points, bean keys). No heavy logic."]
             [:li [:strong "Implementation Plugins (p-*)"] ": Provide the logic. They depend on interfaces, not on other implementations."]]
            [:div {:class "bg-gray-50 p-4 rounded border border-gray-200 text-sm"}
             [:p "Example: " [:code "p-my-feature"] " depends on " [:code "i-database"] ", not " [:code "p-alasql-db"] ". This allows swapping the database implementation without changing feature code."]]]

           ;; Section 3: Bean Definitions
           [:section
            [:h2 {:class "text-2xl font-semibold text-gray-900 mb-4"} "3. Defining Beans"]
            [:p {:class "mb-4"} "Beans are defined in the " [:code ":beans"] " map of a plugin. There are three main types:"]
            
            [:div {:class "space-y-4"}
             [:div
              [:h4 {:class "font-bold text-gray-800"} "A. Value Bean"]
              [:p {:class "text-sm text-gray-600 mb-1"} "Injects a literal value."]
              [highlight-code "{::config [:= {:env :dev}]}"]]
             
             [:div
              [:h4 {:class "font-bold text-gray-800"} "B. Function Bean"]
              [:p {:class "text-sm text-gray-600 mb-1"} "Injects the result of calling a function. Arguments are other bean keys."]
              [highlight-code "{::service [make-service ::db-connection]}"]]
             
             [:div
              [:h4 {:class "font-bold text-gray-800"} "C. Component Bean (Reagent)"]
              [:p {:class "text-sm text-gray-600 mb-1"} "Injects the component function itself (so it can be rendered). Use " [:code "[:="] " or " [:code "identity"] "."]
              [highlight-code "{::ui-component [:= my-reagent-component]}"]]]]

           ;; Section 4: Extension Examples
           [:section
            [:h2 {:class "text-2xl font-semibold text-gray-900 mb-4"} "4. Common Patterns"]

            [:div {:class "space-y-8"}
             ;; Pattern A: Navigation
             [:div {:class "bg-white p-6 rounded-lg border border-gray-200 shadow-sm"}
              [:h3 {:class "text-lg font-bold text-gray-900 mb-2"} "Pattern A: Navigation with Injection"]
              [:p {:class "mb-3 text-sm text-gray-600"} 
               "To add a page, contribute to " [:code "::iapp/nav-items"] ". Use the " [:strong "Bean Constructor Pattern"] " to inject your UI component into the nav item."]
              
              [highlight-code
               "(def plugin\n  (plin/plugin\n   {:deps [iapp/plugin]\n    :contributions\n    {::iapp/nav-items [::nav-item]}\n\n    :beans\n    {;; 1. The Page Component\n     ::ui [:= my-page-component]\n\n     ;; 2. The Nav Item (injects ::ui)\n     ::nav-item\n     {:constructor [(fn [ui]\n                      {:id :my-feature\n                       :label \"My Feature\"\n                       :route \"feature\"\n                       :component ui\n                       :extra {:badge \"New\"}}) ;; Optional metadata\n                    ::ui]}}}))"]]

             ;; Pattern B: Global Overlays
             [:div {:class "bg-white p-6 rounded-lg border border-gray-200 shadow-sm"}
              [:h3 {:class "text-lg font-bold text-gray-900 mb-2"} "Pattern B: Global Overlays"]
              [:p {:class "mb-3 text-sm text-gray-600"} 
               "To add global UI elements (like modals, command palettes, or debug tools), contribute to " [:code "::iapp/overlay-components"] "."]
              
              [highlight-code
               "(def plugin\n  (plin/plugin\n   {:deps [iapp/plugin]\n    :contributions\n    {::iapp/overlay-components [::my-overlay]}\n\n    :beans\n    {::my-overlay [:= my-overlay-component]}}))"]]]]

           ;; Section 5: Plugin Catalog
           [:section
            [:h2 {:class "text-2xl font-semibold text-gray-900 mb-4"} "5. Plugin Catalog"]
            [:p {:class "mb-4 text-gray-600"} "List of all plugins currently loaded in the system."]

            [:h3 {:class "text-xl font-medium text-gray-900 mb-3"} "Infrastructure Plugins"]
            [plugin-table infra #(reset! selected-plugin %)]

            [:h3 {:class "text-xl font-medium text-gray-900 mt-6 mb-3"} "Feature Plugins"]
            [plugin-table features #(reset! selected-plugin %)]]])))))
