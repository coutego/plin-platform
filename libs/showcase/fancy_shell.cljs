(ns showcase.fancy-shell
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-breadcrumb :as ibread]))

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

;; --- Components ---

(defn nav-item-wrapper [child]
  [:div {:class "mb-2 p-2 rounded hover:bg-white/10 transition-colors duration-200"}
   child])

(defn fancy-layout [header routes overlays can? user-atom breadcrumb]
  ;; Setup Router
  (let [hash-handler #(handle-hash-change routes)]
    (set! (.-onhashchange js/window) hash-handler)
    (handle-hash-change routes)
    
    (fn []
      (let [active-route @current-route
            Page (:component active-route)
            user @user-atom]
        [:div {:class "min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 text-white font-sans flex"}
         
         ;; Sidebar
         [:aside {:class "w-72 bg-black/20 backdrop-blur-lg border-r border-white/10 flex flex-col"}
          [:div {:class "p-8"}
           [:h1 {:class "text-3xl font-black tracking-tighter bg-clip-text text-transparent bg-gradient-to-r from-cyan-400 to-blue-500"}
            "PLIN OS"]]
          
          [:nav {:class "flex-1 px-6 py-4 overflow-y-auto"}
           [:div {:class "text-xs font-bold text-slate-500 uppercase tracking-wider mb-4"} "Menu"]
           (for [[idx comp] (map-indexed vector header)]
             ^{:key idx} [nav-item-wrapper [comp]])]
          
          [:div {:class "p-6 border-t border-white/10 bg-black/20"}
           [:div {:class "flex items-center gap-3"}
            [:div {:class "w-10 h-10 rounded-full bg-gradient-to-tr from-pink-500 to-violet-500 flex items-center justify-center font-bold"}
             (if user (subs (str (:name user)) 0 1) "?")]
            [:div
             [:div {:class "font-medium"} (if user (:name user) "Guest")]
             [:div {:class "text-xs text-slate-400"} (if user "Online" "Offline")]]]]]

         ;; Main Area
         [:main {:class "flex-1 flex flex-col h-screen overflow-hidden relative"}
          ;; Decorative background elements
          [:div {:class "absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0"}
           [:div {:class "absolute top-[-10%] right-[-5%] w-96 h-96 bg-blue-500/10 rounded-full blur-3xl"}]
           [:div {:class "absolute bottom-[-10%] left-[-5%] w-96 h-96 bg-purple-500/10 rounded-full blur-3xl"}]]

          ;; Top Bar
          [:header {:class "h-20 flex items-center justify-between px-10 z-10"}
           [:div {:class "flex items-center gap-4"}
            (when breadcrumb 
              [:div {:class "bg-white/5 px-4 py-2 rounded-full border border-white/10"}
               [breadcrumb]])]
           [:div {:class "flex gap-4"}
            ;; Could put extra actions here
            ]]

          ;; Content
          [:div {:class "flex-1 overflow-auto px-10 pb-10 z-10"}
           [:div {:class "bg-white/5 backdrop-blur-md border border-white/10 rounded-3xl p-8 min-h-full shadow-2xl"}
            (cond
              (not Page) 
              [:div {:class "flex flex-col items-center justify-center h-64 text-slate-400"}
               [:div {:class "text-6xl mb-4"} "404"]
               [:div "Destination Unknown"]]
              
              (and (:required-perm active-route) (not (can? (:required-perm active-route))))
              [:div {:class "flex flex-col items-center justify-center h-64 text-red-400"}
               [:div {:class "text-6xl mb-4"} "403"]
               [:div "Access Restricted"]]
              
              :else [Page])]]]

         ;; Overlays
         (for [[idx comp] (map-indexed vector overlays)]
           ^{:key idx} [comp])]))))

(def plugin
  (plin/plugin
   {:doc "A fancy, dark-themed replacement for the App Shell."
    :deps [iapp/plugin iauth/plugin ibread/plugin]
    
    :beans
    {::iapp/ui
     ^{:doc "Fancy App Shell UI."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial fancy-layout
      ::iapp/header-components
      ::iapp/routes
      ::iapp/overlay-components
      ::iauth/can?
      ::iauth/user
      ::ibread/ui]}}))
