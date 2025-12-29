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

(defn- derive-stack [tree current-path]
  (let [virtual-root {:id :root :label "Home" :children tree :full-route "/"}]
    ;; If we're at root or empty path, just return the root
    (if (or (= current-path "/") (str/blank? current-path))
      [virtual-root]
      (loop [stack [virtual-root]
             current-node virtual-root]
        (let [children (:children current-node)
              ;; Find a child that matches the current path prefix
              match (some (fn [child]
                            (when (and (:full-route child)
                                       (not= (:full-route child) "/")
                                       (not (str/blank? (:full-route child)))
                                       (match-prefix? current-path (:full-route child)))
                              child))
                          children)]
          (if (and match (:children match))
            ;; If we found a matching child that acts as a container (has children), drill down
            (recur (conj stack match) match)
            ;; Else stop
            stack))))))

;; --- Components ---

(defn icon-chevron-right []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4 ml-auto opacity-50 transition-transform group-hover:translate-x-1" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]])

(defn icon-arrow-left []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-4 w-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 19l-7-7m0 0l7-7m-7 7h18"}]])

(defn icon-home []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"}]])

(defn sidebar-item [item active?]
  (let [base-classes "flex items-center gap-3 px-3 py-2.5 rounded-xl group transition-all cursor-pointer mb-1"
        active-classes "bg-blue-50 text-ec-blue shadow-sm ring-1 ring-blue-100"
        inactive-classes "text-slate-600 hover:bg-slate-50"]
    [:a {:href (str "#" (:full-route item))
         :class (str base-classes " " (if active? active-classes inactive-classes))}
     (when (:icon item)
       (:icon item))
     [:span {:class "text-sm font-medium sidebar-text flex-1 truncate"} (:label item)]
     (when (:children item)
       [icon-chevron-right])]))

(defn sidebar-back-button [parent-node on-back]
  [:div {:class "px-3 py-3 border-b border-slate-200/80 bg-slate-50/50 flex-shrink-0"}
   [:button {:class "flex items-center gap-3 text-slate-500 hover:text-ec-blue transition-colors w-full group"
             :on-click (fn [e]
                         (.preventDefault e)
                         (on-back))}
    [:div {:class "p-1.5 rounded-lg bg-white border border-slate-200 group-hover:border-ec-blue/30 group-hover:shadow-sm transition-all"}
     [icon-arrow-left]]
    [:div {:class "flex flex-col items-start overflow-hidden"}
     [:span {:class "text-[10px] uppercase font-bold text-slate-400 group-hover:text-ec-blue/70 transition-colors"} "Back"]
     [:span {:class "font-semibold text-slate-700 truncate w-full text-left text-sm"} 
      (if (= (:id parent-node) :root) "Home" (:label parent-node))]]]])

(defn sidebar-component [structure]
  (let [current-hash (r/atom (normalize-hash (.-hash js/location)))]
    
    (r/create-class
     {:component-did-mount
      (fn []
        (let [handler #(reset! current-hash (normalize-hash (.-hash js/location)))]
          (.addEventListener js/window "hashchange" handler)
          (handler)))
      
      :reagent-render
      (fn []
        (let [path @current-hash
              collapsed? (:collapsed? @sidebar-state)
              stack (derive-stack structure path)
              current-node (peek stack)
              items (:children current-node)
              is-root? (= (:id current-node) :root)
              
              ;; Parent is the node before current in the stack (for back button label)
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
              grouped-items (partition-by :section items)]
          
          ;; Sidebar: top-16 positions it below the header, h-[calc(100vh-4rem)] gives it the remaining height
          [:aside {:class (str "bg-white border-r border-slate-200 fixed top-16 h-[calc(100vh-4rem)] z-30 flex flex-col overflow-hidden transition-all duration-300 "
                               (if collapsed? "w-0 -ml-64" "w-64"))}
           
           ;; Back Button (when in submenu) - Shows parent's label
           (when (and (not is-root?) parent-node)
             [sidebar-back-button parent-node 
              (fn [] 
                (let [target-route (or (:full-route parent-node) "/")]
                  (set! (.-hash js/location) target-route)))])
           
           ;; Home link when at root
           (when is-root?
             [:div {:class "px-3 pt-4 pb-2"}
              [:a {:href "#/"
                   :class (str "flex items-center gap-3 px-3 py-2.5 rounded-xl group transition-all cursor-pointer mb-1 "
                               (if (= path "/") 
                                 "bg-blue-50 text-ec-blue shadow-sm ring-1 ring-blue-100"
                                 "text-slate-600 hover:bg-slate-50"))}
               [icon-home]
               [:span {:class "text-sm font-medium sidebar-text"} "Home"]]])
           
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
                   [sidebar-item item (= (:id item) active-id)]))]))]
           
           ;; Footer / Settings - Fixed at bottom
           [:div {:class "px-3 border-t border-slate-100 pt-3 pb-3 bg-white flex-shrink-0"}
            [:button {:class "flex items-center gap-3 px-3 py-3 w-full rounded-xl text-slate-500 hover:bg-slate-50 hover:text-ec-blue transition-all"}
             [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"}]
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"}]]
             [:span {:class "text-xs font-semibold sidebar-text"} "Settings"]]]]))})))
