(ns plinpt.i-application
  (:require [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-router :as irouter]))

;; --- Logic: Tree Builder ---

(defn- join-path [parent-path segment]
  (cond
    (or (nil? segment) (str/blank? segment)) parent-path
    (str/starts-with? segment "/") segment ;; Absolute override
    (= parent-path "/") (str "/" segment)
    (str/ends-with? parent-path "/") (str parent-path segment)
    :else (str parent-path "/" segment)))

(defn- build-node [item all-items-by-parent parent-path]
  (let [id (:id item)
        ;; Calculate the full route for this node
        full-route (join-path parent-path (:route item))
        ;; Find children for this ID
        children (get all-items-by-parent id)
        ;; Recursively build children
        processed-children (->> children
                                (sort-by :order)
                                (mapv #(build-node % all-items-by-parent full-route)))]
    (cond-> (assoc item :full-route full-route)
      (seq processed-children) (assoc :children processed-children))))

(defn build-application-tree [flat-items]
  (let [;; Filter out any non-map items (safety check)
        valid-items (filter map? flat-items)
        ;; Group all items by their parent-id for O(1) lookup
        by-parent (group-by :parent-id valid-items)
        ;; Roots are items with nil parent-id
        roots (get by-parent nil)
        result (->> roots
                    (sort-by :order)
                    (mapv #(build-node % by-parent "")))]
    result))

;; --- Default Parent Page ---

(defn default-parent-page [node]
  (let [;; Filter out hidden children
        visible-children (filter #(not (:hidden %)) (:children node))]
    [:div {:class "p-8"}
     [:h1 {:class "text-2xl font-bold text-slate-900 mb-2"} (:label node)]
     [:p {:class "text-slate-500 mb-8"} "Select an item from the list below:"]
     
     [:div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
      (for [child visible-children]
        ^{:key (:id child)}
        [:a {:href (str "#" (:full-route child))
             :class "block p-6 bg-white border border-slate-200 rounded-xl hover:border-blue-500 hover:shadow-md transition-all group"}
         [:div {:class "flex items-center gap-4 mb-2"}
          (when (:icon child)
            (let [color-class (or (:icon-color child) "text-slate-500 bg-slate-50 group-hover:text-blue-600 group-hover:bg-blue-50")]
              [:div {:class (str "p-2 rounded-lg transition-colors " color-class)}
               (:icon child)]))
          [:h3 {:class "font-semibold text-slate-900 group-hover:text-blue-600 transition-colors"} 
           (:label child)]]
         (when (:description child)
           [:p {:class "text-sm text-slate-500"} (:description child)])])]]))

;; --- Logic: Extract Routes from Tree ---

(defn- extract-routes-from-node
  "Recursively extracts route definitions from a nav-item node.
   Only creates routes for items that have a :component defined,
   OR items that have children (auto-generating a default parent page)."
  [node]
  (let [;; Determine component: explicit > default parent page > nil
        component (or (:component node)
                      (when (seq (:children node))
                        (fn [] [default-parent-page node])))
        
        ;; Create route for this node if we have a component
        this-route (when component
                     {:path (:full-route node)
                      :component component
                      :label (:label node)
                      :layout (:layout node)
                      :required-perm (:required-perm node)})
        
        ;; Recursively get routes from children
        child-routes (when (:children node)
                       (mapcat extract-routes-from-node (:children node)))]
    (if this-route
      (cons this-route child-routes)
      child-routes)))

(defn extract-routes-from-tree
  "Extracts all route definitions from the application tree.
   Returns a vector of route maps for items that have :component defined."
  [tree]
  (vec (mapcat extract-routes-from-node tree)))

;; --- Default Icon ---

(defn icon-puzzle-piece []
  [:svg {:width "200" :height "200" :viewBox "-2 -2 204 204" :xmlns "http://www.w3.org/2000/svg" :class "h-8 w-8"}
   ;; Piece 1: Top Left (Red) - Was Top Right
   [:path {:d "M 0,0 L 100,0 L 100,38.82 A 15 15 0 1 1 100 61.18 L 100,100 L 61.18,100 A 15 15 0 1 0 38.82 100 L 0,100 Z"
           :fill "#fadbd8" :stroke "#c0392b" :stroke-width "4" :stroke-linejoin "round"}]
   ;; Piece 2: Bottom Left (Blue) - Was Top Left
   [:path {:d "M 0,100 L 38.82,100 A 15 15 0 1 1 61.18 100 L 100,100 L 100,138.82 A 15 15 0 1 0 100 161.18 L 100,200 L 0,200 Z"
           :fill "#d6eaf8" :stroke "#2980b9" :stroke-width "4" :stroke-linejoin "round"}]
   ;; Piece 3: Bottom Right (Green) - Was Bottom Left
   [:path {:d "M 100,200 L 100,161.18 A 15 15 0 1 1 100 138.82 L 100,100 L 138.82,100 A 15 15 0 1 0 161.18 100 L 200,100 L 200,200 Z"
           :fill "#d5f5e3" :stroke "#27ae60" :stroke-width "4" :stroke-linejoin "round"}]])

(defn icon-home []
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"}]])

;; --- Default Home Page ---

(defn default-home-page []
  [:div {:class "p-10"}
   [:h1 {:class "text-3xl font-bold text-slate-800"} "Welcome"]
   [:p {:class "mt-4 text-slate-600"} "This is the default home page of the application."]
   [:p {:class "mt-2 text-sm text-slate-500"} "To customize this page, contribute to " [:code "::iapp/homepage"] " in your plugin."]])

;; --- Default UI Placeholder ---

(defn default-ui []
  [:div {:class "p-10"}
   [:h1 {:class "text-3xl font-bold text-slate-800"} "PLIN Application"]
   [:p {:class "mt-4 text-slate-600"} "No UI shell loaded. Please include an app shell plugin (e.g., p-app-shell)."]])

;; --- Plugin Definition ---

(def plugin
  (plin/plugin
   {:doc "Core Application Structure. Defines the navigation hierarchy and shell components."
    :deps [idev/plugin irouter/plugin]

    :contributions
    {::idev/plugins [{:id :i-application
                      :description "Application Backbone."
                      :responsibilities "Defines the navigation extension point and shell components. Routes are auto-extracted from nav-items."
                      :type :infrastructure}]
     
     ;; Contribute the homepage bean to the nav-items list.
     ;; This ensures that whatever ::homepage resolves to (default or override) appears in the sidebar.
     ::nav-items [::homepage]}

    :extensions
    [{:key ::nav-items
      :doc "List of raw navigation items.
            Map keys:
            - :id (keyword) - Required. Unique identifier.
            - :parent-id (keyword, optional) - Parent item for nesting.
            - :label (string) - Display label.
            - :description (string, optional) - Description for parent page cards.
            - :route (string) - Relative URL segment.
            - :icon (hiccup) - Icon component.
            - :icon-color (string, optional) - Tailwind classes for icon container (e.g. 'text-blue-600 bg-blue-50').
            - :section (string) - Grouping header.
            - :order (number) - Sort order.
            - :component (reagent component, optional) - If provided, a route is auto-created.
            - :layout (keyword, optional) - :default or :full-screen.
            - :required-perm (keyword, optional) - Permission required to access.
            - :hidden (boolean, optional) - If true, hide from sidebar and auto-generated parent pages."
      :handler (plin/collect-all ::nav-items)}
     
     {:key ::header-components
      :doc "Components to be rendered in the global header."
      :handler (plin/collect-all ::header-components)}
     
     {:key ::overlay-components
      :doc "Components to be rendered as global overlays (e.g. notifications, modals)."
      :handler (plin/collect-all ::overlay-components)}
     
     {:key ::name
      :doc "Name of the application."
      :handler (plin/collect-last ::name)}
     
     {:key ::short-description
      :doc "Short description (15-35 chars) of the application."
      :handler (plin/collect-last ::short-description)}
     
     {:key ::logo
      :doc "Application Logo component."
      :handler (plin/collect-last ::logo)}
     
     {:key ::homepage
      :doc "The navigation item that serves as the application homepage.
            Defaults to a standard 'Home' item.
            Plugins can override this to replace the home item entirely."
      :handler (plin/collect-last ::homepage)}
     
     {:key ::ui
      :doc "The main application UI component (root component).
            This is what gets mounted to the DOM by p-client-boot.
            Override this in shell plugins (e.g., p-app-shell)."
      :handler (plin/collect-last ::ui)}]

    :beans
    {::structure
     ^{:doc "The calculated Application Tree.
             Returns a vector of root nodes. Each node contains its original data plus:
             - :full-route (string) - The calculated absolute URL
             - :children (vector) - Nested child nodes"
       :api {:ret :vector}}
     [build-application-tree ::nav-items]
     
     ::nav-routes
     ^{:doc "Routes auto-generated from nav-items that have :component defined."
       :api {:ret :vector}}
     [extract-routes-from-tree ::structure]
     
     ::name 
     ^{:doc "Application name."
       :api {:ret :string}}
     [:= "PLIN Demo"]
     
     ::short-description 
     ^{:doc "Application short description."
       :api {:ret :string}}
     [:= "Demo Application for the PLIN Platform"]
     
     ::logo
     ^{:doc "Default Application Logo."
       :reagent-component true
       :api {:ret :hiccup}}
     [:= icon-puzzle-piece]
     
     ::default-home-item
     ^{:doc "Default Home Navigation Item."
       :api {:ret :map}}
     [:= {:id :home
          :label "Home"
          :route "/"
          :icon [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                 [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"}]]
          :icon-color "text-blue-600 bg-blue-50"
          :component default-home-page
          :order 0}]
     
     ::homepage
     ^{:doc "The application homepage item. Defaults to ::default-home-item."
       :api {:ret :map}}
     [identity ::default-home-item]
     
     ::header-components
     ^{:doc "List of header components."
       :api {:ret :vector}}
     [:= []]
     
     ::overlay-components
     ^{:doc "List of overlay components."
       :api {:ret :vector}}
     [:= []]
     
     ::ui
     ^{:doc "The main application UI component. Override in shell plugins."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= default-ui]}}))
