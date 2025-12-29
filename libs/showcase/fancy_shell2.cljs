(ns showcase.fancy-shell2
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-breadcrumb :as ibread]
            [plinpt.i-application :as iapp-core]
            [plinpt.i-session :as isession]))

;; --- Router Logic ---

(defonce current-route (r/atom nil))

(defn- match-route [route-defs path]
  (let [clean-path (if (or (str/blank? path) (= path "#")) "/" (str/replace path #"^#" ""))]
    (or (some (fn [def]
                (let [def-path (:path def)]
                  (if (and def-path (str/includes? def-path ":"))
                    (let [path-parts (str/split clean-path #"/")
                          def-parts (str/split def-path #"/")]
                      (when (= (count path-parts) (count def-parts))
                        (when (every? (fn [[p d]] (or (= p d) (str/starts-with? d ":")))
                                      (map vector path-parts def-parts))
                          def)))
                    (when (= def-path clean-path) def))))
              route-defs)
        nil)))

(defn- handle-hash-change [routes]
  (let [hash (.-hash js/location)]
    (reset! current-route (match-route routes hash))))

;; --- State ---

(defonce shell-state (r/atom {:collapsed? false}))

;; --- Components ---

(defn icon-menu []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6h16M4 12h16M4 18h16"}]])

(defn icon-chevron-left []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 19l-7-7 7-7"}]])

(defn icon-chevron-right []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]])

(defn nav-item [item collapsed? current-route]
  (let [active? (= (:route item) (:path current-route))
        label (:label item)
        initial (when (seq label) (subs label 0 1))]
    [:a {:href (str "#" (:route item))
         :class (str "flex items-center gap-3 p-3 rounded-xl transition-all duration-200 mb-1 group "
                     (if active?
                       "bg-gradient-to-r from-cyan-500/20 to-blue-500/20 text-cyan-400 border border-cyan-500/20 shadow-[0_0_15px_rgba(6,182,212,0.15)]"
                       "text-slate-400 hover:bg-white/5 hover:text-white"))
         :title (when collapsed? label)}
     
     ;; Icon / Initial
     [:div {:class (str "w-6 h-6 flex items-center justify-center flex-shrink-0 transition-colors "
                        (if active? "text-cyan-400" "text-slate-500 group-hover:text-white"))}
      (if (:icon item)
        [(:icon item)]
        [:span {:class "font-bold text-lg uppercase"} initial])]
     
     ;; Label
     [:span {:class (str "font-medium truncate transition-all duration-300 "
                         (if collapsed? "w-0 opacity-0" "w-auto opacity-100"))}
      label]]))

(defn sidebar [items user-widget collapsed?]
  [:aside {:class (str "bg-black/20 backdrop-blur-lg border-r border-white/10 flex flex-col transition-all duration-300 z-20 "
                       (if collapsed? "w-20" "w-72"))}
   
   ;; Header / Toggle
   [:div {:class "h-20 flex items-center justify-between px-0 border-b border-white/5 relative"}
    [:div {:class (str "flex items-center gap-3 px-6 transition-opacity duration-300 "
                       (if collapsed? "opacity-0 pointer-events-none" "opacity-100"))}
     [:div {:class "w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-400 to-blue-600 flex items-center justify-center text-white font-bold"} "P"]
     [:h1 {:class "text-xl font-black tracking-tighter text-white whitespace-nowrap"} "PLIN OS"]]
    
    ;; Toggle Button
    [:button {:class "absolute right-0 top-0 h-full w-12 flex items-center justify-center text-slate-500 hover:text-white transition-colors"
              :on-click #(swap! shell-state update :collapsed? not)}
     (if collapsed? [icon-chevron-right] [icon-chevron-left])]]

   ;; Navigation
   [:nav {:class "flex-1 px-3 py-6 overflow-y-auto overflow-x-hidden scrollbar-hide"}
    (when-not collapsed?
      [:div {:class "px-3 text-xs font-bold text-slate-600 uppercase tracking-wider mb-4"} "Menu"])
    
    (doall
     (for [item (sort-by :order items)]
       ^{:key (:route item)}
       [nav-item item collapsed? @current-route]))]

   ;; User Widget (Bottom)
   [:div {:class "p-4 border-t border-white/10 bg-black/20 min-h-[80px] flex items-center justify-center overflow-hidden"}
    [:div {:class (str "transition-all duration-300 "
                       (if collapsed? "scale-75" "scale-100 w-full"))}
     (if user-widget
       [user-widget]
       [:div {:class "text-slate-500 text-sm"} "No User"])]]])

(defn fancy-layout-2 [routes overlays can? user-atom breadcrumb nav-items user-widget]
  ;; Setup Router
  (let [hash-handler #(handle-hash-change routes)]
    (set! (.-onhashchange js/window) hash-handler)
    (handle-hash-change routes)
    
    (fn []
      (let [active-route @current-route
            Page (:component active-route)
            collapsed? (:collapsed? @shell-state)]
        [:div {:class "min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 text-white font-sans flex overflow-hidden"}
         
         ;; Sidebar
         [sidebar nav-items user-widget collapsed?]

         ;; Main Area
         [:main {:class "flex-1 flex flex-col h-screen overflow-hidden relative"}
          ;; Decorative background elements
          [:div {:class "absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0"}
           [:div {:class "absolute top-[-10%] right-[-5%] w-96 h-96 bg-blue-500/10 rounded-full blur-3xl"}]
           [:div {:class "absolute bottom-[-10%] left-[-5%] w-96 h-96 bg-purple-500/10 rounded-full blur-3xl"}]]

          ;; Top Bar
          [:header {:class "h-20 flex-shrink-0 flex items-center justify-between px-8 z-10"}
           [:div {:class "flex items-center gap-4"}
            (when breadcrumb 
              [:div {:class "bg-white/5 px-4 py-2 rounded-full border border-white/10 backdrop-blur-sm"}
               [breadcrumb]])]
           [:div {:class "flex gap-4"}
            ;; Actions placeholder
            ]]

          ;; Content
          [:div {:class "flex-1 overflow-auto px-8 pb-8 z-10"}
           [:div {:class "bg-white/5 backdrop-blur-md border border-white/10 rounded-3xl p-8 min-h-full shadow-2xl relative overflow-hidden"}
            (cond
              (not Page) 
              [:div {:class "flex flex-col items-center justify-center h-full text-slate-400"}
               [:div {:class "text-6xl mb-4 font-thin"} "404"]
               [:div "Destination Unknown"]]
              
              (and (:required-perm active-route) (not (can? (:required-perm active-route))))
              [:div {:class "flex flex-col items-center justify-center h-full text-red-400"}
               [:div {:class "text-6xl mb-4 font-thin"} "403"]
               [:div "Access Restricted"]]
              
              :else [Page])]]]

         ;; Overlays
         (for [[idx comp] (map-indexed vector overlays)]
           ^{:key idx} [comp])]))))

(def plugin
  (plin/plugin
   {:doc "A fancy, dark-themed replacement for the App Shell (v2)."
    :deps [iapp/plugin iauth/plugin ibread/plugin iapp-core/plugin isession/plugin]
    
    :beans
    {::iapp/ui
     ^{:doc "Fancy App Shell UI v2."
       :reagent-component true}
     [partial fancy-layout-2
      ::iapp/routes
      ::iapp/overlay-components
      ::iauth/can?
      ::iauth/user
      ::ibread/ui
      ::iapp-core/nav-items
      ::isession/user-widget]}}))
