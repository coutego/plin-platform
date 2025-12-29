(ns plinpt.p-dynamic-loader.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(defonce state (r/atom {:loading? false :error nil :success nil}))

(defn icon-upload []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"}]])

(defn- extract-ns [source]
  (let [match (re-find #"\(\s*ns\s+([\w\.\-]+)" source)]
    (second match)))

(defn- handle-cljs-file [sys-api file-name source]
  (let [register-plugin! (:register-plugin! sys-api)]
    (try
      ;; 1. Extract Namespace
      (let [ns-str (extract-ns source)]
        (when-not ns-str (throw (ex-info "Could not find (ns ...) declaration" {})))
        
        ;; 2. Evaluate the code
        (js/scittle.core.eval_string source)
        
        ;; 3. Resolve the plugin var
        (let [plugin-def (js/scittle.core.eval_string (str ns-str "/plugin"))]
          (when-not plugin-def (throw (ex-info (str "Could not resolve " ns-str "/plugin") {})))
          
          ;; 4. Register via Injected API
          (-> (register-plugin! plugin-def)
              (.then (fn [] 
                       (swap! state assoc :loading? false :success (str "Successfully loaded plugin: " ns-str))))
              (.catch (fn [e] 
                        (swap! state assoc :loading? false :error (str "Reload failed: " e)))))))
      (catch :default e
        (js/console.error e)
        (swap! state assoc :loading? false :error (str "Error: " (.-message e)))))))

(defn- handle-file [sys-api handlers file]
  (let [reader (js/FileReader.)
        file-name (.-name file)
        extension (last (str/split file-name #"\."))]
    
    (set! (.-onload reader)
          (fn [e]
            (let [source (-> e .-target .-result)]
              (swap! state assoc :loading? true :error nil :success nil)
              
              ;; Find matching handler
              (if-let [handler-fn (some (fn [h] 
                                          (when (= (:extension h) extension)
                                            (:handler h)))
                                        handlers)]
                ;; Use registered handler
                (handler-fn sys-api file-name source)
                
                ;; Fallback for CLJS (or error if not supported)
                (if (= extension "cljs")
                  (handle-cljs-file sys-api file-name source)
                  (swap! state assoc :loading? false :error (str "No handler found for extension: ." extension)))))))
    (.readAsText reader file)))

(defn loader-page [sys-api handlers]
  (let [{:keys [error success loading?]} @state]
    [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
     [:div {:class "bg-white shadow sm:rounded-lg"}
      [:div {:class "px-4 py-5 sm:p-6"}
       [:h3 {:class "text-lg leading-6 font-medium text-gray-900"} "Dynamic Plugin Loader"]
       [:div {:class "mt-2 max-w-xl text-sm text-gray-500"}
        [:p "Upload a plugin file. Supported extensions: " 
         (str/join ", " (cons "cljs" (map :extension handlers)))]
        [:p {:class "mt-2 font-bold text-red-500"} "Warning: This code will be evaluated immediately in your browser."]]
       
       [:div {:class "mt-5"}
        [:label {:class "block text-sm font-medium text-gray-700"} "Plugin File"]
        [:div {:class "mt-1 flex justify-center px-6 pt-5 pb-6 border-2 border-gray-300 border-dashed rounded-md hover:bg-gray-50 transition-colors"}
         [:div {:class "space-y-1 text-center"}
          [:svg {:class "mx-auto h-12 w-12 text-gray-400" :stroke "currentColor" :fill "none" :viewBox "0 0 48 48" :aria-hidden "true"}
           [:path {:d "M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"}]]
          [:div {:class "flex text-sm text-gray-600"}
           [:label {:for "file-upload" :class "relative cursor-pointer bg-white rounded-md font-medium text-blue-600 hover:text-blue-500 focus-within:outline-none focus-within:ring-2 focus-within:ring-offset-2 focus-within:ring-blue-500"}
            [:span "Upload a file"]
            [:input {:id "file-upload" 
                     :name "file-upload" 
                     :type "file" 
                     :class "sr-only"
                     ;; Accept all supported extensions
                     :accept (str ".cljs," (str/join "," (map #(str "." (:extension %)) handlers)))
                     :on-change (fn [e]
                                  (let [target (.-target e)
                                        file (-> target .-files (aget 0))]
                                    (handle-file sys-api handlers file)
                                    ;; Reset value to allow re-uploading the same file
                                    (set! (.-value target) "")))}]]
           [:p {:class "pl-1"} "or drag and drop"]]
          [:p {:class "text-xs text-gray-500"} "Up to 1MB"]]]]

       (when loading?
         [:div {:class "mt-4 flex items-center text-blue-600"}
          [:svg {:class "animate-spin -ml-1 mr-3 h-5 w-5 text-blue-600" :xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"}
           [:circle {:class "opacity-25" :cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
           [:path {:class "opacity-75" :fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
          "Processing Plugin..."])
       
       (when error
         [:div {:class "mt-4 p-4 bg-red-50 text-red-700 rounded border border-red-200 flex items-start"}
          [:svg {:class "h-5 w-5 text-red-400 mr-2" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]]
          [:span error]])
       
       (when success
         [:div {:class "mt-4 p-4 bg-green-50 text-green-700 rounded border border-green-200 flex items-start"}
          [:svg {:class "h-5 w-5 text-green-400 mr-2" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M5 13l4 4L19 7"}]]
          [:span success]])]]]))
