(ns plinpt-extras.p-plugin-forge.ui.library-page
  "Library tab interface for managing saved plugins."
  (:require [reagent.core :as r]
            [plinpt-extras.p-plugin-forge.core :as core]
            [plinpt-extras.p-plugin-forge.storage :as storage]))

(defonce expanded-plugins (r/atom #{}))
(defonce loading-version (r/atom nil)) ;; {:plugin-id :version-idx}

(defn toggle-expanded! [plugin-id]
  (swap! expanded-plugins
         (fn [s]
           (if (contains? s plugin-id)
             (disj s plugin-id)
             (conj s plugin-id)))))

(defn format-date [ts]
  (let [date (js/Date. ts)]
    (.toLocaleDateString date)))

(defn download-plugin! [plugin-id version-idx]
  (let [versions (core/get-plugin-versions plugin-id)
        version (nth versions version-idx)
        code (:code version)
        blob (js/Blob. #js [code] #js {:type "text/plain"})
        url (js/URL.createObjectURL blob)
        link (js/document.createElement "a")]
    (set! (.-href link) url)
    (set! (.-download link) (str (name plugin-id) ".cljs"))
    (.click link)
    (js/URL.revokeObjectURL url)))

(defn load-plugin-version! [plugin-id version-idx]
  (let [versions (core/get-plugin-versions plugin-id)
        version (nth versions version-idx)
        code (:code version)]
    (reset! loading-version {:plugin-id plugin-id :version-idx version-idx})
    (try
      ;; Evaluate the code using scittle
      (js/scittle.core.eval_string code)
      ;; Try to extract namespace and get the plugin
      (let [ns-match (re-find #"\(\s*ns\s+([\w\.\-]+)" code)
            ns-str (second ns-match)]
        (if ns-str
          (let [plugin-def (js/scittle.core.eval_string (str ns-str "/plugin"))]
            (if plugin-def
              (-> (core/register-plugin! plugin-def)
                  (.then (fn []
                           (js/console.log "Plugin registered successfully:" ns-str)
                           (core/set-hot-loaded! (keyword ns-str) plugin-def code)
                           (reset! loading-version nil)))
                  (.then nil
                         (fn [err]
                           (js/console.error "Failed to register plugin:" err)
                           (core/set-hot-load-error! (str "Registration failed: " (.-message err)))
                           (core/set-hot-loaded! (keyword ns-str) plugin-def code)
                           (reset! loading-version nil))))
              (do
                (js/alert "Could not find 'plugin' var in namespace")
                (reset! loading-version nil))))
          (do
            (js/alert "Could not extract namespace from code")
            (reset! loading-version nil))))
      (catch :default e
        (core/set-hot-load-error! (.-message e))
        (js/console.error "Failed to load plugin:" e)
        ;; Still show overlay with error
        (core/set-hot-loaded! plugin-id nil code)
        (reset! loading-version nil)))))

(defn version-item [plugin-id version-idx version hot-loaded-id]
  (let [is-loaded? (= plugin-id hot-loaded-id)
        loading? (and (= plugin-id (:plugin-id @loading-version))
                      (= version-idx (:version-idx @loading-version)))]
    [:div {:class "flex items-center justify-between py-2 px-3 bg-slate-50 rounded-lg mb-2"}
     [:div {:class "flex items-center gap-3"}
      [:span {:class "text-xs font-mono text-slate-500"}
       (str "v" (inc version-idx))]
      [:span {:class "text-sm text-slate-600"}
       (format-date (:timestamp version))]
      (when is-loaded?
        [:span {:class "px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full"}
         "Loaded"])]
     [:div {:class "flex items-center gap-2"}
      [:button
       {:class (str "p-1.5 rounded transition-colors "
                    (if loading?
                      "text-blue-400 bg-blue-50 cursor-not-allowed"
                      "text-slate-400 hover:text-blue-600 hover:bg-blue-50"))
        :title "Load this version"
        :disabled loading?
        :on-click #(when-not loading?
                     (load-plugin-version! plugin-id version-idx))}
       (if loading?
         [:svg {:class "w-4 h-4 animate-spin" :fill "none" :viewBox "0 0 24 24"}
          [:circle {:class "opacity-25" :cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
          [:path {:class "opacity-75" :fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
         [:svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                  :d "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"}]])]
      [:button
       {:class "p-1.5 text-slate-400 hover:text-green-600 hover:bg-green-50 rounded transition-colors"
        :title "Download"
        :on-click #(download-plugin! plugin-id version-idx)}
       [:svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
        [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                :d "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"}]]]
      [:button
       {:class "p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
        :title "Delete version"
        :on-click #(when (js/confirm "Delete this version?")
                     (core/delete-plugin-version! plugin-id version-idx))}
       [:svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
        [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                :d "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"}]]]]]))

(defn plugin-card [plugin-id plugin-data hot-loaded-id]
  (let [expanded? (contains? @expanded-plugins plugin-id)
        versions (:versions plugin-data)
        version-count (count versions)
        latest-version (last versions)]
    [:div {:class "bg-white border border-slate-200 rounded-xl overflow-hidden mb-4"}
     [:div {:class "flex items-center justify-between p-4 cursor-pointer hover:bg-slate-50"
            :on-click #(toggle-expanded! plugin-id)}
      [:div {:class "flex items-center gap-3"}
       [:div {:class "p-2 bg-purple-100 rounded-lg"}
        [:svg {:class "w-5 h-5 text-purple-600" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                 :d "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z"}]]]
       [:div
        [:h3 {:class "font-medium text-slate-900"} (name plugin-id)]
        [:p {:class "text-sm text-slate-500"}
         (str version-count " version" (when (> version-count 1) "s")
              " • Last updated " (format-date (:timestamp latest-version)))]]]
      [:div {:class "flex items-center gap-2"}
       (when (= plugin-id hot-loaded-id)
         [:span {:class "px-2 py-1 bg-green-100 text-green-700 text-xs font-medium rounded-full"}
          "Active"])
       [:svg {:class (str "w-5 h-5 text-slate-400 transition-transform "
                          (when expanded? "rotate-180"))
              :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
        [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                :d "M19 9l-7 7-7-7"}]]]]
     (when expanded?
       [:div {:class "border-t border-slate-200 p-4"}
        [:div {:class "flex justify-between items-center mb-3"}
         [:span {:class "text-sm font-medium text-slate-700"} "Versions"]
         [:button
          {:class "text-sm text-red-600 hover:text-red-700"
           :on-click #(when (js/confirm (str "Delete all versions of " (name plugin-id) "?"))
                        (core/delete-plugin! plugin-id)
                        (swap! expanded-plugins disj plugin-id))}
          "Delete All"]]
        (for [[idx version] (map-indexed vector (reverse versions))]
          ^{:key idx}
          [version-item plugin-id (- (dec version-count) idx) version hot-loaded-id])])]))

(defn empty-library []
  [:div {:class "flex flex-col items-center justify-center py-16 text-center"}
   [:div {:class "p-4 bg-slate-100 rounded-full mb-4"}
    [:svg {:class "w-12 h-12 text-slate-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "1.5"
             :d "M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"}]]]
   [:h3 {:class "text-lg font-semibold text-slate-700 mb-2"} "No saved plugins"]
   [:p {:class "text-slate-500 max-w-md"}
    "Plugins you create and save will appear here. Start a chat to create your first plugin!"]])

(defn library-header []
  (let [library (core/get-library)
        storage-size (storage/get-storage-size)
        size-kb (/ storage-size 1024)]
    [:div {:class "flex items-center justify-between mb-4"}
     [:div
      [:h2 {:class "text-lg font-semibold text-slate-900"} "Plugin Library"]
      [:p {:class "text-sm text-slate-500"}
       (str (count library) " plugin" (when (not= 1 (count library)) "s")
            " • " (.toFixed size-kb 1) " KB used")]]
     (when (seq library)
       [:div {:class "flex gap-2"}
        [:button
         {:class "px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
          :on-click #(let [json (storage/export-library)
                          blob (js/Blob. #js [json] #js {:type "application/json"})
                          url (js/URL.createObjectURL blob)
                          link (js/document.createElement "a")]
                      (set! (.-href link) url)
                      (set! (.-download link) "plugin-forge-library.json")
                      (.click link)
                      (js/URL.revokeObjectURL url))}
         "Export All"]
        [:button
         {:class "px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 rounded-lg transition-colors"
          :on-click #(when (js/confirm "Clear entire library? This cannot be undone.")
                       (storage/clear-all!)
                       (core/init!))}
         "Clear All"]])]))

(defn library-page []
  (let [library (core/get-library)
        hot-loaded-id (:plugin-id (core/get-hot-loaded))]
    [:div {:class "bg-white rounded-xl border border-slate-200 p-6"}
     [library-header]
     (if (empty? library)
       [empty-library]
       [:div
        (for [[plugin-id plugin-data] library]
          ^{:key plugin-id}
          [plugin-card plugin-id plugin-data hot-loaded-id])])]))
