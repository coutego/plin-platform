(ns plinpt.p-debug.plugins-ui
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [clojure.set :as set]
            [plin.boot :as boot]
            [plinpt.p-debug.common :as common]))

(defn- get-dep-id [dep]
  (if (keyword? dep) dep (:id dep)))

(defn- get-dependents [target-id all-plugins]
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

(defonce ui-state (r/atom {:filter-text ""
                           :immediate? false}))

(defn main-view [api]
  (fn [api]
    (let [{:keys [state enable-plugin-no-reload! disable-plugin-no-reload! reload!]} api
          {:keys [all-plugins disabled-ids]} @state
          cascading (boot/get-cascading-disabled all-plugins disabled-ids)
          
          {:keys [filter-text immediate?]} @ui-state
          
          ;; Calculate formatter based on all plugin IDs
          format-id (common/make-id-formatter (map :id all-plugins))
          
          ft filter-text
          filtered-plugins (if (str/blank? ft)
                             all-plugins
                             (filter #(str/includes? (str/lower-case (str (:id %))) (str/lower-case ft))
                                     all-plugins))
          
          toggle-fn (fn [id current-disabled?]
                      (if current-disabled?
                        ;; Enable
                        (do
                          (enable-plugin-no-reload! id)
                          (when immediate? (reload!)))
                        
                        ;; Disable
                        (let [dependents (get-dependents id all-plugins)
                              ;; Only care about dependents that are currently active
                              active-dependents (filter #(not (contains? cascading %)) dependents)]
                          
                          (if (seq active-dependents)
                            (when (js/confirm (str "Disabling " id " will also disable the following plugins which depend on it:\n\n"
                                                   (str/join "\n" active-dependents)
                                                   "\n\nContinue?"))
                              (disable-plugin-no-reload! id)
                              (when immediate? (reload!)))
                            (do
                              (disable-plugin-no-reload! id)
                              (when immediate? (reload!)))))))]
      
      [:div {:class "flex flex-col h-full bg-gray-50"}
       ;; Toolbar
       [:div {:class "p-4 border-b border-gray-200 bg-white flex flex-col gap-4"}
        [:div {:class "flex justify-between items-center"}
         [:div
          [:h3 {:class "font-bold text-lg"} "System Plugins"]
          [:p {:class "text-sm text-gray-500"} "Manage loaded plugins and their states."]]
         
         [:div {:class "flex items-center gap-4"}
          [:label {:class "flex items-center gap-2 text-sm text-gray-700 cursor-pointer select-none"}
           [:input {:type "checkbox"
                    :class "rounded text-blue-600 focus:ring-blue-500"
                    :checked immediate?
                    :on-change #(swap! ui-state assoc :immediate? (-> % .-target .-checked))}]
           "Apply immediately"]
          
          [:button {:class (str "px-4 py-2 rounded font-medium transition-colors shadow-sm "
                                (if immediate?
                                  "bg-gray-100 text-gray-400 cursor-not-allowed"
                                  "bg-blue-600 text-white hover:bg-blue-700"))
                    :disabled immediate?
                    :on-click reload!}
           "Reload System"]]]
        
        [:input {:class "w-full border rounded px-3 py-2 text-sm"
                 :placeholder "Filter plugins by ID..."
                 :value filter-text
                 :on-change #(swap! ui-state assoc :filter-text (-> % .-target .-value))}]]
       
       ;; Grid
       [:div {:class "flex-1 overflow-y-auto p-4"}
        [:div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
         (for [p filtered-plugins]
           (let [id (:id p)
                 manually-disabled? (contains? disabled-ids id)
                 effectively-disabled? (contains? cascading id)
                 is-system? (= id :system-api)]
             ^{:key id}
             [:div {:class (str "p-4 rounded-lg border transition-all "
                                (if effectively-disabled? 
                                  "bg-gray-100 border-gray-200 opacity-75" 
                                  "bg-white border-gray-200 shadow-sm hover:shadow-md"))}
              [:div {:class "flex items-start justify-between mb-2"}
               [:div {:class "flex items-center gap-2"}
                [:input {:type "checkbox"
                         :class "h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
                         :checked (not manually-disabled?)
                         :disabled is-system?
                         :on-change #(toggle-fn id manually-disabled?)}]
                [:span {:class (str "font-mono font-bold "
                                    (if effectively-disabled? "text-gray-500 line-through" "text-blue-800"))
                        :title (str id)} ;; Show full ID on hover
                 (format-id id)]] ;; Use formatted ID
               
               (when effectively-disabled?
                 [:span {:class "px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800"}
                  "Disabled"])]
              
              (when (:doc p)
                [:p {:class "text-sm text-gray-600 mb-3 line-clamp-2" :title (:doc p)} (:doc p)])
              
              (when (seq (:deps p))
                [:div {:class "text-xs text-gray-500"}
                 [:span {:class "font-semibold"} "Deps: "]
                 [:div {:class "flex flex-wrap gap-1 mt-1"}
                  (for [dep (:deps p)]
                    (let [dep-id (get-dep-id dep)
                          dep-doc (if (keyword? dep) (str "ID: " dep) (:doc dep))]
                      ^{:key dep-id}
                      [:span {:class "bg-gray-100 px-1.5 py-0.5 rounded border border-gray-200 cursor-help"
                              :title dep-doc}
                       (format-id dep-id)]))]])]))]]])))
