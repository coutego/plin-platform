(ns plinpt.i-application
  (:require [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

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
  (let [;; Group all items by their parent-id for O(1) lookup
        by-parent (group-by :parent-id flat-items)
        ;; Roots are items with nil parent-id
        roots (get by-parent nil)]
    (->> roots
         (sort-by :order)
         (mapv #(build-node % by-parent "")))))

;; --- Default Parent Page ---

(defn default-parent-page [node]
  [:div {:class "p-8"}
   [:h1 {:class "text-2xl font-bold text-slate-900 mb-2"} (:label node)]
   [:p {:class "text-slate-500 mb-8"} "Select an item from the list below:"]
   
   [:div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
    (for [child (:children node)]
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
         [:p {:class "text-sm text-slate-500"} (:description child)])])]])

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

;; --- Logic: Merge Routes ---

(defn merge-routes
  "Merges routes from nav-items (auto-generated) with explicit routes.
   Explicit routes take precedence (override) if paths conflict."
  [nav-routes explicit-routes]
  (let [explicit-by-path (into {} (map (juxt :path identity) explicit-routes))
        ;; Start with nav routes, but allow explicit routes to override
        merged (reduce (fn [acc route]
                         (let [path (:path route)]
                           (if (contains? explicit-by-path path)
                             acc ;; Skip, explicit route will be added later
                             (conj acc route))))
                       []
                       nav-routes)]
    ;; Add all explicit routes
    (into merged explicit-routes)))

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

;; --- Plugin Definition ---

(def plugin
  (plin/plugin
   {:doc "Core Application Structure. Defines the navigation hierarchy, routes, and shell components."
    :deps [idev/plugin]

    :contributions
    {::idev/plugins [{:id :i-application
                      :description "Application Backbone."
                      :responsibilities "Defines the navigation extension point, routes, and shell components."
                      :type :infrastructure}]}

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
            - :required-perm (keyword, optional) - Permission required to access."
      :handler (plin/collect-all ::nav-items)}
     
     {:key ::routes
      :doc "Explicit route definitions (in addition to auto-generated from nav-items).
            Map keys:
            - :path (string) - URL path, e.g. '/info-systems/:id'
            - :component (reagent component) - Component to render.
            - :label (string, optional) - Route label.
            - :layout (keyword, optional) - :default or :full-screen.
            - :required-perm (keyword, optional) - Permission required."
      :handler (plin/collect-all ::routes)}
     
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
      :handler (plin/collect-last ::logo)}]

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
     
     ::all-routes
     ^{:doc "All routes: merged from nav-items (auto) and explicit route contributions.
             Explicit routes override nav-item routes if paths conflict."
       :api {:ret :vector}}
     [merge-routes ::nav-routes ::routes]
     
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
     
     ::routes
     ^{:doc "Explicit routes (before merge)."
       :api {:ret :vector}}
     [:= []]
     
     ::header-components
     ^{:doc "List of header components."
       :api {:ret :vector}}
     [:= []]
     
     ::overlay-components
     ^{:doc "List of overlay components."
       :api {:ret :vector}}
     [:= []]}}))
