(ns plinpt-extras.showcase.hn-shell
  "A shell styled after Hacker News - minimalist, orange-themed, classic web aesthetic."
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-application :as iapp]
            [plinpt.i-router :as irouter]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-session :as isession]
            [plinpt.i-head-config :as ihead]))

;; --- Constants ---

(def hn-orange "#ff6600")
(def hn-bg "#f6f6ef")
(def hn-link "#000000")
(def hn-meta "#828282")

;; --- Helpers ---

(defn- normalize-path [path]
  (if (or (str/blank? path) (= path "#"))
    "/"
    (str/replace path #"^#" "")))

(defn- path-matches? [current-path item-path]
  (let [current (normalize-path current-path)
        target (normalize-path item-path)]
    (or (= current target)
        (and (not= target "/")
             (str/starts-with? current (str target "/"))))))

;; --- Components ---

(defn hn-logo []
  [:div {:class "flex items-center gap-1 px-1 border border-white"
         :style {:background-color "#fff"}}
   [:span {:class "font-bold text-sm"
           :style {:color hn-orange}}
    "Y"]])

(defn nav-link [item current-path navigate!]
  (let [active? (path-matches? current-path (:full-route item))]
    [:a {:href (str "#" (:full-route item))
         :on-click (fn [e]
                     (.preventDefault e)
                     (navigate! (:full-route item)))
         :class (str "text-sm hover:underline "
                     (if active? "text-white font-bold" "text-black"))}
     (:label item)]))

(defn header-bar [structure current-path navigate! user-data user-actions app-name]
  (let [{:keys [logged? name]} @user-data
        {:keys [login! logout!]} user-actions
        sorted-items (sort-by :order structure)]
    [:header {:style {:background-color hn-orange}}
     [:div {:class "max-w-5xl mx-auto px-2 py-0.5"}
      [:div {:class "flex items-center gap-2 text-sm"}
       ;; Logo
       [hn-logo]
       
       ;; App name
       [:a {:href "#/"
            :on-click (fn [e]
                        (.preventDefault e)
                        (navigate! "/"))
            :class "font-bold text-black hover:no-underline mr-1"}
        (or app-name "PLIN")]
       
       ;; Navigation links
       [:nav {:class "flex items-center gap-2 flex-1"}
        (doall
         (for [item sorted-items]
           ^{:key (:id item)}
           [:<>
            [nav-link item current-path navigate!]
            [:span {:class "text-black"} "|"]]))]
       
       ;; User section
       [:div {:class "flex items-center gap-2"}
        (if logged?
          [:<>
           [:span {:class "text-black text-sm"} name]
           [:span {:class "text-black"} "|"]
           [:a {:href "#"
                :on-click (fn [e]
                            (.preventDefault e)
                            (logout!))
                :class "text-black text-sm hover:underline"}
            "logout"]]
          [:a {:href "#"
               :on-click (fn [e]
                           (.preventDefault e)
                           (login!))
               :class "text-black text-sm hover:underline"}
           "login"])]]]]))

(defn breadcrumb [path]
  (when (and path (not= path "/"))
    (let [parts (str/split path #"/")
          parts (filter (complement str/blank?) parts)]
      [:div {:class "text-xs mb-4"
             :style {:color hn-meta}}
       [:a {:href "#/" :class "hover:underline"} "home"]
       (for [[idx part] (map-indexed vector parts)]
         ^{:key idx}
         [:<>
          [:span " > "]
          [:span part]])])))

(defn main-content [current-route can?]
  (let [route @current-route
        Page (:component route)
        path (:path route)]
    [:main {:class "max-w-5xl mx-auto px-2 py-4"}
     [breadcrumb path]
     (cond
       (not Page)
       [:div {:class "py-8"}
        [:p {:class "text-sm" :style {:color hn-meta}} 
         "404 - Page not found."]
        [:p {:class "text-sm mt-2"}
         [:a {:href "#/" :class "hover:underline" :style {:color hn-link}} 
          "Return to homepage"]]]
       
       (and (:required-perm route) (not (can? (:required-perm route))))
       [:div {:class "py-8"}
        [:p {:class "text-sm" :style {:color hn-meta}} 
         "Permission denied. You don't have access to this page."]
        [:p {:class "text-sm mt-2"}
         [:a {:href "#/" :class "hover:underline" :style {:color hn-link}} 
          "Return to homepage"]]]
       
       :else
       [Page])]))

(defn footer []
  [:footer {:class "max-w-5xl mx-auto px-2 py-4 mt-8 border-t-2"
            :style {:border-color hn-orange}}
   [:div {:class "text-center text-xs" :style {:color hn-meta}}
    [:div {:class "flex justify-center gap-2 flex-wrap"}
     [:span "Guidelines"]
     [:span "|"]
     [:span "FAQ"]
     [:span "|"]
     [:span "Lists"]
     [:span "|"]
     [:span "API"]
     [:span "|"]
     [:span "Security"]
     [:span "|"]
     [:span "Legal"]
     [:span "|"]
     [:span "Apply to YC"]
     [:span "|"]
     [:span "Contact"]]
    [:div {:class "mt-2"}
     "Powered by PLIN Platform"]]])

(defn hn-shell [structure current-route navigate! setup! homepage
                overlays login-modal can? user-data user-actions app-name]
  (r/create-class
   {:component-did-mount
    (fn []
      (let [home-path (or (:route homepage) "/")]
        (setup! home-path)))
    
    :reagent-render
    (fn []
      (let [route @current-route
            path (:path route)]
        [:div {:class "min-h-screen flex flex-col"
               :style {:background-color hn-bg
                       :font-family "Verdana, Geneva, sans-serif"}}
         
         [header-bar structure path navigate! user-data user-actions app-name]
         
         [:div {:class "flex-1"}
          [main-content current-route can?]]
         
         [footer]
         
         ;; Login Modal
         (when login-modal [login-modal])
         
         ;; Overlays
         (for [[idx comp] (map-indexed vector overlays)]
           ^{:key idx} [comp])]))}))

;; --- Custom Styles ---

(def custom-styles
  "
  /* HN Shell - Override default styles for classic web look */
  .hn-shell a {
    color: #000000;
  }
  
  .hn-shell a:visited {
    color: #828282;
  }
  
  /* Make content area have classic web feel */
  .hn-shell main {
    font-size: 13px;
    line-height: 1.4;
  }
  
  /* Style buttons to look more classic */
  .hn-shell button {
    font-family: Verdana, Geneva, sans-serif;
    font-size: 13px;
  }
  
  /* Compact tables */
  .hn-shell table {
    font-size: 13px;
  }
  
  /* Classic link styling */
  .hn-shell .storylink {
    color: #000000;
    text-decoration: none;
  }
  
  .hn-shell .storylink:hover {
    text-decoration: underline;
  }
  
  .hn-shell .comhead {
    color: #828282;
    font-size: 11px;
  }
  ")

(def plugin
  (plin/plugin
   {:doc "A shell styled after Hacker News - minimalist, orange-themed, classic web aesthetic."
    :deps [iapp/plugin irouter/plugin iauth/plugin isession/plugin ihead/plugin]
    
    :contributions
    {::ihead/inline-styles [custom-styles]
     ::iapp/ui ::ui}
    
    :beans
    {::ui
     ^{:doc "Hacker News style Shell UI component."
       :reagent-component true}
     [partial hn-shell
      ::iapp/structure
      ::irouter/current-route
      ::irouter/navigate!
      ::irouter/setup!
      ::iapp/homepage
      ::iapp/overlay-components
      ::isession/login-modal
      ::iauth/can?
      ::isession/user-data
      ::isession/user-actions
      ::iapp/name]}}))
