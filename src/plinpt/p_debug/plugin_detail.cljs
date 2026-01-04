(ns plinpt.p-debug.plugin-detail
  "Full plugin detail page showing everything the system knows about a plugin."
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.edn :as edn]
            [plin.boot :as boot]
            [plinpt.p-debug.common :as common]))

(defn- get-dep-id [dep]
  (if (keyword? dep) dep (:id dep)))

(defn- get-direct-dependents [target-id all-plugins]
  (->> all-plugins
       (filter (fn [p] (some #(= (get-dep-id %) target-id) (:deps p))))
       (map :id)
       set))

(defn- get-transitive-dependents [target-id all-plugins]
  (loop [ids #{}
         to-check #{target-id}
         checked #{}]
    (if (empty? to-check)
      (disj ids target-id)
      (let [current (first to-check)
            direct (get-direct-dependents current all-plugins)
            new-ids (set/difference direct checked)]
        (recur (into ids direct)
               (into (disj to-check current) new-ids)
               (conj checked current))))))

(defn- get-transitive-deps [plugin all-plugins]
  (let [plugin-map (into {} (map (juxt :id identity) all-plugins))]
    (loop [ids #{}
           to-check (set (map get-dep-id (:deps plugin)))
           checked #{}]
      (if (empty? to-check)
        ids
        (let [current (first to-check)
              current-plugin (get plugin-map current)
              direct-deps (when current-plugin (set (map get-dep-id (:deps current-plugin))))
              new-deps (set/difference (or direct-deps #{}) checked)]
          (recur (conj ids current)
                 (into (disj to-check current) new-deps)
                 (conj checked current)))))))

(defn- decode-plugin-id
  "Decode a plugin ID from URL format. Replaces ~ back to /."
  [encoded-str]
  (when encoded-str
    (str/replace encoded-str "~" "/")))

(defn- encode-plugin-id
  "Encode a plugin ID for use in URLs. Replaces / with ~ to avoid routing issues."
  [id]
  (let [id-str (if (keyword? id)
                 (subs (str id) 1)  ;; Remove leading colon from keyword
                 (str id))]
    (str/replace id-str "/" "~")))

(defn- parse-plugin-id
  "Parse plugin ID from route parameter. Handles the ~ encoding for /."
  [id-str]
  (when (and id-str (not (str/blank? id-str)))
    ;; First decode ~ back to /
    (let [decoded (decode-plugin-id id-str)]
      ;; Convert to keyword
      (keyword decoded))))

(defn- find-plugin-by-id
  "Find a plugin by ID with exact matching only."
  [all-plugins plugin-id]
  (when plugin-id
    (or
     ;; Exact match
     (->> all-plugins (filter #(= (:id %) plugin-id)) first)
     ;; Try with /plugin suffix (for plugin IDs like :plinpt.i-application/plugin)
     (let [with-suffix (keyword (str (namespace plugin-id) "/" (name plugin-id) "/plugin"))]
       (->> all-plugins (filter #(= (:id %) with-suffix)) first))
     ;; Try interpreting the whole thing as namespace/plugin
     (let [as-ns-plugin (keyword (str plugin-id "/plugin"))]
       (->> all-plugins (filter #(= (:id %) as-ns-plugin)) first)))))

;; --- Icons ---

(defn- icon-back []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 19l-7-7m0 0l7-7m-7 7h18"}]])

(defn- icon-check []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M5 13l4 4L19 7"}]])

(defn- icon-x []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M6 18L18 6M6 6l12 12"}]])

(defn- icon-external-link []
  [:svg {:class "w-3 h-3 ml-1" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"}]])

;; --- Navigation Helper ---

(defn- navigate-to-plugin [plugin-id]
  (let [encoded-id (encode-plugin-id plugin-id)]
    (set! (.-hash js/location) (str "#/development/debugger/plugin/" encoded-id))))

;; --- Section Components ---

(defn- section-header [title subtitle icon-component color-class]
  [:div {:class (str "flex items-center gap-3 px-4 py-3 rounded-t-lg " color-class)}
   [:div {:class "p-2 bg-white/50 rounded-lg"}
    icon-component]
   [:div
    [:h3 {:class "font-semibold text-gray-900"} title]
    (when subtitle
      [:p {:class "text-sm text-gray-600"} subtitle])]])

(defn- code-block [content]
  [:pre {:class "bg-gray-900 text-green-400 p-4 rounded-lg text-xs font-mono overflow-x-auto whitespace-pre-wrap"}
   (with-out-str (pprint/pprint content))])

;; --- Clickable Plugin Chip ---

(defn- plugin-chip [plugin-id format-id cascading & {:keys [direction]}]
  (let [disabled? (contains? cascading plugin-id)
        arrow-icon (case direction
                     :left [:svg {:class "w-4 h-4 text-indigo-400" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
                            [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M14 5l7 7m0 0l-7 7m7-7H3"}]]
                     :right [:svg {:class "w-4 h-4 text-purple-400" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
                             [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M14 5l7 7m0 0l-7 7m7-7H3"}]]
                     nil)]
    [:button {:class (str "flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-mono transition-colors cursor-pointer "
                          (if disabled?
                            "bg-gray-200 text-gray-400 line-through hover:bg-gray-300"
                            (case direction
                              :left "bg-indigo-100 text-indigo-800 hover:bg-indigo-200"
                              :right "bg-purple-100 text-purple-800 hover:bg-purple-200"
                              "bg-blue-100 text-blue-800 hover:bg-blue-200")))
              :on-click #(navigate-to-plugin plugin-id)
              :title (str "View details for " plugin-id)}
     (when (= direction :left) arrow-icon)
     (format-id plugin-id)
     [icon-external-link]
     (when (= direction :right) arrow-icon)]))

(defn- dependency-graph-mini [plugin-id deps dependents format-id cascading]
  [:div {:class "bg-gray-50 rounded-lg p-4"}
   [:div {:class "flex items-center justify-center gap-8 flex-wrap"}
    ;; Dependencies (left)
    [:div {:class "flex flex-col items-end gap-1"}
     (if (seq deps)
       (for [dep deps]
         ^{:key dep}
         [plugin-chip dep format-id cascading :direction :right])
       [:span {:class "text-gray-400 text-sm italic"} "No deps"])]
    
    ;; Current plugin (center)
    [:div {:class "px-6 py-4 bg-blue-600 text-white rounded-xl font-mono font-bold text-lg shadow-lg"}
     (format-id plugin-id)]
    
    ;; Dependents (right)
    [:div {:class "flex flex-col items-start gap-1"}
     (if (seq dependents)
       (for [dep dependents]
         ^{:key dep}
         [plugin-chip dep format-id cascading :direction :left])
       [:span {:class "text-gray-400 text-sm italic"} "No dependents"])]]])

;; --- Bean Detail with Reagent Component Rendering ---

(defn- get-default-args [api-meta]
  (if-let [args (:args api-meta)]
    (try
      (pr-str (mapv second args))
      (catch :default _ "[]"))
    "[]"))

(defn- render-signature [api-meta]
  (cond
    (nil? api-meta)
    [:div {:class "mb-2 text-xs text-orange-500 italic"} "API is not documented"]

    (empty? (:args api-meta))
    [:div {:class "mb-2 text-xs text-gray-500 font-mono"}
     [:span {:class "font-bold text-gray-600"} "Signature: "]
     [:span "[]"]
     (when-let [ret (:ret api-meta)]
       [:span {:class "ml-2"}
        [:span {:class "text-gray-400"} "-> "]
        [:span {:class "text-purple-600"} (str ret)]])]

    :else
    (let [args (:args api-meta)]
      [:div {:class "mb-2 text-xs text-gray-500 font-mono"}
       [:span {:class "font-bold text-gray-600"} "Signature: "]
       [:span "["]
       (doall
        (for [[i [arg-name _ arg-type]] (map-indexed vector args)]
          ^{:key i}
          [:span
           (when (pos? i) ", ")
           [:span {:class "text-blue-600"} arg-name]
           (when arg-type
             [:span {:class "text-gray-400"} (str " " arg-type)])]))
       [:span "]"]
       (when-let [ret (:ret api-meta)]
         [:span {:class "ml-2"}
          [:span {:class "text-gray-400"} "-> "]
          [:span {:class "text-purple-600"} (str ret)]])])))

(defn- component-renderer [comp-fn api-meta]
  (let [args-state (r/atom (get-default-args api-meta))
        mounting? (r/atom false)
        error-state (r/atom nil)
        show-input? (or (nil? api-meta)
                        (not (empty? (:args api-meta))))]
    (fn [comp-fn api-meta]
      [:div {:class "mt-2 border p-2 rounded bg-blue-50"}
       [:div {:class "text-xs font-bold mb-1 text-blue-800"} "Render Component"]
       
       [render-signature api-meta]
       
       (when show-input?
         [:textarea {:class "w-full p-1 text-xs border rounded font-mono"
                     :rows 2
                     :value @args-state
                     :on-change #(reset! args-state (-> % .-target .-value))
                     :placeholder "Arguments vector e.g. [] or [{:prop 1}]"}])
       
       [:div {:class "flex gap-2 mt-1"}
        [:button {:class "px-2 py-1 bg-blue-600 text-white text-xs rounded hover:bg-blue-700"
                  :on-click (fn []
                              (reset! error-state nil)
                              (try
                                (let [args (if show-input?
                                             (edn/read-string @args-state)
                                             [])]
                                  (if (vector? args)
                                    (swap! mounting? not)
                                    (reset! error-state "Arguments must be a vector [...]")))
                                (catch :default e
                                  (reset! error-state (str e)))))}
         (if @mounting? "Unmount" "Render")]]
       
       (when @error-state
         [:div {:class "mt-2 text-red-600 text-xs"} @error-state])
       
       (when @mounting?
         [:div {:class "mt-2 border border-dashed border-blue-400 p-2 bg-white rounded"}
          [common/ui-error-boundary
           (try
             (let [args (if show-input?
                          (edn/read-string @args-state)
                          [])]
               (into [comp-fn] args))
             (catch :default e
               [:div.text-red-500 (str "Render error: " e)]))
           #(reset! mounting? false)]])])))

(defn- function-caller [f api-meta]
  (let [args-state (r/atom (get-default-args api-meta))
        result-state (r/atom nil)
        error-state (r/atom nil)
        show-input? (or (nil? api-meta)
                        (not (empty? (:args api-meta))))]
    (fn [f api-meta]
      [:div {:class "mt-2 border p-2 rounded bg-gray-50"}
       [:div {:class "text-xs font-bold mb-1"} "Call Function"]
       
       [render-signature api-meta]
       
       (when show-input?
         [:textarea {:class "w-full p-1 text-xs border rounded font-mono"
                     :rows 2
                     :value @args-state
                     :on-change #(reset! args-state (-> % .-target .-value))
                     :placeholder "Arguments vector e.g. [1 2]"}])
       
       [:div {:class "flex gap-2 mt-1"}
        [:button {:class "px-2 py-1 bg-blue-600 text-white text-xs rounded hover:bg-blue-700"
                  :on-click (fn []
                              (reset! result-state nil)
                              (reset! error-state nil)
                              (try
                                (let [args (if show-input?
                                             (edn/read-string @args-state)
                                             [])]
                                  (if (vector? args)
                                    (let [res (apply f args)]
                                      (if (instance? js/Promise res)
                                        (reset! result-state {:type :promise :value res :id (str (random-uuid))})
                                        (reset! result-state {:type :value :value res :id (str (random-uuid))})))
                                    (reset! error-state "Arguments must be a vector [...]")))
                                (catch :default e
                                  (reset! error-state (str e)))))}
         "Call"]]
       
       (when @error-state
         [:div {:class "mt-2 text-red-600 text-xs"} @error-state])
       
       (when @result-state
         (let [{:keys [type value id]} @result-state]
           [:div {:class "mt-2" :key id}
            [:div {:class "text-xs text-gray-500"} "Result:"]
            (if (= type :promise)
              [common/promise-view value]
              [common/render-result value])]))])))

(defn- bean-detail-row [bean-key bean-def container]
  (let [expanded? (r/atom false)
        instance (get container bean-key)]
    (fn [bean-key bean-def container]
      (let [doc (:debug/doc bean-def)
            source (:debug/source bean-def)
            api-meta (or (:api bean-def)
                         (:debug/api bean-def))
            is-reagent? (:debug/reagent-component bean-def)
            is-fn? (fn? instance)]
        [:div {:class "border border-gray-200 rounded-lg overflow-hidden bg-white"}
         ;; Header
         [:div {:class "flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-gray-50"
                :on-click #(swap! expanded? not)}
          [:div {:class "flex items-center gap-3"}
           [:div {:class (str "transition-transform duration-200 " (when @expanded? "rotate-90"))}
            [:svg {:class "w-4 h-4 text-gray-400" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
             [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]
           [:span {:class "font-mono text-sm text-blue-800 bg-blue-50 px-2 py-1 rounded"} (str bean-key)]
           [:span {:class (str "text-xs px-2 py-0.5 rounded "
                               (cond 
                                 is-reagent? "bg-purple-100 text-purple-700"
                                 is-fn? "bg-blue-100 text-blue-700"
                                 :else "bg-gray-100 text-gray-600"))}
            (cond is-reagent? "Component" is-fn? "Function" :else "Value")]]
          (when doc
            [:span {:class "text-sm text-gray-500 truncate max-w-md"} doc])]
         
         ;; Expanded content
         (when @expanded?
           [:div {:class "px-4 py-3 border-t border-gray-100 bg-gray-50 space-y-3"}
            (when doc
              [:div
               [:div {:class "text-xs font-semibold text-gray-500 mb-1"} "Documentation"]
               [:p {:class "text-sm text-gray-700"} doc]])
            
            (when source
              [:div
               [:div {:class "text-xs font-semibold text-gray-500 mb-1"} "Definition"]
               [:pre {:class "bg-gray-800 text-gray-300 p-2 rounded text-xs overflow-x-auto"}
                (pr-str source)]])
            
            ;; Value / Function Caller / Component Renderer
            (cond
              is-reagent?
              [component-renderer instance api-meta]
              
              is-fn?
              [function-caller instance api-meta]
              
              :else
              [:div
               [:div {:class "text-xs font-semibold text-gray-500 mb-1"} "Current Value"]
               [common/render-result instance]])])]))))

;; --- Extension Point Detail ---

(defn- extension-detail-row [ext]
  (let [expanded? (r/atom false)]
    (fn [ext]
      [:div {:class "border border-gray-200 rounded-lg overflow-hidden bg-white"}
       [:div {:class "flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-gray-50"
              :on-click #(swap! expanded? not)}
        [:div {:class "flex items-center gap-3"}
         [:div {:class (str "transition-transform duration-200 " (when @expanded? "rotate-90"))}
          [:svg {:class "w-4 h-4 text-gray-400" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]
         [:span {:class "font-mono text-sm text-amber-800 bg-amber-50 px-2 py-1 rounded"} (str (:key ext))]]
        (when (:doc ext)
          [:span {:class "text-sm text-gray-500 truncate max-w-md"} (:doc ext)])]
       
       (when @expanded?
         [:div {:class "px-4 py-3 border-t border-gray-100 bg-gray-50 space-y-3"}
          (when (:doc ext)
            [:div
             [:div {:class "text-xs font-semibold text-gray-500 mb-1"} "Documentation"]
             [:p {:class "text-sm text-gray-700 whitespace-pre-wrap"} (:doc ext)]])
          
          [:div
           [:div {:class "text-xs font-semibold text-gray-500 mb-1"} "Handler"]
           [:pre {:class "bg-gray-800 text-gray-300 p-2 rounded text-xs overflow-x-auto"}
            (pr-str (:handler ext))]]])])))

;; --- Contribution Detail ---

(defn- safe-count [x]
  "Safely count items - returns 1 for non-countable items like keywords"
  (cond
    (nil? x) 0
    (keyword? x) 1
    (string? x) 1
    (coll? x) (count x)
    :else 1))

(defn- contribution-detail-row [ext-key values]
  (let [expanded? (r/atom false)]
    (fn [ext-key values]
      [:div {:class "border border-gray-200 rounded-lg overflow-hidden bg-white"}
       [:div {:class "flex items-center justify-between px-4 py-3 cursor-pointer hover:bg-gray-50"
              :on-click #(swap! expanded? not)}
        [:div {:class "flex items-center gap-3"}
         [:div {:class (str "transition-transform duration-200 " (when @expanded? "rotate-90"))}
          [:svg {:class "w-4 h-4 text-gray-400" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]
         [:span {:class "font-mono text-sm text-cyan-800 bg-cyan-50 px-2 py-1 rounded"} (str ext-key)]]
        [:span {:class "text-sm text-gray-500"} (str (safe-count values) " contribution(s)")]]
       
       (when @expanded?
         [:div {:class "px-4 py-3 border-t border-gray-100 bg-gray-50"}
          [:pre {:class "bg-gray-800 text-gray-300 p-2 rounded text-xs overflow-x-auto whitespace-pre-wrap"}
           (with-out-str (pprint/pprint values))]])])))

;; --- Extract plugin ID from URL hash ---

(defn- extract-plugin-id-from-hash
  "Extract the plugin ID directly from the URL hash as a fallback."
  []
  (let [hash (.-hash js/location)
        ;; Hash format: #/development/debugger/plugin/plinpt-extras.showcase.snake~plugin
        match (re-find #"/development/debugger/plugin/(.+)$" hash)]
    (when match
      (second match))))

;; --- Main Detail Page ---

(defn plugin-detail-page [api plugin-id-str]
  (fn [api plugin-id-str]
    ;; If plugin-id-str is empty, try to extract from URL hash directly
    (let [effective-id-str (if (str/blank? plugin-id-str)
                             (extract-plugin-id-from-hash)
                             plugin-id-str)
          plugin-id (parse-plugin-id effective-id-str)
          {:keys [state enable-plugin-no-reload! disable-plugin-no-reload! reload!]} api
          {:keys [all-plugins disabled-ids container]} @state
          cascading (boot/get-cascading-disabled all-plugins disabled-ids)
          
          ;; Find plugin with exact matching
          plugin (find-plugin-by-id all-plugins plugin-id)
          
          actual-plugin-id (when plugin (:id plugin))
          format-id (common/make-id-formatter (map :id all-plugins))
          
          manually-disabled? (contains? disabled-ids actual-plugin-id)
          effectively-disabled? (contains? cascading actual-plugin-id)
          is-system? (= actual-plugin-id :system-api)
          
          deps (when plugin (map get-dep-id (:deps plugin)))
          direct-dependents (when plugin (get-direct-dependents actual-plugin-id all-plugins))
          transitive-dependents (when plugin (get-transitive-dependents actual-plugin-id all-plugins))
          transitive-deps (when plugin (get-transitive-deps plugin all-plugins))
          
          definitions (get container :plin.core/definitions {})
          plugin-beans (when plugin (:beans plugin))
          
          toggle-fn (fn []
                      (if manually-disabled?
                        (do (enable-plugin-no-reload! actual-plugin-id) (reload!))
                        (do (disable-plugin-no-reload! actual-plugin-id) (reload!))))]
      
      (if-not plugin
        [:div {:class "flex items-center justify-center h-full"}
         [:div {:class "text-center"}
          [:div {:class "text-6xl mb-4"} "🔍"]
          [:h2 {:class "text-xl font-semibold text-gray-700"} "Plugin Not Found"]
          [:p {:class "text-gray-500 mt-2"} (str "No plugin with ID: " effective-id-str)]
          [:p {:class "text-gray-400 mt-1 text-sm"} (str "Parsed as: " plugin-id)]
          [:div {:class "mt-4 p-3 bg-gray-100 rounded text-left text-xs font-mono max-w-md mx-auto"}
           [:div {:class "text-gray-600 mb-2"} "Available plugins:"]
           [:div {:class "max-h-40 overflow-y-auto"}
            (for [p (take 10 all-plugins)]
              ^{:key (:id p)}
              [:div {:class "text-gray-500"} (str (:id p))])
            (when (> (count all-plugins) 10)
              [:div {:class "text-gray-400 italic"} (str "... and " (- (count all-plugins) 10) " more")])]]
          [:button {:class "mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                    :on-click #(js/history.back)}
           "Go Back"]]]
        
        [:div {:class "h-full overflow-y-auto bg-gray-100"}
         ;; Header
         [:div {:class "bg-white border-b border-gray-200 shadow-sm sticky top-0 z-10"}
          [:div {:class "px-6 py-4"}
           [:div {:class "flex items-center justify-between"}
            [:div {:class "flex items-center gap-4"}
             [:button {:class "p-2 rounded-lg text-gray-500 hover:text-gray-700 hover:bg-gray-100"
                       :on-click #(js/history.back)}
              [icon-back]]
             [:div
              [:div {:class "flex items-center gap-3"}
               [:h1 {:class (str "text-2xl font-bold "
                                 (if effectively-disabled? "text-gray-400 line-through" "text-gray-900"))}
                (format-id actual-plugin-id)]
               (when is-system?
                 [:span {:class "px-2 py-1 rounded text-sm font-medium bg-blue-100 text-blue-700"} "System"])
               (cond
                 effectively-disabled?
                 [:span {:class "px-2 py-1 rounded text-sm font-medium bg-red-100 text-red-700"} "Disabled"]
                 :else
                 [:span {:class "px-2 py-1 rounded text-sm font-medium bg-green-100 text-green-700"} "Active"])]
              [:p {:class "text-sm text-gray-500 font-mono mt-1"} (str actual-plugin-id)]]]
            
            [:div {:class "flex items-center gap-3"}
             (when-not is-system?
               [:button {:class (str "flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all "
                                     (if effectively-disabled?
                                       "bg-green-600 text-white hover:bg-green-700"
                                       "bg-red-600 text-white hover:bg-red-700"))
                         :on-click toggle-fn}
                (if effectively-disabled? [icon-check] [icon-x])
                (if effectively-disabled? "Enable" "Disable")])]]]]
         
         ;; Content
         [:div {:class "p-6 space-y-6 max-w-6xl mx-auto"}
          ;; Documentation
          (when (:doc plugin)
            [:div {:class "bg-white rounded-xl shadow-sm overflow-hidden"}
             [section-header "Documentation" nil
              [:svg {:class "w-5 h-5 text-gray-600" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
               [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                       :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]
              "bg-gray-100"]
             [:div {:class "p-4"}
              [:p {:class "text-gray-700"} (:doc plugin)]]])
          
          ;; Dependency Graph
          [:div {:class "bg-white rounded-xl shadow-sm overflow-hidden"}
           [section-header "Dependency Graph" 
            (str (count deps) " dependencies, " (count direct-dependents) " direct dependents")
            [:svg {:class "w-5 h-5 text-purple-600" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
             [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                     :d "M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"}]]
            "bg-purple-100"]
           [:div {:class "p-4"}
            [dependency-graph-mini actual-plugin-id deps direct-dependents format-id cascading]
            
            ;; Transitive info
            [:div {:class "mt-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
             [:div {:class "bg-purple-50 rounded-lg p-3"}
              [:div {:class "text-sm font-medium text-purple-800 mb-2"} 
               "All Dependencies (transitive)"]
              [:div {:class "flex flex-wrap gap-1"}
               (for [dep (sort transitive-deps)]
                 ^{:key dep}
                 [:button {:class "px-2 py-1 bg-purple-100 text-purple-800 rounded text-xs font-mono hover:bg-purple-200 cursor-pointer"
                           :on-click #(navigate-to-plugin dep)
                           :title (str "View " dep)}
                  (format-id dep)])
               (when (empty? transitive-deps)
                 [:span {:class "text-purple-600 text-sm italic"} "None"])]]
             [:div {:class "bg-indigo-50 rounded-lg p-3"}
              [:div {:class "text-sm font-medium text-indigo-800 mb-2"} 
               "All Dependents (transitive)"]
              [:div {:class "flex flex-wrap gap-1"}
               (for [dep (sort transitive-dependents)]
                 ^{:key dep}
                 [:button {:class "px-2 py-1 bg-indigo-100 text-indigo-800 rounded text-xs font-mono hover:bg-indigo-200 cursor-pointer"
                           :on-click #(navigate-to-plugin dep)
                           :title (str "View " dep)}
                  (format-id dep)])
               (when (empty? transitive-dependents)
                 [:span {:class "text-indigo-600 text-sm italic"} "None"])]]]]]
          
          ;; Beans
          (when (seq plugin-beans)
            [:div {:class "bg-white rounded-xl shadow-sm overflow-hidden"}
             [section-header "Beans" (str (count plugin-beans) " beans defined")
              [:svg {:class "w-5 h-5 text-emerald-600" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
               [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                       :d "M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"}]]
              "bg-emerald-100"]
             [:div {:class "p-4 space-y-2"}
              (for [[bean-key bean-def] plugin-beans]
                ^{:key bean-key}
                [bean-detail-row bean-key (get definitions bean-key) container])]])
          
          ;; Extension Points
          (when (seq (:extensions plugin))
            [:div {:class "bg-white rounded-xl shadow-sm overflow-hidden"}
             [section-header "Extension Points" (str (count (:extensions plugin)) " extension points")
              [:svg {:class "w-5 h-5 text-amber-600" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
               [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                       :d "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z"}]]
              "bg-amber-100"]
             [:div {:class "p-4 space-y-2"}
              (for [ext (:extensions plugin)]
                ^{:key (:key ext)}
                [extension-detail-row ext])]])
          
          ;; Contributions
          (when (seq (:contributions plugin))
            [:div {:class "bg-white rounded-xl shadow-sm overflow-hidden"}
             [section-header "Contributions" 
              (str (reduce + (map safe-count (vals (:contributions plugin)))) " total contributions")
              [:svg {:class "w-5 h-5 text-cyan-600" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
               [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                       :d "M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"}]]
              "bg-cyan-100"]
             [:div {:class "p-4 space-y-2"}
              (for [[ext-key values] (:contributions plugin)]
                ^{:key ext-key}
                [contribution-detail-row ext-key values])]])
          
          ;; Raw Plugin Data
          [:div {:class "bg-white rounded-xl shadow-sm overflow-hidden"}
           [section-header "Raw Plugin Data" "Complete plugin definition"
            [:svg {:class "w-5 h-5 text-gray-600" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
             [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                     :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]]
            "bg-gray-100"]
           [:div {:class "p-4"}
            [code-block plugin]]]]]))))

(defn create-plugin-detail-page [api]
  (fn [plugin-id]
    [plugin-detail-page api plugin-id]))
