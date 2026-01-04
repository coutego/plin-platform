(ns plinpt.p-debug.plugins-ui
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [clojure.set :as set]
            [plin.boot :as boot]
            [plinpt.p-debug.common :as common]))

(defn- get-dep-id [dep]
  (if (keyword? dep) dep (:id dep)))

(defn- get-dependents [target-id all-plugins]
  "Returns set of plugin IDs that depend on target-id (directly or transitively)"
  (let [direct-dependents (filter (fn [p]
                                    (some #(= (get-dep-id %) target-id) (:deps p)))
                                  all-plugins)
        direct-ids (map :id direct-dependents)]
    (loop [ids (set direct-ids)
           checked #{}]
      (let [unchecked (set/difference ids checked)]
        (if (empty? unchecked)
          ids
          (let [next-id (first unchecked)
                next-dependents (filter (fn [p]
                                          (some #(= (get-dep-id %) next-id) (:deps p)))
                                        all-plugins)
                next-ids (map :id next-dependents)]
            (recur (into ids next-ids) (conj checked next-id))))))))

(defn- get-direct-dependents [target-id all-plugins]
  "Returns set of plugin IDs that directly depend on target-id"
  (->> all-plugins
       (filter (fn [p] (some #(= (get-dep-id %) target-id) (:deps p))))
       (map :id)
       set))

(defn- count-beans [plugin]
  (count (keys (:beans plugin))))

(defn- count-extensions [plugin]
  (count (:extensions plugin)))

(defn- safe-count [x]
  "Safely count items - returns 1 for non-countable items like keywords"
  (cond
    (nil? x) 0
    (keyword? x) 1
    (string? x) 1
    (coll? x) (count x)
    :else 1))

(defn- count-contributions [plugin]
  (reduce + (map safe-count (vals (:contributions plugin)))))

;; --- Icons ---

(defn- icon-chevron-up []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M5 15l7-7 7 7"}]])

(defn- icon-chevron-down []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19 9l-7 7-7-7"}]])

(defn- icon-arrow-right []
  [:svg {:class "w-3 h-3" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M14 5l7 7m0 0l-7 7m7-7H3"}]])

(defn- icon-arrow-left []
  [:svg {:class "w-3 h-3" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 19l-7-7m0 0l7-7m-7 7h18"}]])

(defn- icon-cube []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"}]])

(defn- icon-puzzle []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z"}]])

(defn- icon-link []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"}]])

;; --- Stat Badge ---

(defn- stat-badge [label count color-class]
  (when (pos? count)
    [:span {:class (str "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium " color-class)}
     label
     [:span {:class "font-bold"} count]]))

;; --- Dependency Chip ---

(defn- dep-chip [id format-id disabled? on-click]
  [:button {:class (str "inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-mono transition-colors "
                        (if disabled?
                          "bg-gray-100 text-gray-400 line-through"
                          "bg-blue-50 text-blue-700 hover:bg-blue-100"))
            :on-click on-click
            :title (str id)}
   (format-id id)])

;; --- Plugin ID Encoding ---
;; We use a custom encoding to avoid issues with URL-encoded slashes in path segments
;; Replace "/" with "~" for the URL, then decode back when reading

(defn encode-plugin-id
  "Encode a plugin ID for use in URLs. Replaces / with ~ to avoid routing issues."
  [id]
  (let [id-str (if (keyword? id)
                 (subs (str id) 1)  ;; Remove leading colon from keyword
                 (str id))]
    (str/replace id-str "/" "~")))

(defn decode-plugin-id
  "Decode a plugin ID from URL format. Replaces ~ back to /."
  [encoded-str]
  (when encoded-str
    (str/replace encoded-str "~" "/")))

;; --- Plugin Card (Collapsed) ---

(defn- plugin-card-collapsed [plugin format-id cascading disabled-ids toggle-fn on-expand navigate-to-detail]
  (let [id (:id plugin)
        manually-disabled? (contains? disabled-ids id)
        effectively-disabled? (contains? cascading id)
        is-system? (= id :system-api)
        beans-count (count-beans plugin)
        extensions-count (count-extensions plugin)
        contributions-count (count-contributions plugin)
        deps-count (count (:deps plugin))]
    [:div {:class (str "group relative bg-white rounded-xl border transition-all duration-200 overflow-hidden "
                       (if effectively-disabled?
                         "border-gray-200 bg-gray-50"
                         "border-gray-200 hover:border-blue-300 hover:shadow-md"))}
     ;; Status indicator bar
     [:div {:class (str "absolute left-0 top-0 bottom-0 w-1 "
                        (cond
                          effectively-disabled? "bg-gray-300"
                          is-system? "bg-blue-500"
                          :else "bg-green-500"))}]
     
     [:div {:class "pl-4 pr-3 py-3"}
      [:div {:class "flex items-start justify-between gap-3"}
       ;; Left side: info
       [:div {:class "flex-1 min-w-0"}
        [:div {:class "flex items-center gap-2 flex-wrap"}
         ;; Clickable title with expand/collapse arrow
         [:button {:class "flex items-center gap-1 hover:text-blue-600 transition-colors cursor-pointer"
                   :on-click on-expand
                   :title "Click to expand"}
          [:span {:class (str "font-mono font-semibold text-sm "
                              (if effectively-disabled? "text-gray-400 line-through" "text-gray-900"))
                  :title (str id)}
           (format-id id)]
          [:span {:class "text-gray-400"}
           [icon-chevron-down]]]
         
         (when is-system?
           [:span {:class "px-1.5 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-700"} "System"])
         
         (when (and effectively-disabled? (not manually-disabled?))
           [:span {:class "px-1.5 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-700"} "Cascade"])]
        
        ;; Doc - more space now (line-clamp-2 instead of line-clamp-1)
        (when (:doc plugin)
          [:p {:class (str "text-sm mt-1 line-clamp-2 "
                           (if effectively-disabled? "text-gray-400" "text-gray-600"))}
           (:doc plugin)])
        
        ;; Stats row
        [:div {:class "flex items-center gap-2 mt-2 flex-wrap"}
         (when (pos? deps-count)
           [stat-badge "deps" deps-count "bg-purple-50 text-purple-700"])
         (when (pos? beans-count)
           [stat-badge "beans" beans-count "bg-emerald-50 text-emerald-700"])
         (when (pos? extensions-count)
           [stat-badge "ext" extensions-count "bg-amber-50 text-amber-700"])
         (when (pos? contributions-count)
           [stat-badge "contrib" contributions-count "bg-cyan-50 text-cyan-700"])]]
       
       ;; Right side: actions
       [:div {:class "flex items-center gap-2 flex-shrink-0"}
        ;; Enable/Disable button
        (when-not is-system?
          [:button {:class (str "px-3 py-1.5 rounded-lg text-xs font-medium transition-colors "
                                (if effectively-disabled?
                                  "bg-green-100 text-green-700 hover:bg-green-200"
                                  "bg-red-100 text-red-700 hover:bg-red-200"))
                    :on-click #(toggle-fn id manually-disabled?)}
           (if effectively-disabled? "Enable" "Disable")])
        
        ;; Detail page button
        [:button {:class "px-3 py-1.5 rounded-lg text-xs font-medium bg-blue-100 text-blue-700 hover:bg-blue-200 transition-colors"
                  :on-click #(navigate-to-detail id)}
         "Details"]]]]]))

;; --- Plugin Card (Expanded) ---

(defn- plugin-card-expanded [plugin format-id all-plugins cascading disabled-ids toggle-fn on-collapse navigate-to-detail]
  (let [id (:id plugin)
        manually-disabled? (contains? disabled-ids id)
        effectively-disabled? (contains? cascading id)
        is-system? (= id :system-api)
        deps (map get-dep-id (:deps plugin))
        dependents (get-direct-dependents id all-plugins)
        beans (:beans plugin)
        extensions (:extensions plugin)
        contributions (:contributions plugin)]
    [:div {:class (str "bg-white rounded-xl border-2 shadow-lg overflow-hidden "
                       (if effectively-disabled?
                         "border-gray-300"
                         "border-blue-400"))}
     ;; Header
     [:div {:class (str "px-4 py-3 border-b "
                        (if effectively-disabled?
                          "bg-gray-100 border-gray-200"
                          "bg-gradient-to-r from-blue-50 to-indigo-50 border-blue-200"))}
      [:div {:class "flex items-center justify-between"}
       [:div {:class "flex items-center gap-3"}
        [:div
         [:div {:class "flex items-center gap-2"}
          ;; Clickable title with collapse arrow
          [:button {:class "flex items-center gap-1 hover:text-blue-600 transition-colors cursor-pointer"
                    :on-click on-collapse
                    :title "Click to collapse"}
           [:span {:class (str "font-mono font-bold text-lg "
                               (if effectively-disabled? "text-gray-400 line-through" "text-gray-900"))}
            (format-id id)]
           [:span {:class "text-gray-400"}
            [icon-chevron-up]]]
          (when is-system?
            [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-700"} "System"])
          (when effectively-disabled?
            [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700"} "Disabled"])]
         [:div {:class "text-xs text-gray-500 font-mono mt-0.5"} (str id)]]]
       
       [:div {:class "flex items-center gap-2"}
        ;; Enable/Disable button
        (when-not is-system?
          [:button {:class (str "px-3 py-1.5 rounded-lg text-xs font-medium transition-colors "
                                (if effectively-disabled?
                                  "bg-green-500 text-white hover:bg-green-600"
                                  "bg-red-500 text-white hover:bg-red-600"))
                    :on-click #(toggle-fn id manually-disabled?)}
           (if effectively-disabled? "Enable" "Disable")])
        
        ;; Details button
        [:button {:class "px-3 py-1.5 rounded-lg text-xs font-medium bg-blue-600 text-white hover:bg-blue-700 transition-colors"
                  :on-click #(navigate-to-detail id)}
         "Details"]]]]
     
     ;; Content
     [:div {:class "p-4 space-y-4"}
      ;; Documentation
      (when (:doc plugin)
        [:div {:class "text-sm text-gray-700 bg-gray-50 rounded-lg p-3 border border-gray-200"}
         (:doc plugin)])
      
      ;; Dependencies Section
      [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
       ;; Depends On
       [:div {:class "bg-purple-50 rounded-lg p-3 border border-purple-200"}
        [:div {:class "flex items-center gap-2 text-purple-800 font-medium text-sm mb-2"}
         [icon-arrow-right]
         [:span "Depends On"]
         [:span {:class "text-purple-600"} (str "(" (count deps) ")")]]
        (if (seq deps)
          [:div {:class "flex flex-wrap gap-1"}
           (for [dep-id deps]
             ^{:key dep-id}
             [dep-chip dep-id format-id (contains? cascading dep-id) nil])]
          [:span {:class "text-sm text-purple-600 italic"} "No dependencies"])]
       
       ;; Depended By
       [:div {:class "bg-indigo-50 rounded-lg p-3 border border-indigo-200"}
        [:div {:class "flex items-center gap-2 text-indigo-800 font-medium text-sm mb-2"}
         [icon-arrow-left]
         [:span "Depended By"]
         [:span {:class "text-indigo-600"} (str "(" (count dependents) ")")]]
        (if (seq dependents)
          [:div {:class "flex flex-wrap gap-1"}
           (for [dep-id dependents]
             ^{:key dep-id}
             [dep-chip dep-id format-id (contains? cascading dep-id) nil])]
          [:span {:class "text-sm text-indigo-600 italic"} "No dependents"])]]
      
      ;; Beans Section
      (when (seq beans)
        [:div {:class "bg-emerald-50 rounded-lg p-3 border border-emerald-200"}
         [:div {:class "flex items-center gap-2 text-emerald-800 font-medium text-sm mb-2"}
          [icon-cube]
          [:span "Beans"]
          [:span {:class "text-emerald-600"} (str "(" (count beans) ")")]]
         [:div {:class "flex flex-wrap gap-1"}
          (for [[bean-key _] beans]
            ^{:key bean-key}
            [:span {:class "px-2 py-1 bg-emerald-100 text-emerald-800 rounded text-xs font-mono"
                    :title (str bean-key)}
             (name bean-key)])]])
      
      ;; Extensions Section
      (when (seq extensions)
        [:div {:class "bg-amber-50 rounded-lg p-3 border border-amber-200"}
         [:div {:class "flex items-center gap-2 text-amber-800 font-medium text-sm mb-2"}
          [icon-puzzle]
          [:span "Extension Points"]
          [:span {:class "text-amber-600"} (str "(" (count extensions) ")")]]
         [:div {:class "space-y-1"}
          (for [ext extensions]
            ^{:key (:key ext)}
            [:div {:class "text-xs"}
             [:span {:class "font-mono text-amber-800 bg-amber-100 px-1.5 py-0.5 rounded"} (str (:key ext))]
             (when (:doc ext)
               [:span {:class "text-amber-700 ml-2"} (:doc ext)])])]])
      
      ;; Contributions Section
      (when (seq contributions)
        [:div {:class "bg-cyan-50 rounded-lg p-3 border border-cyan-200"}
         [:div {:class "flex items-center gap-2 text-cyan-800 font-medium text-sm mb-2"}
          [icon-link]
          [:span "Contributions"]
          [:span {:class "text-cyan-600"} (str "(" (count-contributions plugin) ")")]]
         [:div {:class "space-y-2"}
          (for [[ext-key values] contributions]
            ^{:key ext-key}
            [:div
             [:span {:class "font-mono text-cyan-800 text-xs bg-cyan-100 px-1.5 py-0.5 rounded"} (str ext-key)]
             [:span {:class "text-cyan-600 text-xs ml-2"} (str (safe-count values) " item(s)")]])]])]]))

;; --- Plugin Card Wrapper ---

(defn- plugin-card [plugin format-id all-plugins cascading disabled-ids toggle-fn expanded-ids navigate-to-detail]
  (let [id (:id plugin)
        expanded? (contains? @expanded-ids id)]
    (if expanded?
      [plugin-card-expanded plugin format-id all-plugins cascading disabled-ids toggle-fn
       #(swap! expanded-ids disj id) navigate-to-detail]
      [plugin-card-collapsed plugin format-id cascading disabled-ids toggle-fn
       #(swap! expanded-ids conj id) navigate-to-detail])))

;; --- Main View ---

(defonce ui-state (r/atom {:filter-text ""
                           :show-disabled-only false
                           :sort-by :id}))

(defonce expanded-ids (r/atom #{}))

(defn main-view [api]
  (fn [api]
    (let [{:keys [state enable-plugin-no-reload! disable-plugin-no-reload! reload!]} api
          {:keys [all-plugins disabled-ids]} @state
          cascading (boot/get-cascading-disabled all-plugins disabled-ids)
          
          {:keys [filter-text show-disabled-only sort-by]} @ui-state
          
          format-id (common/make-id-formatter (map :id all-plugins))
          
          ;; Filter plugins
          filtered-plugins (->> all-plugins
                                (filter (fn [p]
                                          (let [matches-text (or (str/blank? filter-text)
                                                                 (str/includes? (str/lower-case (str (:id p))) 
                                                                                (str/lower-case filter-text))
                                                                 (str/includes? (str/lower-case (or (:doc p) ""))
                                                                                (str/lower-case filter-text)))
                                                matches-disabled (or (not show-disabled-only)
                                                                     (contains? cascading (:id p)))]
                                            (and matches-text matches-disabled))))
                                (sort-by (case sort-by
                                           :id :id
                                           :beans #(- (count-beans %))
                                           :deps #(- (count (:deps %)))
                                           :id)))
          
          enabled-count (- (count all-plugins) (count cascading))
          disabled-count (count cascading)
          
          toggle-fn (fn [id current-disabled?]
                      (if current-disabled?
                        (do
                          (enable-plugin-no-reload! id)
                          (reload!))
                        (let [dependents (get-dependents id all-plugins)
                              active-dependents (filter #(not (contains? cascading %)) dependents)]
                          (if (seq active-dependents)
                            (when (js/confirm (str "Disabling " id " will also disable:\n\n"
                                                   (str/join "\n" (map str active-dependents))
                                                   "\n\nContinue?"))
                              (disable-plugin-no-reload! id)
                              (reload!))
                            (do
                              (disable-plugin-no-reload! id)
                              (reload!))))))
          
          navigate-to-detail (fn [id]
                               ;; Navigate to plugin detail page
                               ;; Use custom encoding (/ -> ~) to avoid URL routing issues
                               (let [encoded-id (encode-plugin-id id)]
                                 (set! (.-hash js/location) (str "#/development/debugger/plugin/" encoded-id))))]
      
      [:div {:class "flex flex-col h-full bg-gray-100"}
       ;; Header with stats
       [:div {:class "bg-white border-b border-gray-200 shadow-sm"}
        [:div {:class "px-4 sm:px-6 py-4"}
         ;; Title and stats row - responsive
         [:div {:class "flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-4"}
          [:div {:class "flex-shrink-0"}
           [:h2 {:class "text-xl font-bold text-gray-900"} "Plugin Manager"]
           [:p {:class "text-sm text-gray-500 hidden sm:block"} "Manage and inspect loaded plugins"]]
          
          ;; Stats - horizontal on all sizes
          [:div {:class "flex items-center gap-6 sm:gap-4"}
           [:div {:class "text-center"}
            [:div {:class "text-xl sm:text-2xl font-bold text-green-600"} enabled-count]
            [:div {:class "text-xs text-gray-500"} "Enabled"]]
           [:div {:class "text-center"}
            [:div {:class "text-xl sm:text-2xl font-bold text-gray-400"} disabled-count]
            [:div {:class "text-xs text-gray-500"} "Disabled"]]
           [:div {:class "text-center"}
            [:div {:class "text-xl sm:text-2xl font-bold text-gray-900"} (count all-plugins)]
            [:div {:class "text-xs text-gray-500"} "Total"]]]]
         
         ;; Filters row - stack on mobile
         [:div {:class "flex flex-col sm:flex-row items-stretch sm:items-center gap-3"}
          ;; Search - full width on mobile
          [:div {:class "flex-1"}
           [:input {:class "w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm"
                    :placeholder "🔍 Search plugins..."
                    :value filter-text
                    :on-change #(swap! ui-state assoc :filter-text (-> % .-target .-value))}]]
          
          ;; Sort and checkbox row
          [:div {:class "flex items-center gap-3"}
           ;; Sort
           [:select {:class "px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 text-sm flex-shrink-0"
                     :value (name sort-by)
                     :on-change #(swap! ui-state assoc :sort-by (keyword (-> % .-target .-value)))}
            [:option {:value "id"} "Sort by Name"]
            [:option {:value "beans"} "Sort by Beans"]
            [:option {:value "deps"} "Sort by Dependencies"]]
           
           ;; Show disabled only
           [:label {:class "flex items-center gap-2 text-sm text-gray-700 cursor-pointer select-none whitespace-nowrap"}
            [:input {:type "checkbox"
                     :class "w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                     :checked show-disabled-only
                     :on-change #(swap! ui-state assoc :show-disabled-only (-> % .-target .-checked))}]
            [:span {:class "hidden sm:inline"} "Disabled only"]
            [:span {:class "sm:hidden"} "Disabled"]]]]]]
       
       ;; Plugin grid - single column until very wide screens
       [:div {:class "flex-1 overflow-y-auto p-4 sm:p-6"}
        [:div {:class "grid grid-cols-1 2xl:grid-cols-2 gap-4"}
         (for [plugin filtered-plugins]
           ^{:key (:id plugin)}
           [plugin-card plugin format-id all-plugins cascading disabled-ids toggle-fn expanded-ids navigate-to-detail])]
        
        (when (empty? filtered-plugins)
          [:div {:class "text-center py-12 text-gray-500"}
           [:div {:class "text-4xl mb-2"} "🔍"]
           [:p "No plugins match your search criteria"]])]])))
