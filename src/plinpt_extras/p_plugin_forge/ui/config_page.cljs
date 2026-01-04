(ns plinpt-extras.p-plugin-forge.ui.config-page
  "Settings tab interface."
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plinpt-extras.p-plugin-forge.core :as core]
            [plinpt-extras.p-plugin-forge.config :as config]
            [plinpt-extras.p-plugin-forge.prompt :as prompt]))

(defonce local-state (r/atom {:testing? false
                              :test-result nil
                              :show-advanced? false
                              :show-default-prompt? false
                              :custom-model-input ""}))

(defn test-connection! []
  (swap! local-state assoc :testing? true :test-result nil)
  (-> (config/test-connection!)
      (.then (fn [result]
               (swap! local-state assoc :testing? false :test-result result)))))

(defn input-field [{:keys [label value on-change type placeholder help]}]
  [:div {:class "mb-4"}
   [:label {:class "block text-sm font-medium text-slate-700 mb-1"} label]
   [:input {:class "w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            :type (or type "text")
            :value (or value "")
            :placeholder placeholder
            :on-change #(on-change (.. % -target -value))}]
   (when help
     [:p {:class "mt-1 text-xs text-slate-500"} help])])

(defn model-selector []
  (let [current-model (core/get-model)
        custom-input (:custom-model-input @local-state)
        is-custom? (not (some #(= (:id %) current-model) config/available-models))]
    [:div {:class "mb-4"}
     [:label {:class "block text-sm font-medium text-slate-700 mb-1"} "Model"]
     
     ;; Dropdown for preset models
     [:select {:class "w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent mb-2"
               :value (if is-custom? "__custom__" current-model)
               :on-change (fn [e]
                            (let [v (.. e -target -value)]
                              (if (= v "__custom__")
                                (do
                                  (swap! local-state assoc :custom-model-input current-model)
                                  ;; Keep current model until user types a new one
                                  )
                                (do
                                  (core/set-config! :model v)
                                  (swap! local-state assoc :custom-model-input "")))))}
      (for [{:keys [id name input-cost output-cost]} config/available-models]
        ^{:key id}
        [:option {:value id}
         (str name " ($" input-cost "/$" output-cost " per 1M tokens)")])
      [:option {:value "__custom__"} "Custom model..."]]
     
     ;; Custom model input field (always visible for editing)
     [:div {:class "flex gap-2"}
      [:input {:class "flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
               :type "text"
               :value (if is-custom? current-model (or custom-input ""))
               :placeholder "Enter custom model ID (e.g., provider/model-name)"
               :on-change (fn [e]
                            (let [v (.. e -target -value)]
                              (swap! local-state assoc :custom-model-input v)))
               :on-blur (fn [e]
                          (let [v (str/trim (.. e -target -value))]
                            (when (seq v)
                              (core/set-config! :model v))))
               :on-key-down (fn [e]
                              (when (= (.-key e) "Enter")
                                (let [v (str/trim (.. e -target -value))]
                                  (when (seq v)
                                    (core/set-config! :model v)
                                    (.blur (.-target e))))))}]
      (when (seq (or custom-input (when is-custom? current-model)))
        [:button {:class "px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
                  :on-click (fn []
                              (let [v (str/trim (or (:custom-model-input @local-state) ""))]
                                (when (seq v)
                                  (core/set-config! :model v))))}
         "Set"])]
     
     [:p {:class "mt-1 text-xs text-slate-500"}
      "Select a preset model or enter any OpenRouter-compatible model ID"]]))

(defn llm-config-section []
  (let [cfg (core/get-config)
        {:keys [testing? test-result]} @local-state]
    [:div {:class "bg-white rounded-xl border border-slate-200 p-6 mb-6"}
     [:h2 {:class "text-lg font-semibold text-slate-900 mb-4"} "LLM Configuration"]

     [input-field {:label "API Key"
                   :value (:api-key cfg)
                   :type "password"
                   :placeholder "sk-or-..."
                   :on-change #(core/set-config! :api-key %)
                   :help "Your OpenRouter API key. Get one at openrouter.ai"}]

     [input-field {:label "Endpoint URL"
                   :value (:endpoint cfg)
                   :placeholder "https://openrouter.ai/api/v1/chat/completions"
                   :on-change #(core/set-config! :endpoint %)
                   :help "OpenRouter-compatible API endpoint"}]

     [model-selector]

     [:div {:class "flex items-center gap-4 mt-6"}
      [:button
       {:class (str "px-4 py-2 rounded-lg font-medium transition-colors "
                    (if testing?
                      "bg-slate-300 text-slate-500 cursor-not-allowed"
                      "bg-blue-600 text-white hover:bg-blue-700"))
        :disabled testing?
        :on-click test-connection!}
       (if testing? "Testing..." "Test Connection")]

      (when test-result
        (if (:success test-result)
          [:span {:class "flex items-center gap-2 text-green-600"}
           [:svg {:class "w-5 h-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                    :d "M5 13l4 4L19 7"}]]
           "Connection successful!"]
          [:span {:class "flex items-center gap-2 text-red-600"}
           [:svg {:class "w-5 h-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                    :d "M6 18L18 6M6 6l12 12"}]]
           (:error test-result)]))]]))

(defn default-prompt-modal []
  (let [show? (:show-default-prompt? @local-state)]
    (when show?
      [:div {:class "fixed inset-0 z-50 flex items-center justify-center p-4"}
       ;; Backdrop
       [:div {:class "absolute inset-0 bg-black/50"
              :on-click #(swap! local-state assoc :show-default-prompt? false)}]
       ;; Modal
       [:div {:class "relative bg-white rounded-xl shadow-2xl max-w-4xl w-full max-h-[80vh] flex flex-col"}
        ;; Header
        [:div {:class "flex items-center justify-between px-6 py-4 border-b border-slate-200"}
         [:h3 {:class "text-lg font-semibold text-slate-900"} "Default System Prompt"]
         [:button {:class "p-2 hover:bg-slate-100 rounded-lg transition-colors"
                   :on-click #(swap! local-state assoc :show-default-prompt? false)}
          [:svg {:class "w-5 h-5 text-slate-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                   :d "M6 18L18 6M6 6l12 12"}]]]]
        ;; Content
        [:div {:class "flex-1 overflow-y-auto p-6"}
         [:pre {:class "text-sm text-slate-700 whitespace-pre-wrap font-mono bg-slate-50 p-4 rounded-lg"}
          (prompt/get-default-prompt)]]
        ;; Footer
        [:div {:class "px-6 py-4 border-t border-slate-200 flex justify-end"}
         [:button {:class "px-4 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors"
                   :on-click #(swap! local-state assoc :show-default-prompt? false)}
          "Close"]]]])))

(defn advanced-section []
  (let [show? (:show-advanced? @local-state)
        custom-prompt (core/get-system-prompt)]
    [:div {:class "bg-white rounded-xl border border-slate-200 p-6"}
     [:button {:class "flex items-center justify-between w-full text-left"
               :on-click #(swap! local-state update :show-advanced? not)}
      [:h2 {:class "text-lg font-semibold text-slate-900"} "Advanced Settings"]
      [:svg {:class (str "w-5 h-5 text-slate-400 transition-transform "
                         (when show? "rotate-180"))
             :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
               :d "M19 9l-7 7-7-7"}]]]

     (when show?
       [:div {:class "mt-4"}
        [:label {:class "block text-sm font-medium text-slate-700 mb-1"}
         "Custom System Prompt"]
        [:p {:class "text-xs text-slate-500 mb-2"}
         "Customize the instructions sent to the LLM. Leave empty to use the default prompt."]
        [:textarea
         {:class "w-full h-64 rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          :value (or custom-prompt "")
          :placeholder "Leave empty to use the default system prompt..."
          :on-change #(core/set-config! :system-prompt (.. % -target -value))}]
        [:div {:class "flex justify-between mt-2"}
         [:button
          {:class "text-sm text-blue-600 hover:text-blue-700"
           :on-click #(swap! local-state assoc :show-default-prompt? true)}
          "View Default Prompt"]
         [:button
          {:class "text-sm text-red-600 hover:text-red-700"
           :on-click #(core/set-config! :system-prompt nil)}
          "Reset to Default"]]])]))

(defn config-page []
  [:div
   [llm-config-section]
   [advanced-section]
   [default-prompt-modal]])
