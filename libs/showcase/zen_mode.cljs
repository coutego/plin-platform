(ns showcase.zen-mode
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-application :as iapp]))

(defonce zen? (r/atom false))

(defn toggle-zen! []
  (swap! zen? not)
  (if @zen?
    (.-add (.-classList js/document.body) "zen-mode")
    (.-remove (.-classList js/document.body) "zen-mode")))

(defn zen-style []
  [:style
   "
   /* Hide Header (Top Bar) */
   header { display: none !important; }

   /* Hide Sidebar */
   aside { display: none !important; }
   
   /* Hide Breadcrumbs if they are in a separate container or nav */
   .breadcrumb-container, nav[aria-label='Breadcrumb'] { display: none !important; }
   
   /* Force Main content to be full width */
   main { 
      width: 100% !important; 
      max-width: 100% !important; 
      margin: 0 !important;
      padding-left: 2rem !important;
      padding-right: 2rem !important;
   }
   "])

(defn icon-leaf []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M12 19l9 2-9-18-9 18 9-2zm0 0v-8"}]])

(defn header-button []
  [:button {:class (str "p-2 rounded-full transition-all duration-300 "
                        (if @zen? 
                          "bg-emerald-500 text-white shadow-[0_0_15px_rgba(16,185,129,0.5)] rotate-180" 
                          "text-slate-400 hover:text-white hover:bg-white/10"))
            :on-click toggle-zen!
            :title "Enter Zen Mode"}
   [icon-leaf]])

(defn floating-overlay []
  (when @zen?
    [:div
     [zen-style]
     [:div {:class "fixed bottom-6 right-6 z-50 animate-in fade-in zoom-in duration-300"}
      [:button {:class "p-4 rounded-full bg-emerald-500 text-white shadow-lg hover:bg-emerald-600 hover:scale-110 transition-all duration-200"
                :on-click toggle-zen!
                :title "Exit Zen Mode"}
       [icon-leaf]]]]))

(def plugin
  (plin/plugin
   {:doc "Zen Mode Toggle Plugin."
    :deps [iapp/plugin]
    
    :contributions
    {::iapp/header-components [::header-btn]
     ::iapp/overlay-components [::floating-overlay]}
    
    :beans
    {::header-btn
     ^{:doc "Zen Mode Toggle Button (Header)."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= header-button]

     ::floating-overlay
     ^{:doc "Zen Mode Overlay (Style + Floating Button)."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= floating-overlay]}}))
