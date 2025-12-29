(ns plinpt.p-ai-creator.core
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [promesa.core :as p]))

;; --- State ---

(defonce state (r/atom {:api-key (or (js/localStorage.getItem "plin-ai-key") "")
                        :prompt ""
                        :messages []
                        :status :idle ;; :idle, :working, :success, :error, :confirm-fix
                        :current-code nil
                        :last-error nil
                        :saved-plugins (try (js/JSON.parse (js/localStorage.getItem "plin-saved-plugins"))
                                            (catch :default _ {}))}))

(defn set-api-key! [key]
  (js/localStorage.setItem "plin-ai-key" key)
  (swap! state assoc :api-key key))

(defn save-plugin! [id code]
  (let [saved (:saved-plugins @state)
        new-saved (assoc saved id {:code code :timestamp (js/Date.now)})]
    (js/localStorage.setItem "plin-saved-plugins" (js/JSON.stringify (clj->js new-saved)))
    (swap! state assoc :saved-plugins new-saved)))

(defn delete-plugin! [id]
  (let [saved (:saved-plugins @state)
        new-saved (dissoc saved (name id) id)] ;; Handle string/keyword keys
    (js/localStorage.setItem "plin-saved-plugins" (js/JSON.stringify (clj->js new-saved)))
    (swap! state assoc :saved-plugins new-saved)))

;; --- Context Gathering ---

(defn fetch-readme []
  (p/create
   (fn [resolve reject]
     (let [xhr (js/XMLHttpRequest.)]
       (.open xhr "GET" "README.org")
       (.send xhr)
       (set! (.-onload xhr) #(if (= (.-status xhr) 200)
                               (resolve (.-responseText xhr))
                               (resolve "README not found.")))
       (set! (.-onerror xhr) #(resolve "Error fetching README."))))))

(defn format-plugin-def [p]
  (let [beans-meta (reduce-kv (fn [m k v]
                                (assoc m k (select-keys (meta v) [:doc :api])))
                              {}
                              (:beans p))]
    (str "Plugin ID: " (:id p) "\n"
         "Doc: " (:doc p) "\n"
         "Extensions: " (pr-str (:extensions p)) "\n"
         "Beans (API): " (pr-str beans-meta) "\n")))

(defn get-system-context [all-plugins]
  (let [interfaces (filter #(str/starts-with? (namespace (:id %)) "plinpt.i-") all-plugins)
        formatted (str/join "\n---\n" (map format-plugin-def interfaces))]
    formatted))

(defn build-system-prompt [readme context]
  (str "You are an expert ClojureScript developer using the PLIN architecture.\n"
       "Your task is to write a PLIN plugin based on the user's request.\n\n"
       "### Architecture Overview (README)\n"
       readme "\n\n"
       "### Available Interfaces (API)\n"
       context "\n\n"
       "### Instructions\n"
       "1. Output ONLY the ClojureScript code for the plugin. No markdown, no explanations.\n"
       "2. The code must start with `(ns ...)` and define `(def plugin ...)`.\n"
       "3. Use `plinpt.i-*` namespaces for dependencies. Do NOT depend on `p-*` implementations.\n"
       "4. Ensure you use `[:= ...]` for values and `[:= (fn ...)]` or `[partial ...]` for functions/components in beans.\n"
       "5. Do not use external npm libraries unless they are standard (React, Reagent).\n"
       "6. If the user asks for a UI component, make sure it is a Reagent component.\n"
       "7. The plugin ID is automatic, do not specify `:id` in the map.\n"))

;; --- LLM Interaction ---

(defn call-llm [messages api-key]
  (p/create
   (fn [resolve reject]
     (let [xhr (js/XMLHttpRequest.)
           payload {:model "openrouter/google/gemini-3-preview"
                    :messages messages}]
       (.open xhr "POST" "https://openrouter.ai/api/v1/chat/completions")
       (.setRequestHeader xhr "Authorization" (str "Bearer " api-key))
       (.setRequestHeader xhr "Content-Type" "application/json")
       (.send xhr (js/JSON.stringify (clj->js payload)))
       (set! (.-onload xhr)
             (fn []
               (if (= (.-status xhr) 200)
                 (let [resp (js/JSON.parse (.-responseText xhr))
                       content (.. resp -choices (0) -message -content)]
                   (resolve content))
                 (reject (str "LLM Error: " (.-responseText xhr))))))
       (set! (.-onerror xhr) #(reject "Network Error"))))))

(defn extract-code [text]
  (let [clean (str/replace text #"^```clojure\n" "")
        clean (str/replace clean #"^```\n" "")
        clean (str/replace clean #"\n```$" "")]
    clean))

(defn extract-ns [source]
  (second (re-find #"\(\s*ns\s+([\w\.\-]+)" source)))

;; --- Main Flow ---

(defn generate! [boot-api user-prompt]
  (let [{:keys [api-key messages]} @state
        {:keys [state]} (boot-api)
        all-plugins (:all-plugins @state)]
    
    (swap! state assoc :status :working :last-error nil)
    
    (p/let [readme (fetch-readme)
            context (get-system-context all-plugins)
            sys-prompt (build-system-prompt readme context)
            
            initial-msgs (if (empty? messages)
                           [{:role "system" :content sys-prompt}]
                           messages)
            new-msgs (conj initial-msgs {:role "user" :content user-prompt})]
      
      (swap! state assoc :messages new-msgs)
      
      (-> (call-llm new-msgs api-key)
          (p/then (fn [response]
                    (let [code (extract-code response)
                          msgs-with-reply (conj new-msgs {:role "assistant" :content response})]
                      (swap! state assoc 
                             :messages msgs-with-reply
                             :current-code code
                             :status :evaluating)
                      
                      ;; Try to Eval
                      (try
                        (js/scittle.core.eval_string code)
                        (let [ns-str (extract-ns code)
                              plugin-def (js/scittle.core.eval_string (str ns-str "/plugin"))
                              register! (:register-plugin! (boot-api))]
                          
                          (-> (register! plugin-def)
                              (p/then (fn []
                                        (swap! state assoc :status :success)
                                        ;; Auto-save to local storage as draft?
                                        ;; Maybe wait for user to click save.
                                        ))
                              (p/catch (fn [e]
                                         (swap! state assoc 
                                                :status :confirm-fix 
                                                :last-error (str "Registration Error: " e))))))
                        (catch :default e
                          (swap! state assoc 
                                 :status :confirm-fix 
                                 :last-error (str "Eval Error: " (.-message e))))))))
          (p/catch (fn [e]
                     (swap! state assoc :status :error :last-error (str e))))))))

(defn fix-it! [boot-api]
  (let [{:keys [last-error]} @state
        prompt (str "The code you provided failed to load with the following error:\n"
                    last-error "\n\n"
                    "Please fix the code and output the full corrected file.")]
    (generate! boot-api prompt)))
(ns plinpt.p-ai-creator.ui
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plinpt.p-ai-creator.core :as core]))

(defn icon-magic []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"}]])

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
         [:div
          (case status
            :working [:span {:class "text-blue-600 animate-pulse"} "Thinking & Generating..."]
            :evaluating [:span {:class "text-purple-600 animate-pulse"} "Compiling & Loading..."]
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
(ns plinpt.p-ai-creator
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-application :as iapp]
            [plinpt.i-devtools :as devtools]
            [plinpt.p-ai-creator.ui :as ui]))

(def plugin
  (plin/plugin
   {:doc "AI Plugin Creator."
    :deps [iapp/plugin devtools/plugin boot/plugin]
    
    :contributions
    {::iapp/routes [::route]
     ::devtools/items [{:title "AI Plugin Creator"
                        :description "Generate plugins using AI."
                        :icon ui/icon-magic
                        :color-class "bg-purple-600"
                        :href "/development/ai-creator"
                        :order 10}]}

    :beans
    {::route
     ^{:doc "Route for AI Creator."
       :api {:ret :map}}
     [(fn [boot-api]
        {:path "/development/ai-creator"
         :component (partial ui/page boot-api)})
      ::boot/api]}}))
