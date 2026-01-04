(ns plinpt.p-app-shell.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- State ---
;; Note: Routing state has been moved to p-hash-router.
;; These atoms are for shell-specific concerns only.

(defonce head-injected? (atom false))
(defonce breadcrumb-initialized? (atom false))
(defonce router-initialized? (atom false))

;; --- Helpers ---

(defn- normalize-hash [hash]
  (if (or (str/blank? hash) (= hash "#"))
    "/"
    (str/replace hash #"^#" "")))

(defn- inject-head-once! [head-inject!]
  (when (and head-inject! (not @head-injected?))
    (try
      (head-inject!)
      (catch :default e
        (js/console.error "[AppShell] Error in head-inject!:" e)))
    (reset! head-injected? true)))

(defn- init-breadcrumb-once! [trail-actions]
  (when (and trail-actions (not @breadcrumb-initialized?))
    (when-let [navigate! (:navigate! trail-actions)]
      (let [path (normalize-hash (.-hash js/location))]
        (navigate! path)))
    (reset! breadcrumb-initialized? true)))

;; --- Helper to resolve homepage ---

(defn- find-node-by-id [nodes id]
  (reduce (fn [found node]
            (if found found
                (if (= (:id node) id)
                  node
                  (find-node-by-id (:children node) id))))
          nil
          nodes))

(defn- resolve-homepage [homepage structure]
  (let [result (if (map? homepage)
                 ;; If homepage is an item map, find it in structure to get full route
                 (let [id (:id homepage)
                       node (find-node-by-id structure id)]
                   (or (:full-route node) (:route homepage) "/"))
                 ;; Fallback for legacy or simple string
                 (or homepage "/"))]
    result))

;; --- Icon Components ---

(defn icon-menu []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6h16M4 12h16M4 18h16"}]])

(defn icon-search []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4 text-slate-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"}]])

(defn icon-bell []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6
11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"}]])

(defn icon-dashboard []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 01-2 2h-2a2 2 0
01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"}]])

;; --- User Widget Component ---

(defn user-widget [user-data user-actions]
  (let [data (when (and user-data (satisfies? IDeref user-data)) @user-data)
        {:keys [logged? name initials]} (or data {})
        {:keys [login! logout!]} (or user-actions {})]
    (if logged?
      ;; Logged in state - User Pill with dropdown
      [:div {:class "relative group"}
       [:button {:class "flex items-center gap-2 pl-2 pr-4 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 shadow-sm transition-colors"}
        [:div {:class "h-7 w-7 rounded-lg bg-gradient-to-br from-slate-100 to-slate-200 flex items-center justify-center text-xs font-bold text-slate-600 border border-slate-300"}
         initials]
        [:div {:class "text-left leading-tight"}
         [:p {:class "text-xs font-semibold text-slate-900"} name]]]

       ;; Dropdown menu
       [:div {:class "absolute right-0 top-full pt-2 w-48 hidden group-hover:block z-50"}
        [:div {:class "bg-white rounded-xl shadow-lg py-1 ring-1 ring-black ring-opacity-5 border border-slate-200"}
         [:div {:class "px-4 py-2 border-b border-slate-100"}
          [:p {:class "text-sm font-medium text-slate-900"} name]
          [:p {:class "text-xs text-slate-500"} "Logged in"]]
         [:button {:class "block w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
                   :on-click (fn [e]
                               (.preventDefault e)
                               (.stopPropagation e)
                               (when logout! (logout!)))}
          "Sign out"]]]]

      ;; Logged out state - Login button
      [:button {:class "flex items-center gap-2 px-4 py-2 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 shadow-sm transition-colors text-sm font-medium text-slate-700"
                :on-click (fn [e]
                            (.preventDefault e)
                            (.stopPropagation e)
                            (when login! (login!)))}
       "Log in"])))

;; --- Breadcrumb Component ---

(defn breadcrumb [trail-data trail-actions]
  (let [data (when (and trail-data (satisfies? IDeref trail-data)) @trail-data)
        {:keys [trail]} (or data {})
        {:keys [navigate!]} (or trail-actions {})]
    (when (seq trail)
      [:nav {:class "flex text-xs text-slate-400 font-medium mb-4" :aria-label "Breadcrumb"}
       [:ol {:class "inline-flex items-center"}
        (doall
         (for [[idx item] (map-indexed vector trail)]
           ^{:key (str (:path item) "-" idx)}
           [:li {:class "inline-flex items-center"}
            (when (pos? idx)
              [:span {:class "mx-2 text-slate-300"} "/"])
            (if (= idx (dec (count trail)))
              [:span {:class "text-slate-700 font-semibold"} (:label item)]
              [:a {:href (str "#" (:path item))
                   :on-click (fn [e]
                               (.preventDefault e)
                               (when navigate! (navigate! (:path item))))
                   :class "hover:text-ec-blue transition-colors"}
               (:label item)])]))]])))

;; --- Header Component ---

(defn header [app-name app-subtitle app-logo user-data user-actions sidebar-toggle! homepage-path]
  [:header {:class "bg-white/80 backdrop-blur-md border-b border-slate-200/80 h-16 flex items-center justify-between pl-2 pr-6 fixed w-full z-40 transition-all duration-300"}
   [:div {:class "flex items-center gap-4"}
    ;; Hamburger Menu Toggle
    [:button {:class "p-2 rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
              :on-click #(when sidebar-toggle! (sidebar-toggle!))}
     [icon-menu]]

    ;; App Logo
    [:a {:href (str "#" homepage-path) :class "flex items-center gap-3 hover:opacity-80 transition-opacity"}
     (when app-logo
       [:div {:class "flex items-center justify-center"}
        [app-logo]])
     [:div
      [:h1 {:class "text-xl font-bold text-slate-900 tracking-tight leading-none"} app-name]
      [:p {:class "text-xs text-slate-500 mt-0.5"} app-subtitle]]]]

   ;; Right Controls
   [:div {:class "flex items-center gap-3 ml-auto"}
    ;; Search Pill
    [:div {:class "hidden md:flex items-center gap-2 bg-slate-100/50 hover:bg-slate-100 border border-slate-200 rounded-xl px-3 py-2 transition-colors w-64"}
     [icon-search]
     [:input {:type "text" :placeholder "Search..." :class "bg-transparent text-sm w-full outline-none placeholder:text-slate-400 text-slate-700"}]]

    [:div {:class "h-8 w-px bg-slate-200 mx-1"}]

    ;; Notification Pill
    [:button {:class "h-10 w-10 flex items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 shadow-sm"}
     [icon-bell]]

    ;; User Widget
    [user-widget user-data user-actions]]])

;; --- Main Content Area ---

(defn main-content [current-route-atom can? user-atom trail-data trail-actions sidebar-collapsed?]
  (let [active-route (when (and current-route-atom (satisfies? IDeref current-route-atom)) 
                       @current-route-atom)
        Page (:component active-route)
        params (:params active-route)
        required-perm (:required-perm active-route)
        user (when (and user-atom (satisfies? IDeref user-atom)) @user-atom)
        collapsed? (if sidebar-collapsed? (sidebar-collapsed?) false)]

    [:main {:class (str "flex-1 p-6 md:p-8 overflow-x-hidden transition-all duration-300 "
                        (if collapsed? "ml-0" "ml-64"))}
     ;; Breadcrumbs
     [breadcrumb trail-data trail-actions]

     ;; Page Content
     (cond
       (not Page)
       (do
         [:div {:class "flex flex-col items-center justify-center h-64 text-slate-500"}
          [:h2 {:class "text-2xl font-bold mb-2"} "404"]
          [:p "Page not found"]])

       (and required-perm (nil? user))
       [:div {:class "flex flex-col items-center justify-center h-64 text-slate-500"}
        [:h2 {:class "text-2xl font-bold mb-2"} "Authentication Required"]
        [:p "Please log in to access this page"]]

       (and required-perm can? (not (can? required-perm)))
       [:div {:class "flex flex-col items-center justify-center h-64 text-red-500"}
        [:h2 {:class "text-2xl font-bold mb-2"} "403"]
        [:p "You do not have permission to view this page"]]

       :else
       ;; Pass route params to the page component if available
       (if (and params (seq params))
         [Page params]
         [Page]))]))

;; --- Overlay Wrapper Component ---

(defn overlay-wrapper [comp error-boundary]
  [error-boundary
   [comp]])

;; --- Router Initialization ---

(defn- init-router-once! [router-setup! homepage structure]
  (when (and router-setup! (not @router-initialized?))
    (let [home-path (resolve-homepage homepage structure)]
      (router-setup! home-path))
    (reset! router-initialized? true)))

;; --- Main Layout Component ---

(defn app-layout [name subtitle logo overlays login-modal can? user-atom trail-data trail-actions sidebar-ui sidebar-toggle! sidebar-collapsed? user-data user-actions head-inject! error-boundary homepage structure router-setup! current-route-atom]
  ;; Run side effects only once
  (init-router-once! router-setup! homepage structure)
  (inject-head-once! head-inject!)
  (init-breadcrumb-once! trail-actions)

  ;; Compute homepage path for header link
  (let [homepage-path (resolve-homepage homepage structure)]
    ;; Render the layout
    [:div {:class "min-h-screen text-slate-600"}
     ;; Header (always visible, full width)
     [error-boundary
      [header name subtitle logo user-data user-actions sidebar-toggle! homepage-path]]

     ;; Render login modal explicitly (if available)
     (when login-modal 
       [error-boundary [login-modal]])

     ;; Main layout
     [:div {:class "flex pt-16 min-h-screen"}
      ;; Sidebar (Injected)
      (when sidebar-ui 
        [error-boundary [sidebar-ui]])

      [error-boundary
       [main-content current-route-atom can? user-atom trail-data trail-actions sidebar-collapsed?]]]

     ;; Global Overlays
     (when (seq overlays)
       [:div {:class "plin-overlays"}
        (doall
         (for [[idx comp] (map-indexed vector overlays)]
           ^{:key (str "overlay-" idx)}
           [overlay-wrapper comp error-boundary]))])]))
