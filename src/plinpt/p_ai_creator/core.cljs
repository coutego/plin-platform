(ns plinpt.p-ai-creator.core
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [promesa.core :as p]))

;; --- State ---

(defn safe-load-plugins []
  (try
    (let [raw (js/localStorage.getItem "plin-saved-plugins")]
      (if (str/blank? raw)
        {}
        (let [parsed (js/JSON.parse raw)
              converted (js->clj parsed :keywordize-keys true)]
          (if (map? converted)
            converted
            {}))))
    (catch :default e
      (js/console.warn "Failed to load saved plugins, resetting." e)
      {})))

(defonce state (r/atom {:api-key (or (js/localStorage.getItem "plin-ai-key") "")
                        :prompt ""
                        :messages []
                        :status :idle ;; :idle, :working, :success, :error, :confirm-fix
                        :current-code nil
                        :last-error nil
                        :saved-plugins (safe-load-plugins)}))

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
           payload {:model "google/gemini-3-pro-preview"
                    :messages messages}]
       (.open xhr "POST" "https://openrouter.ai/api/v1/chat/completions")
       (.setRequestHeader xhr "Authorization" (str "Bearer " api-key))
       (.setRequestHeader xhr "Content-Type" "application/json")
       (.send xhr (js/JSON.stringify (clj->js payload)))
       (set! (.-onload xhr)
             (fn []
               (if (= (.-status xhr) 200)
                 (try
                   (let [resp (js/JSON.parse (.-responseText xhr))
                         content (-> resp .-choices (aget 0) .-message .-content)]
                     (resolve content))
                   (catch :default e
                     (reject (str "JSON Parse Error: " (.-message e)))))
                 (reject (str "LLM Error: " (.-responseText xhr))))))
       (set! (.-onerror xhr) #(reject "Network Error"))))))

(defn extract-code [text]
  (let [text (str/trim text)]
    ;; 1. Try to find a markdown code block
    (if-let [match (re-find #"```(?:clojure)?\s*([\s\S]*?)\s*```" text)]
      (second match)
      ;; 2. If no block, check if it looks like it starts with (ns ...)
      (if-let [start-index (str/index-of text "(ns ")]
        (subs text start-index)
        ;; 3. Fallback
        text))))

(defn extract-ns [source]
  (second (re-find #"\(\s*ns\s+([\w\.\-]+)" source)))

;; --- Main Flow ---

(defn generate! [boot-api user-prompt]
  (let [{:keys [api-key messages]} @state
        ;; Fix shadowing: use distinct names for system state
        boot-state (:state boot-api)
        all-plugins (:all-plugins @boot-state)]
    
    ;; Update the UI state (not the system state!)
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
                      (js/console.log "AI Creator: Extracted Code:" code)
                      (swap! state assoc 
                             :messages msgs-with-reply
                             :current-code code
                             :status :evaluating)
                      
                      ;; Try to Eval
                      (try
                        (js/scittle.core.eval_string code)
                        (let [ns-str (extract-ns code)
                              plugin-def (js/scittle.core.eval_string (str ns-str "/plugin"))
                              register! (:register-plugin! boot-api)]
                          
                          (-> (register! plugin-def)
                              (p/then (fn []
                                        (swap! state assoc :status :success)))
                              (p/catch (fn [e]
                                         (js/console.error "Registration failed:" e)
                                         (swap! state assoc 
                                                :status :confirm-fix 
                                                :last-error (str "Registration Error: " e))))))
                        (catch :default e
                          (js/console.error "Eval failed:" e)
                          (let [msg (or (.-message e) (str e))
                                data (.-data e)]
                            (swap! state assoc 
                                   :status :confirm-fix 
                                   :last-error (str "Eval Error: " msg "\n" (when data (pr-str data))))))))))
          (p/catch (fn [e]
                     (js/console.error "LLM Call failed:" e)
                     (swap! state assoc :status :error :last-error (str e))))))))

(defn fix-it! [boot-api]
  (let [{:keys [last-error]} @state
        prompt (str "The code you provided failed to load with the following error:\n"
                    last-error "\n\n"
                    "Please fix the code and output the full corrected file.")]
    (generate! boot-api prompt)))
