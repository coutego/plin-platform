(ns plinpt.p-ai-creator.ui
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plinpt.p-ai-creator.core :as core]))

(defn icon-magic []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"}]])

(defn spinner []
  [:svg {:class "animate-spin -ml-1 mr-3 h-5 w-5 text-current" :xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"}
   [:circle {:class "opacity-25" :cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
   [:path {:class "opacity-75" :fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]])

(defn saved-plugins-list [boot-api]
  (let [saved (:saved-plugins @core/state)]
    (if (empty? saved)
      [:div {:class "text-gray-500 italic"} "No saved plugins."]
      [:div {:class "space-y-2"}
       (for [[id data] saved]
         (let [data (js->clj data :keywordize-keys true)]
           ^{:key id}
           [:div {:class "flex items-center justify-between p-3 bg-white rounded border border-gray-200"}
            [:div
             [:div {:class "font-bold text-sm"} id]
             [:div {:class "text-xs text-gray-500"} (str (js/Date. (:timestamp data)))]]
            [:div {:class "flex gap-2"}
             [:button {:class "text-blue-600 hover:text-blue-800 text-xs font-bold"
                       :on-click (fn []
                                   (let [blob (js/Blob. #js [(:code data)] #js {:type "text/plain"})
                                         url (js/URL.createObjectURL blob)
                                         a (.createElement js/document "a")]
                                     (set! (.-href a) url)
                                     (set! (.-download a) (str (name id) ".cljs"))
                                     (.click a)))}
              "Download"]
             [:button {:class "text-red-600 hover:text-red-800 text-xs font-bold"
                       :on-click #(core/delete-plugin! id)}
              "Delete"]]])) ])))

(defn page [boot-api]
  (let [{:keys [api-key prompt status messages current-code last-error]} @core/state]
    [:div {:class "p-6 max-w-6xl mx-auto"}
     [:div {:class "flex items-center gap-4 mb-6"}
      [:div {:class "p-3 bg-purple-100 text-purple-600 rounded-lg"}
       [icon-magic]]
      [:div
       [:h1 {:class "text-2xl font-bold text-slate-800"} "AI Plugin Creator"]
       [:p {:class "text-slate-500"} "Describe what you want, and I'll build it."]]]

     ;; API Key
     [:div {:class "mb-6 p-4 bg-white rounded shadow-sm border border-gray-200"}
      [:label {:class "block text-sm font-medium text-gray-700 mb-1"} "OpenRouter API Key"]
      [:input {:type "password"
               :class "w-full p-2 border rounded"
               :value api-key
               :on-change #(core/set-api-key! (.. % -target -value))
               :placeholder "sk-or-..."}]]

     [:div {:class "grid grid-cols-1 lg:grid-cols-3 gap-6"}
      
      ;; Left Column: Chat & Input
      [:div {:class "lg:col-span-2 space-y-6"}
       
       ;; Chat History (Simplified)
       (when (seq messages)
         [:div {:class "bg-white rounded shadow-sm border border-gray-200 p-4 max-h-[400px] overflow-y-auto"}
          (for [[idx msg] (map-indexed vector messages)]
            (when (not= (:role msg) "system")
              ^{:key idx}
              [:div {:class (str "mb-4 p-3 rounded "
                                 (if (= (:role msg) "user") "bg-blue-50 ml-8" "bg-gray-50 mr-8"))}
               [:div {:class "text-xs font-bold mb-1 uppercase text-gray-500"} (:role msg)]
               [:div {:class "whitespace-pre-wrap text-sm font-mono"} 
                (if (> (count (:content msg)) 500)
                  (str (subs (:content msg) 0 500) "...")
                  (:content msg))]]))])

       ;; Input Area
       [:div {:class "bg-white rounded shadow-sm border border-gray-200 p-4"}
        [:textarea {:class "w-full p-3 border rounded h-32 font-sans"
                    :placeholder "E.g., Create a plugin that adds a 'Clock' widget to the homepage metrics."
                    :value prompt
                    :on-change #(swap! core/state assoc :prompt (.. % -target -value))}]
        
        [:div {:class "mt-4 flex justify-between items-center"}
         [:div {:class "flex items-center"}
          (case status
            :working [:<> [spinner] [:span {:class "text-blue-600 animate-pulse"} "Thinking & Generating..."]]
            :evaluating [:<> [spinner] [:span {:class "text-purple-600 animate-pulse"} "Compiling & Loading..."]]
            :success [:span {:class "text-green-600 font-bold"} "Plugin Loaded Successfully!"]
            :confirm-fix [:span {:class "text-red-600 font-bold"} "Generation Failed."]
            :error [:span {:class "text-red-600 font-bold"} "System Error."]
            nil)]
         
         [:div {:class "flex gap-2"}
          (when (= status :confirm-fix)
            [:button {:class "px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
                      :on-click #(core/fix-it! boot-api)}
             "Fix It (Auto)"])
          
          [:button {:class "px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
                    :disabled (or (str/blank? api-key) (str/blank? prompt) (#{:working :evaluating} status))
                    :on-click #(core/generate! boot-api prompt)}
           "Generate"]]]]]

      ;; Right Column: Code & Actions
      [:div {:class "space-y-6"}
       
       ;; Current Code
       (when current-code
         [:div {:class "bg-slate-900 rounded shadow-lg overflow-hidden"}
          [:div {:class "bg-slate-800 px-4 py-2 flex justify-between items-center"}
           [:span {:class "text-gray-300 text-xs font-bold"} "Generated Code"]
           (when (= status :success)
             [:button {:class "text-green-400 hover:text-white text-xs font-bold"
                       :on-click #(let [ns-str (core/extract-ns current-code)]
                                    (core/save-plugin! ns-str current-code))}
              "Save to LocalStorage"])]
          [:pre {:class "p-4 text-xs text-green-400 overflow-x-auto font-mono"}
           current-code]])

       ;; Error Display
       (when last-error
         [:div {:class "bg-red-50 border border-red-200 rounded p-4 text-red-800 text-sm font-mono whitespace-pre-wrap"}
          last-error])

       ;; Saved Plugins
       [:div {:class "bg-white rounded shadow-sm border border-gray-200 p-4"}
        [:h3 {:class "font-bold text-gray-700 mb-4"} "Saved Plugins"]
        [saved-plugins-list boot-api]]]]]))
