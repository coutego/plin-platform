(ns plinpt.p-sidebar.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- State ---

(defonce sidebar-state (r/atom {:collapsed? false}))

;; --- Public API ---

(defn toggle-sidebar! []
  (swap! sidebar-state update :collapsed? not))

(defn sidebar-collapsed? []
  (:collapsed? @sidebar-state))

;; --- Helpers ---

(defn- normalize-hash [hash]
  (if (or (str/blank? hash) (= hash "#"))
    "/"
    (str/replace hash #"^#" "")))

(defn- match-prefix? [path prefix]
  (cond
    ;; Root path "/" only matches exactly "/"
    (= prefix "/") (= path "/")
    ;; Empty prefix matches nothing
    (str/blank? prefix) false
    ;; Exact match
    (= path prefix) true
    ;; Prefix match (ensure we match at path boundaries)
    :else (let [prefix-with-slash (if (str/ends-with? prefix "/") prefix (str prefix "/"))]
            (str/starts-with? path prefix-with-slash))))

(defn- filter-visible-children
  "Recursively filters out hidden items from the tree structure.
   An item is hidden if it has :hidden true.
   Also filters children of each node recursively.
   If an item ends up with no visible children, the :children key is removed
   so it's treated as a leaf node (normal item) rather than a submenu."
  [items]
  (->> items
       (remove :hidden)
       (mapv (fn [item]
               (if (:children item)
                 (let [visible-children (filter-visible-children (:children item))]
                   (if (seq visible-children)
                     (assoc item :children visible-children)
                     (dissoc item :children)))
                 item)))))

(defn- derive-stack [tree current-path]
  ;; Filter hidden items from the tree before processing
  (let [visible-tree (filter-visible-children tree)
        virtual-root {:id :root :label "Menu" :children visible-tree :full-route nil}]
    ;; If we're at root or empty path, just return the root
    (if (or (= current-path "/") (str/blank? current-path))
      [virtual-root]
      (loop [stack [virtual-root]
             current-node virtual-root]
        (let [children (:children current-node)
              ;; Find a child that matches the current path prefix
              match (some (fn [child]
                            (when (and (:full-route child)
                                       (not (str/blank? (:full-route child)))
                                       (match-prefix? current-path (:full-route child)))
                              child))
                          children)]
          (if (and match (:children match))
            ;; If we found a matching child that acts as a container (has children), drill down
            (recur (conj stack match) match)
            ;; Else stop
            stack))))))

(defn- group-items
  "Groups items by section.
   Items without a section come first.
   Then items are grouped by section, preserving the order of appearance of sections."
  [items]
  (let [grouped (group-by :section items)
        ;; Get sections in order of appearance
        sections (->> items
                      (map :section)
                      (distinct)
                      (remove nil?))]
    ;; Build result: items with no section first, then items for each section
    (cond-> []
      (seq (get grouped nil)) (conj (get grouped nil))
      true (into (map #(get grouped %) sections)))))

;; --- Components ---

(defn icon-chevron-right []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4 ml-auto opacity-50 transition-transform group-hover:translate-x-1" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]])

(defn icon-arrow-left []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 19l-7-7m0 0l7-7m-7 7h18"}]])

(defn sidebar-item [item active? navigate!]
  (let [base-classes "flex items-center gap-3 px-3 py-2.5 rounded-xl group transition-all cursor-pointer mb-1"
        active-classes "bg-blue-50 text-ec-blue shadow-sm ring-1 ring-blue-100"
        inactive-classes "text-slate-600 hover:bg-slate-50"]
    [:a {:href (str "#" (:full-route item))
         :on-click (fn [e]
                     (.preventDefault e)
                     (navigate! (:full-route item)))
         :class (str base-classes " " (if active? active-classes inactive-classes))}
     (when (:icon item)
       (:icon item))
     [:span {:class "text-sm font-medium sidebar-text flex-1 truncate"} (:label item)]
     (when (:children item)
       [icon-chevron-right])]))

(defn sidebar-section-header [current-node on-back]
  [:div {:class "border-b border-slate-200/80 flex-shrink-0"}
   ;; Back button row - subtle, secondary action
   [:div {:class "px-3 pt-3 pb-2"}
    [:button {:class "flex items-center gap-2 text-xs text-slate-400 hover:text-ec-blue transition-colors group"
              :on-click (fn [e]
                          (.preventDefault e)
                          (on-back))}
     [:div {:class "p-1 rounded-md group-hover:bg-slate-100 transition-all"}
      [icon-arrow-left]]
     [:span {:class "font-medium"} "Back"]]]
   
   ;; Current section header - prominent, shows where you are
   [:div {:class "px-3 pb-3 flex items-center gap-3"}
    ;; Icon with colored background (using the node's icon-color if available)
    (when (:icon current-node)
      (let [icon-color (or (:icon-color current-node) "text-slate-600 bg-slate-100")]
        [:div {:class (str "p-2 rounded-lg " icon-color)}
         (:icon current-node)]))
    ;; Section name - bold and prominent
    [:span {:class "font-bold text-slate-800 text-sm truncate"} 
     (:label current-node)]]])

(defn sidebar-component [structure current-route-atom navigate!]
  (r/create-class
   {:reagent-render
    (fn []
      (let [;; Get current path from router's current-route atom
            current-route (when (and current-route-atom (satisfies? IDeref current-route-atom))
                            @current-route-atom)
            path (or (:path current-route) 
                     (normalize-hash (.-hash js/location)))
            collapsed? (:collapsed? @sidebar-state)
            stack (derive-stack structure path)
            current-node (peek stack)
            items (:children current-node)
            is-root? (= (:id current-node) :root)
            
            ;; Parent is the node before current in the stack (for navigation target)
            parent-node (when (> (count stack) 1)
                          (peek (pop stack)))
            
            ;; Determine active item by finding the longest matching prefix
            active-item (->> items
                             (filter #(and (:full-route %)
                                           (match-prefix? path (:full-route %))))
                             (sort-by #(count (or (:full-route %) "")) >)
                             first)
            active-id (:id active-item)
            
            ;; Group items by section
            grouped-items (group-items items)]
        
        ;; Sidebar: top-16 positions it below the header, h-[calc(100vh-4rem)] gives it the remaining height
        [:aside {:class (str "bg-white border-r border-slate-200 fixed top-16 h-[calc(100vh-4rem)] z-30 flex flex-col overflow-hidden transition-all duration-300 "
                             (if collapsed? "w-0 -ml-64" "w-64"))}
         
         ;; Section Header (when in submenu) - Shows back button and current section
         (when (and (not is-root?) parent-node)
           [sidebar-section-header current-node 
            (fn [] 
              (let [target-route (or (:full-route parent-node) "/")]
                (navigate! target-route)))])
         
         ;; Navigation List - Scrollable area
         [:nav {:class "flex-1 px-3 py-4 space-y-1 overflow-y-auto scrollbar-thin"}
          (doall
           (for [[idx group] (map-indexed vector grouped-items)]
             ^{:key idx}
             [:<>
              (let [section (:section (first group))]
                (when section
                  [:div {:class "pt-4 pb-2 px-3 sidebar-text"}
                   [:span {:class "text-[10px] font-bold text-slate-400 uppercase tracking-wider"} section]]))
              
              (doall
               (for [item group]
                 ^{:key (:id item)}
                 [sidebar-item item (= (:id item) active-id) navigate!]))]))]
         
         ;; Footer / Settings - Fixed at bottom
         [:div {:class "px-3 border-t border-slate-100 pt-3 pb-3 bg-white flex-shrink-0"}
          [:button {:class "flex items-center gap-3 px-3 py-3 w-full rounded-xl text-slate-500 hover:bg-slate-50 hover:text-ec-blue transition-all"}
           [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"}]
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"}]]
           [:span {:class "text-xs font-semibold sidebar-text"} "Settings"]]]]))}))
