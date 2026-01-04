(ns plinpt-extras.p-plugin-forge.ui.chat-page
  "Chat tab interface."
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plinpt-extras.p-plugin-forge.core :as core]
            [plinpt-extras.p-plugin-forge.config :as config]
            [plinpt-extras.p-plugin-forge.prompt :as prompt]))

(defonce input-state (r/atom ""))
(defonce loading-plugin? (r/atom false))
(defonce messages-container-ref (atom nil))

;; Track expanded state for plugin blocks by message index
(defonce expanded-plugins (r/atom #{}))

(defn scroll-to-bottom! []
  (when-let [container @messages-container-ref]
    (set! (.-scrollTop container) (.-scrollHeight container))))

(defn format-timestamp [ts]
  (let [date (js/Date. ts)]
    (.toLocaleTimeString date)))

(defn extract-plugin-code
  "Extract plugin code from message content between markers."
  [content]
  (let [start-marker "===PLUGIN_START==="
        end-marker "===PLUGIN_END==="
        start-idx (str/index-of content start-marker)
        end-idx (str/index-of content end-marker)]
    (when (and start-idx end-idx (< start-idx end-idx))
      (-> content
          (subs (+ start-idx (count start-marker)) end-idx)
          str/trim))))

(defn extract-namespace
  "Extract namespace from plugin code."
  [code]
  (when code
    (let [ns-match (re-find #"\(\s*ns\s+([\w\.\-]+)" code)]
      (second ns-match))))

(defn has-plugin-markers?
  "Check if content contains plugin markers."
  [content]
  (and (str/includes? content "===PLUGIN_START===")
       (str/includes? content "===PLUGIN_END===")))

(defn has-partial-plugin-start?
  "Check if content has started a plugin block but not finished."
  [content]
  (and (str/includes? content "===PLUGIN_START===")
       (not (str/includes? content "===PLUGIN_END==="))))

(defn split-content-around-plugin
  "Split content into parts: before plugin, plugin code, after plugin."
  [content]
  (let [start-marker "===PLUGIN_START==="
        end-marker "===PLUGIN_END==="
        start-idx (str/index-of content start-marker)
        end-idx (str/index-of content end-marker)]
    (if (and start-idx end-idx (< start-idx end-idx))
      {:before (subs content 0 start-idx)
       :plugin (-> content
                   (subs (+ start-idx (count start-marker)) end-idx)
                   str/trim)
       :after (subs content (+ end-idx (count end-marker)))}
      {:before content
       :plugin nil
       :after ""})))

(defn syntax-highlight
  "Apply syntax highlighting to ClojureScript code.
   Returns HTML string with span elements for different token types.
   Base text color should be light (set via parent element)."
  [code]
  (-> code
      ;; Escape HTML first
      (str/replace #"&" "&amp;")
      (str/replace #"<" "&lt;")
      (str/replace #">" "&gt;")
      ;; Comments - gray/muted
      (str/replace #"(;[^\n]*)" "<span class=\"text-slate-500\">$1</span>")
      ;; Strings - green
      (str/replace #"(\"(?:[^\"\\]|\\.)*\")" "<span class=\"text-emerald-400\">$1</span>")
      ;; Keywords - cyan
      (str/replace #"(:[a-zA-Z0-9\-_\.\/\*\+\!\?]+)" "<span class=\"text-cyan-400\">$1</span>")
      ;; Special forms and macros - purple/magenta
      (str/replace #"\b(ns|def|defn|defonce|defn-|fn|let|if|when|cond|case|do|loop|recur|try|catch|throw|for|doseq|dotimes|require|import|use|partial|atom|swap!|reset!|deref|and|or|not)\b" 
                   "<span class=\"text-fuchsia-400 font-semibold\">$1</span>")
      ;; Boolean and nil - orange
      (str/replace #"\b(true|false|nil)\b" "<span class=\"text-orange-400\">$1</span>")
      ;; Numbers - orange/yellow
      (str/replace #"\b(\d+\.?\d*)\b" "<span class=\"text-amber-300\">$1</span>")))

(defn code-block [code]
  [:pre {:class "bg-slate-900 text-slate-300 p-4 rounded-lg overflow-x-auto text-xs font-mono mt-2"}
   [:code code]])

(defn parse-content-simple
  "Parse message content and render code blocks (for non-plugin code)."
  [content]
  (let [parts (str/split content #"```(?:clojure|cljs)?\n?")]
    (if (= 1 (count parts))
      [:span {:class "whitespace-pre-wrap text-sm"} content]
      [:div
       (map-indexed
        (fn [idx part]
          (if (even? idx)
            ^{:key idx} [:span {:class "whitespace-pre-wrap text-sm"} part]
            ^{:key idx} [code-block (str/trim part)]))
        parts)])))

(defn load-plugin! [code on-success]
  (reset! loading-plugin? true)
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
                         (reset! loading-plugin? false)
                         (when on-success (on-success))))
                (.then nil
                       (fn [err]
                         (js/console.error "Failed to register plugin:" err)
                         (core/set-hot-load-error! (str "Registration failed: " (.-message err)))
                         (core/set-hot-loaded! (keyword ns-str) plugin-def code)
                         (reset! loading-plugin? false))))
            (do
              (js/alert "Could not find 'plugin' var in namespace")
              (reset! loading-plugin? false))))
        (do
          (js/alert "Could not extract namespace from code")
          (reset! loading-plugin? false))))
    (catch :default e
      (core/set-hot-load-error! (.-message e))
      (js/console.error "Failed to load plugin:" e)
      (reset! loading-plugin? false))))

(defn plugin-block
  "Collapsible plugin code block component."
  [{:keys [code expanded? on-toggle streaming? msg-key]}]
  (let [ns-name (extract-namespace code)
        loading? @loading-plugin?
        line-count (count (str/split-lines code))]
    [:div {:class "my-3 rounded-lg overflow-hidden border border-slate-700 bg-slate-900"}
     ;; Header - always visible
     [:div {:class "flex items-center justify-between px-3 py-2 bg-slate-800 border-b border-slate-700"}
      [:div {:class "flex items-center gap-2"}
       ;; Plugin icon
       [:div {:class "p-1 bg-purple-600 rounded"}
        [:svg {:class "w-4 h-4 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                 :d "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z"}]]]
       [:div
        [:span {:class "text-sm font-medium text-white"} "Plugin"]
        (when ns-name
          [:span {:class "ml-2 text-xs text-slate-400 font-mono"} ns-name])]]
      
      [:div {:class "flex items-center gap-2"}
       ;; Line count badge
       [:span {:class "text-xs text-slate-500"}
        (str line-count " lines")]
       
       ;; Streaming indicator
       (when streaming?
         [:span {:class "flex items-center gap-1 text-xs text-blue-400"}
          [:span {:class "w-2 h-2 bg-blue-400 rounded-full animate-pulse"}]
          "Generating..."])
       
       ;; Expand/collapse button
       (when-not streaming?
         [:button {:class "p-1 hover:bg-slate-700 rounded transition-colors"
                   :on-click on-toggle}
          [:svg {:class (str "w-4 h-4 text-slate-400 transition-transform "
                             (when expanded? "rotate-180"))
                 :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                   :d "M19 9l-7 7-7-7"}]]])]]
     
     ;; Code content - collapsible
     ;; IMPORTANT: text-slate-300 sets the base/default text color for unhighlighted code
     (when (or expanded? streaming?)
       [:div {:class "max-h-96 overflow-auto"}
        [:pre {:class "p-3 text-xs font-mono leading-relaxed text-slate-300"}
         [:code {:dangerouslySetInnerHTML {:__html (syntax-highlight code)}}]]])
     
     ;; Footer with Load button
     [:div {:class "flex items-center justify-between px-3 py-2 bg-slate-800 border-t border-slate-700"}
      [:div {:class "flex items-center gap-2 text-xs text-slate-500"}
       [:svg {:class "w-3 h-3" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
        [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                :d "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]]
       "Click Load to test this plugin"]
      
      [:button
       {:class (str "flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded transition-colors "
                    (cond
                      streaming? "bg-slate-700 text-slate-500 cursor-not-allowed"
                      loading? "bg-green-700 text-green-200 cursor-not-allowed"
                      :else "bg-green-600 text-white hover:bg-green-500"))
        :disabled (or streaming? loading?)
        :on-click #(load-plugin! code nil)}
       (cond
         loading?
         [:<>
          [:svg {:class "w-3 h-3 animate-spin" :fill "none" :viewBox "0 0 24 24"}
           [:circle {:class "opacity-25" :cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
           [:path {:class "opacity-75" :fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
          "Loading..."]
         
         streaming?
         "Generating..."
         
         :else
         [:<>
          [:svg {:class "w-3 h-3" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                   :d "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"}]]
          "Load Plugin"])]]]))

(defn streaming-plugin-block
  "Plugin block for streaming content (always expanded)."
  [code]
  [plugin-block {:code code
                 :expanded? true
                 :streaming? true
                 :on-toggle #()}])

(defn parse-content-with-plugin
  "Parse message content, extracting plugin blocks specially."
  [content msg-key]
  (let [{:keys [before plugin after]} (split-content-around-plugin content)
        expanded? (contains? @expanded-plugins msg-key)]
    [:div
     ;; Content before plugin
     (when (seq (str/trim before))
       [parse-content-simple before])
     
     ;; Plugin block
     (when plugin
       [plugin-block {:code plugin
                      :expanded? expanded?
                      :streaming? false
                      :msg-key msg-key
                      :on-toggle #(swap! expanded-plugins
                                         (fn [s]
                                           (if (contains? s msg-key)
                                             (disj s msg-key)
                                             (conj s msg-key))))}])
     
     ;; Content after plugin
     (when (seq (str/trim after))
       [parse-content-simple after])]))

(defn user-message [{:keys [content timestamp]}]
  [:div {:class "flex justify-end mb-4"}
   [:div {:class "max-w-[80%] bg-blue-600 text-white px-4 py-3 rounded-2xl rounded-br-md"}
    [:p {:class "whitespace-pre-wrap text-sm"} content]
    [:p {:class "text-xs text-blue-200 mt-1"} (format-timestamp timestamp)]]])

(defn assistant-message [{:keys [content timestamp]} msg-key]
  [:div {:class "flex justify-start mb-4"}
   [:div {:class "max-w-[85%] bg-slate-100 text-slate-900 px-4 py-3 rounded-2xl rounded-bl-md"}
    (if (has-plugin-markers? content)
      [parse-content-with-plugin content msg-key]
      [parse-content-simple content])
    (when timestamp
      [:p {:class "text-xs text-slate-400 mt-2"} (format-timestamp timestamp)])]])

(defn streaming-message []
  (let [content (core/get-current-stream)]
    (when (seq content)
      [:div {:class "flex justify-start mb-4"}
       [:div {:class "max-w-[85%] bg-slate-100 text-slate-900 px-4 py-3 rounded-2xl rounded-bl-md"}
        (cond
          ;; Has complete plugin
          (has-plugin-markers? content)
          (let [{:keys [before plugin after]} (split-content-around-plugin content)]
            [:div
             (when (seq (str/trim before))
               [parse-content-simple before])
             (when plugin
               [plugin-block {:code plugin
                              :expanded? true
                              :streaming? false
                              :on-toggle #()}])
             (when (seq (str/trim after))
               [:div
                [parse-content-simple after]
                [:span {:class "inline-block w-2 h-4 bg-blue-500 animate-pulse ml-1"}]])])
          
          ;; Has partial plugin (still generating)
          (has-partial-plugin-start? content)
          (let [start-marker "===PLUGIN_START==="
                start-idx (str/index-of content start-marker)
                before (subs content 0 start-idx)
                partial-code (-> content
                                 (subs (+ start-idx (count start-marker)))
                                 str/trim)]
            [:div
             (when (seq (str/trim before))
               [parse-content-simple before])
             [streaming-plugin-block partial-code]])
          
          ;; Regular content
          :else
          [:div
           [parse-content-simple content]
           [:span {:class "inline-block w-2 h-4 bg-blue-500 animate-pulse ml-1"}]])]])))

(defn empty-state []
  [:div {:class "flex flex-col items-center justify-center h-full text-center p-8"}
   [:div {:class "p-4 bg-slate-100 rounded-full mb-4"}
    [:svg {:class "w-12 h-12 text-slate-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "1.5"
             :d "M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"}]]]
   [:h3 {:class "text-lg font-semibold text-slate-700 mb-2"} "Start a conversation"]
   [:p {:class "text-slate-500 max-w-md text-sm"}
    "Describe the plugin you want to create. For example: "
    [:span {:class "italic"} "\"Create a plugin that shows a countdown timer\""]]])

(defn api-key-warning []
  [:div {:class "bg-amber-50 border border-amber-200 rounded-lg p-4 mb-4"}
   [:div {:class "flex items-start gap-3"}
    [:svg {:class "w-5 h-5 text-amber-500 mt-0.5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
             :d "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"}]]
    [:div
     [:p {:class "font-medium text-amber-800"} "API Key Required"]
     [:p {:class "text-sm text-amber-700 mt-1"}
      "Please configure your OpenRouter API key in the Settings tab to start chatting."]]]])

(defn message-list []
  (let [messages (core/get-messages)
        streaming? (core/streaming?)
        current-stream (core/get-current-stream)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (scroll-to-bottom!))
      
      :component-did-update
      (fn [this old-argv]
        ;; Auto-scroll when messages change or stream updates
        (scroll-to-bottom!))
      
      :reagent-render
      (fn []
        (let [messages (core/get-messages)
              streaming? (core/streaming?)]
          [:div {:class "flex-1 overflow-y-auto p-4"
                 :ref #(reset! messages-container-ref %)}
           (if (and (empty? messages) (not streaming?))
             [empty-state]
             [:div
              (for [[idx msg] (map-indexed vector messages)]
                ^{:key idx}
                (if (= :user (:role msg))
                  [user-message msg]
                  [assistant-message msg idx]))
              (when streaming?
                [streaming-message])])]))})))

(defn build-messages-for-api
  "Build the messages array for the API call, including system prompt and chat history."
  []
  (let [messages (core/get-messages)
        system-prompt (prompt/get-effective-prompt (core/get-system-prompt))]
    (into [{:role "system" :content system-prompt}]
          (map (fn [msg]
                 {:role (name (:role msg))
                  :content (:content msg)})
               messages))))

(defn process-stream-chunk [decoder chunk on-done on-usage]
  (let [text (.decode decoder chunk)
        lines (str/split text #"\n")]
    (doseq [line lines]
      (when (str/starts-with? line "data: ")
        (let [data (subs line 6)]
          (cond
            (= data "[DONE]")
            (on-done)
            
            (seq (str/trim data))
            (try
              (let [parsed (js->clj (js/JSON.parse data) :keywordize-keys true)
                    content (get-in parsed [:choices 0 :delta :content])
                    usage (:usage parsed)]
                (when content
                  (core/append-stream! content))
                (when usage
                  (on-usage usage)))
              (catch :default e
                (js/console.warn "Failed to parse chunk:" data)))))))))

(defn read-stream-recursive [reader decoder on-usage]
  (.then (.read reader)
         (fn [result]
           (if (.-done result)
             (do
               (core/finalize-stream!)
               (core/set-streaming! false))
             (do
               (process-stream-chunk decoder (.-value result)
                                     (fn []
                                       (core/finalize-stream!)
                                       (core/set-streaming! false))
                                     on-usage)
               (read-stream-recursive reader decoder on-usage))))))

(defn handle-usage! [usage]
  (let [model (core/get-model)
        input-tokens (or (:prompt_tokens usage) 0)
        output-tokens (or (:completion_tokens usage) 0)
        cost (config/calculate-cost model input-tokens output-tokens)]
    (when (pos? cost)
      (core/add-cost! cost))))

(defn handle-stream-response!
  "Process a streaming response from the API."
  [response]
  (let [reader (.getReader (.-body response))
        decoder (js/TextDecoder.)]
    (core/set-streaming! true)
    (core/clear-stream!)
    (read-stream-recursive reader decoder handle-usage!)))

(defn handle-api-error! [response]
  (.then (.json response)
         (fn [data]
           (let [error-msg (or (when-let [err (.-error data)]
                                 (.-message err))
                               (str "HTTP " (.-status response)))]
             (js/console.error "API Error:" error-msg)
             (core/add-message! :assistant (str "Error: " error-msg))
             (core/set-streaming! false)))))

(defn handle-fetch-error! [err]
  (js/console.error "Fetch error:" err)
  (core/add-message! :assistant (str "Connection error: " (.-message err)))
  (core/set-streaming! false))

(defn call-llm!
  "Send the current conversation to the LLM API."
  []
  (let [cfg (core/get-config)
        messages (build-messages-for-api)]
    (core/set-streaming! true)
    (-> (js/fetch (:endpoint cfg)
                  #js {:method "POST"
                       :headers #js {"Content-Type" "application/json"
                                     "Authorization" (str "Bearer " (:api-key cfg))}
                       :body (js/JSON.stringify
                              (clj->js {:model (:model cfg)
                                        :messages messages
                                        :stream true
                                        :stream_options {:include_usage true}}))})
        (.then (fn [response]
                 (if (.-ok response)
                   (handle-stream-response! response)
                   (handle-api-error! response)))
               (fn [err]
                 (handle-fetch-error! err))))))

(defn send-message! []
  (let [content (str/trim @input-state)]
    (when (and (seq content) 
               (not (core/streaming?))
               (config/api-key-configured?))
      (core/add-message! :user content)
      (reset! input-state "")
      (call-llm!))))

(defn chat-header []
  (let [messages (core/get-messages)
        has-messages? (seq messages)]
    [:div {:class "flex items-center justify-between px-4 py-2 border-b border-slate-200"}
     [:span {:class "text-sm text-slate-500"}
      (if has-messages?
        (str (count messages) " messages")
        "New conversation")]
     (when has-messages?
       [:button
        {:class "flex items-center gap-1.5 px-3 py-1.5 text-sm text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors"
         :on-click #(when (js/confirm "Start a new chat? Current messages will be cleared.")
                      (core/clear-chat!))}
        [:svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                 :d "M12 4v16m8-8H4"}]]
        "New Chat"])]))

(defn input-area []
  (let [streaming? (core/streaming?)
        api-configured? (config/api-key-configured?)]
    [:div {:class "border-t border-slate-200 p-4"}
     [:div {:class "flex gap-2"}
      [:textarea
       {:class "flex-1 resize-none rounded-lg border border-slate-300 px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        :rows 2
        :placeholder (if api-configured?
                       "Describe the plugin you want to create..."
                       "Configure your API key in Settings first...")
        :value @input-state
        :disabled (or streaming? (not api-configured?))
        :on-change #(reset! input-state (.. % -target -value))
        :on-key-down (fn [e]
                       (when (and (= (.-key e) "Enter")
                                  (not (.-shiftKey e)))
                         (.preventDefault e)
                         (send-message!)))}]
      [:button
       {:class (str "px-4 py-2 rounded-lg font-medium transition-colors "
                    (if (or streaming? (not api-configured?))
                      "bg-slate-300 text-slate-500 cursor-not-allowed"
                      "bg-blue-600 text-white hover:bg-blue-700"))
        :disabled (or streaming? (not api-configured?))
        :on-click send-message!}
       (if streaming?
         [:svg {:class "w-5 h-5 animate-spin" :fill "none" :viewBox "0 0 24 24"}
          [:circle {:class "opacity-25" :cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
          [:path {:class "opacity-75" :fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
         [:svg {:class "w-5 h-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                  :d "M12 19l9 2-9-18-9 18 9-2zm0 0v-8"}]])]]]))

(defn chat-page []
  [:div {:class "flex flex-col h-[calc(100vh-280px)] bg-white rounded-xl border border-slate-200"}
   (when-not (config/api-key-configured?)
     [api-key-warning])
   [chat-header]
   [message-list]
   [input-area]])
