(ns showcase.zen-mode
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-application :as iapp]
            [plinpt.i-head-config :as ihead]))

(defonce zen? (r/atom false))

(defn toggle-zen! []
  (swap! zen? not)
  (if @zen?
    (.add (.-classList js/document.body) "zen-mode")
    (.remove (.-classList js/document.body) "zen-mode")))

(defn icon-leaf []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"}]])

(defn header-button []
  [:button {:class (str "p-2 rounded-full transition-all duration-300 "
                        (if @zen? 
                          "bg-emerald-500 text-white shadow-[0_0_15px_rgba(16,185,129,0.5)]" 
                          "text-slate-400 hover:text-slate-600 hover:bg-slate-100"))
            :on-click toggle-zen!
            :title (if @zen? "Exit Zen Mode" "Enter Zen Mode")}
   [icon-leaf]])

;; CSS that targets the default p-app-shell structure
(def zen-styles
  "
  /* Hide the fixed header */
  body.zen-mode header {
    display: none !important;
  }
  
  /* Hide the sidebar (aside element) */
  body.zen-mode aside {
    display: none !important;
  }
  
  /* Hide breadcrumbs */
  body.zen-mode nav[aria-label='Breadcrumb'] {
    display: none !important;
  }
  
  /* Remove the top padding that accounts for fixed header (pt-16 = 4rem) */
  body.zen-mode .pt-16 {
    padding-top: 0 !important;
  }
  
  /* Remove sidebar margin - the shell uses ml-64 or ml-0 dynamically */
  body.zen-mode .ml-64 {
    margin-left: 0 !important;
  }
  
  /* Ensure main takes full width */
  body.zen-mode main {
    margin-left: 0 !important;
    width: 100% !important;
  }
  
  /* Add some nice padding to content in zen mode */
  body.zen-mode main > div {
    max-width: 1200px;
    margin: 0 auto;
  }
  
  /* Smooth transitions */
  header, aside, main, .pt-16, .ml-64 {
    transition: all 0.3s ease !important;
  }
  
  /* Subtle background change for zen mode */
  body.zen-mode {
    background-color: #fafafa;
  }
  ")

(defn floating-exit-button []
  (when @zen?
    [:div {:class "fixed bottom-6 right-6 z-[100]"}
     [:button {:class "p-4 rounded-full bg-emerald-500 text-white shadow-lg hover:bg-emerald-600 hover:scale-110 transition-all duration-200 group animate-pulse"
               :on-click toggle-zen!
               :title "Exit Zen Mode"}
      [:div {:class "relative"}
       [icon-leaf]
       [:span {:class "absolute -top-10 right-0 bg-slate-800 text-white text-xs px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none"}
        "Exit Zen Mode"]]]]))

(defn zen-overlay []
  ;; This component renders the styles and the floating button
  ;; The body class is managed by toggle-zen!
  [:<>
   [:style zen-styles]
   [floating-exit-button]])

(def plugin
  (plin/plugin
   {:doc "Zen Mode - Hide navigation and focus on content. Toggle via header button or floating button."
    :deps [iapp/plugin ihead/plugin]
    
    :contributions
    {::iapp/header-components [::header-btn]
     ::iapp/overlay-components [::overlay]}
    
    :beans
    {::header-btn
     ^{:doc "Zen Mode Toggle Button for the header."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= header-button]

     ::overlay
     ^{:doc "Zen Mode Overlay - injects styles and shows exit button when active."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= zen-overlay]}}))
