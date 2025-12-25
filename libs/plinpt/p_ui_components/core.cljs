(ns plinpt.p-ui-components.core
  (:require [reagent.core :as r]))

;; --- Icons ---

(defn icon-add []
  [:svg {:class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 4v16m8-8H4"}]])

(defn icon-edit []
  [:svg {:class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"}]])

(defn icon-delete []
  [:svg {:class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"}]])

(defn icon-back []
  [:svg {:class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 19l-7-7 7-7m8 14l-7-7 7-7"}]])

(defn icon-chevron-right []
  [:svg {:class "h-5 w-5 text-gray-400" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" :clip-rule "evenodd"}]])

(defn icon-menu []
  [:svg {:class "h-6 w-6" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6h16M4 12h16M4 18h16"}]])

(defn icon-x []
  [:svg {:class "h-6 w-6" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M6 18L18 6M6 6l12 12"}]])

(defn icon-chevron-double-left []
  [:svg {:class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M11 19l-7-7 7-7m8 14l-7-7 7-7"}]])

(defn icon-chevron-double-right []
  [:svg {:class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M13 5l7 7-7 7M5 5l7 7-7 7"}]])

;; --- Structural Components ---

(defn page-header [title subtitle actions]
  [:div {:class "md:flex md:items-center md:justify-between mb-6"}
   [:div {:class "flex-1 min-w-0"}
    [:h2 {:class "text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate"}
     title]
    (when subtitle
      [:p {:class "mt-1 text-sm text-gray-500"} subtitle])]
   (when (seq actions)
     [:div {:class "mt-4 flex md:mt-0 md:ml-4 space-x-3"}
      (for [[idx action] (map-indexed vector actions)]
        ^{:key idx}
        [:button {:class (str "inline-flex items-center px-4 py-2 border rounded-md shadow-sm text-sm font-medium focus:outline-none focus:ring-2 focus:ring-offset-2 "
                              (cond
                                (:danger? action) "border-transparent text-white bg-red-600 hover:bg-red-700 focus:ring-red-500"
                                (:primary? action) "border-transparent text-white bg-blue-600 hover:bg-blue-700 focus:ring-blue-500"
                                :else "border-gray-300 text-gray-700 bg-white hover:bg-gray-50 focus:ring-indigo-500"))
                  :on-click (:on-click action)}
         (when (:icon action)
           [:span {:class "mr-2 -ml-1"} [(:icon action)]])
         (:label action)])])])

(defn list-page [title subtitle actions content]
  [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
   [page-header title subtitle actions]
   [:div {:class "bg-white shadow overflow-hidden sm:rounded-md"}
    content]])

(defn detail-page [title subtitle actions content]
  [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
   [page-header title subtitle actions]
   [:div {:class "bg-white shadow overflow-hidden sm:rounded-lg"}
    content]])

(defn tabs [items active-id on-change]
  [:div {:class "border-b border-gray-200 mb-6"}
   [:nav {:class "-mb-px flex space-x-8" :aria-label "Tabs"}
    (for [[idx item] (map-indexed vector items)]
      ^{:key idx}
      [:button
       {:class (str "whitespace-nowrap py-2 px-1 border-b-2 font-medium text-sm focus:outline-none "
                    (if (= active-id (:id item))
                      "border-blue-500 text-blue-600"
                      "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"))
        :on-click #(on-change (:id item))}
       (:label item)])]])

(defn data-table [columns data on-row-click]
  [:div {:class "flex flex-col"}
   [:div {:class "-my-2 overflow-x-auto sm:-mx-6 lg:-mx-8"}
    [:div {:class "py-2 align-middle inline-block min-w-full sm:px-6 lg:px-8"}
     [:div {:class "shadow overflow-hidden border-b border-gray-200 sm:rounded-lg"}
      [:table {:class "min-w-full divide-y divide-gray-200"}
       [:thead {:class "bg-gray-50"}
        [:tr
         (for [[idx col] (map-indexed vector columns)]
           ^{:key idx}
           [:th {:scope "col" :class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"}
            (:label col)])
         (when on-row-click
           [:th {:scope "col" :class "relative px-6 py-3"}
            [:span {:class "sr-only"} "View"]])]]
       [:tbody {:class "bg-white divide-y divide-gray-200"}
        (if (empty? data)
          [:tr
           [:td {:col-span (if on-row-click (inc (count columns)) (count columns))
                 :class "px-6 py-4 text-center text-sm text-gray-500 italic"}
            "No data available."]]
          (for [[row-idx row] (map-indexed vector data)]
            ^{:key row-idx}
            [:tr {:class (if on-row-click "hover:bg-gray-50 cursor-pointer" "")
                  :on-click #(when on-row-click (on-row-click row))}
             (for [[col-idx col] (map-indexed vector columns)]
               ^{:key col-idx}
               [:td {:class "px-6 py-4 whitespace-nowrap text-sm text-gray-900"}
                (if (:render col)
                  ((:render col) row)
                  (get row (:key col)))])
             (when on-row-click
               [:td {:class "px-6 py-4 whitespace-nowrap text-right text-sm font-medium"}
                [icon-chevron-right]])]))]]]]]])

(defn description-list [items]
  [:div {:class "border-t border-gray-200"}
   [:dl
    (for [[idx item] (map-indexed vector items)]
      ^{:key idx}
      [:div {:class (str "px-4 py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6 "
                         (if (even? idx) "bg-gray-50" "bg-white"))}
       [:dt {:class "text-sm font-medium text-gray-500"} (:label item)]
       [:dd {:class "mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2"} (:value item)]])]])

;; --- Sidebar Components ---

(defn sidebar-nav-item [item active-item collapsed? on-select]
  [:button
   {:class (str "w-full flex items-center px-3 py-2 text-sm font-medium rounded-md group transition-colors duration-150 "
                (if (= (:id active-item) (:id item))
                  "bg-blue-50 text-blue-700 border border-blue-100 shadow-sm"
                  "text-gray-700 hover:bg-gray-50 hover:text-gray-900 border border-transparent")
                (if collapsed? " justify-center px-2" ""))
    :title (:label item)
    :on-click #(on-select item)}
   (if collapsed?
     [:span {:class (str "h-8 w-8 flex items-center justify-center rounded-full text-sm font-bold "
                         (if (= (:id active-item) (:id item))
                           "bg-blue-100 text-blue-700"
                           "bg-gray-200 text-gray-600"))}
      (subs (:label item) 0 1)]
     [:div {:class "text-left"}
      [:div {:class "font-semibold truncate"} (:label item)]
      (when (:description item)
        [:div {:class (str "text-xs mt-0.5 truncate "
                           (if (= (:id active-item) (:id item))
                             "text-blue-500"
                             "text-gray-500"))}
         (:description item)])])])

(defn mobile-sidebar [title icon items active-item on-select mobile-open? set-mobile-open!]
  (when mobile-open?
    [:div {:class "fixed inset-0 z-40 flex md:hidden"}
     [:div {:class "fixed inset-0 bg-gray-600 bg-opacity-75"
            :on-click #(set-mobile-open! false)}]
     [:div {:class "relative flex-1 flex flex-col max-w-xs w-full bg-white"}
      [:div {:class "absolute top-0 right-0 -mr-12 pt-2"}
       [:button {:class "ml-1 flex items-center justify-center h-10 w-10 rounded-full focus:outline-none focus:ring-2 focus:ring-inset focus:ring-white"
                 :on-click #(set-mobile-open! false)}
        [:span {:class "sr-only"} "Close sidebar"]
        [:div {:class "text-white"} [icon-x]]]]
      ;; Sidebar Content (Mobile)
      [:div {:class "flex-1 h-0 pt-5 pb-4 overflow-y-auto"}
       [:div {:class "flex-shrink-0 flex items-center px-4 space-x-3"}
        [:div {:class "text-gray-500"} icon]
        [:h1 {:class "text-xl font-bold text-gray-900"} title]]
       [:nav {:class "mt-5 px-4 space-y-2"}
        (for [item items]
          ^{:key (:id item)}
          [sidebar-nav-item item active-item false #(do (on-select item) (set-mobile-open! false))])]]]
     [:div {:class "flex-shrink-0 w-14"}]]))

(defn collapse-toggle [collapsed? on-toggle]
  [:div {:class "flex-shrink-0 flex border-t border-gray-200 p-4"}
   [:button {:class "flex-shrink-0 w-full group block focus:outline-none"
             :on-click on-toggle}
    [:div {:class (str "flex items-center" (if collapsed? " justify-center" ""))}
     [:div {:class "text-gray-400 group-hover:text-gray-600 transition-colors"}
      (if collapsed? [icon-chevron-double-right] [icon-chevron-double-left])]
     (when-not collapsed?
       [:div {:class "ml-3"}
        [:p {:class "text-sm font-medium text-gray-500 group-hover:text-gray-700 transition-colors"} "Collapse"]])]]])

(defn desktop-sidebar [title icon items active-item on-select collapsed? set-collapsed!]
  [:div {:class (str "hidden md:flex md:flex-col bg-white shadow-md flex-shrink-0 transition-all duration-300 ease-in-out h-full "
                     (if collapsed? "w-16" "w-72"))}
   [:div {:class "flex items-center flex-shrink-0 px-6 h-20 border-b border-gray-200"}
    [:div {:class "text-gray-500"} icon]
    (when-not collapsed?
      [:h1 {:class "ml-3 text-xl font-bold text-gray-900 truncate"} title])]

   [:div {:class "flex-1 flex flex-col overflow-y-auto"}
    [:nav {:class "flex-1 px-4 py-6 space-y-2"}
     (for [item items]
       ^{:key (:id item)}
       [sidebar-nav-item item active-item collapsed? on-select])]]

   [collapse-toggle collapsed? #(set-collapsed! (not collapsed?))]])

(defn mobile-header [title icon set-mobile-open!]
  [:div {:class "md:hidden pl-1 pt-1 sm:pl-3 sm:pt-3 bg-white border-b border-gray-200 flex items-center h-16 flex-shrink-0"}
   [:button {:class "-ml-0.5 -mt-0.5 h-12 w-12 inline-flex items-center justify-center rounded-md text-gray-500 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
             :on-click #(set-mobile-open! true)}
    [:span {:class "sr-only"} "Open sidebar"]
    [icon-menu]]
   [:div {:class "ml-3 flex items-center"}
    [:div {:class "text-gray-500 mr-2"} icon]
    [:h1 {:class "text-lg font-bold text-gray-900"} title]]])

(defn main-content [title icon active-item render-content set-mobile-open!]
  [:div {:class "flex flex-col flex-1 overflow-hidden h-full"}
   [mobile-header title icon set-mobile-open!]

   [:main {:class "flex-1 relative z-0 overflow-y-auto focus:outline-none"}
    (if active-item
      (render-content active-item)
      [:div {:class "flex flex-col items-center justify-center h-full text-gray-500"}
       [:div {:class "mb-4 opacity-50"} icon]
       [:p "Select an item from the sidebar to get started"]])]])

(defn sidebar-page
  "A responsive sidebar layout component.
   Arguments:
   - title: String, title of the application/section.
   - icon: Reagent component, icon displayed next to title.
   - items: Collection of maps with :id, :label, :description.
   - active-item: The currently selected item map.
   - on-select: Function called with the selected item when clicked.
   - render-content: Function that takes the active item and returns the content component."
  [title icon items active-item on-select render-content]
  (let [mobile-open? (r/atom false)
        desktop-collapsed? (r/atom false)]
    (fn [title icon items active-item on-select render-content]
      [:div {:class "flex h-full overflow-hidden bg-gray-100"}
       [mobile-sidebar title icon items active-item on-select @mobile-open? #(reset! mobile-open? %)]
       [desktop-sidebar title icon items active-item on-select @desktop-collapsed? #(reset! desktop-collapsed? %)]
       [main-content title icon active-item render-content #(reset! mobile-open? %)]])))
