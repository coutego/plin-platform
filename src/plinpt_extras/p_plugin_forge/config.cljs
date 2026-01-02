(ns plinpt-extras.p-plugin-forge.config
  "Configuration management for Plugin Forge."
  (:require [plinpt-extras.p-plugin-forge.core :as core]))

(def available-models
  "List of available models with their pricing (per 1M tokens)."
  [{:id "google/gemini-3-pro-preview"
    :name "Gemini 3 Pro Preview"
    :input-cost 1.25
    :output-cost 5}
   {:id "anthropic/claude-opus-4.5"
    :name "Claude Opus 4.5"
    :input-cost 15
    :output-cost 75}
   {:id "openai/gpt-5.2-pro"
    :name "GPT-5.2 Pro"
    :input-cost 5
    :output-cost 15}
   {:id "z-ai/glm-4.7"
    :name "GLM 4.7"
    :input-cost 0.5
    :output-cost 2}
   {:id "minimax/minimax-m2.1"
    :name "MiniMax M2.1"
    :input-cost 0.2
    :output-cost 1}
   {:id "deepseek/deepseek-v3.2"
    :name "DeepSeek V3.2"
    :input-cost 0.14
    :output-cost 0.28}
   {:id "mistralai/mistral-large-2512"
    :name "Mistral Large 2512"
    :input-cost 2
    :output-cost 6}])

(defn get-model-config [model-id]
  (some #(when (= (:id %) model-id) %) available-models))

(defn calculate-cost
  "Calculate cost in dollars from token usage."
  [model-id input-tokens output-tokens]
  (if-let [model (get-model-config model-id)]
    (+ (* (/ input-tokens 1000000) (:input-cost model))
       (* (/ output-tokens 1000000) (:output-cost model)))
    ;; Default pricing for custom models
    (+ (* (/ input-tokens 1000000) 3)
       (* (/ output-tokens 1000000) 15))))

(defn api-key-configured? []
  (some? (core/get-api-key)))

(defn validate-config []
  (let [config (core/get-config)]
    (cond
      (empty? (:api-key config))
      {:valid? false :error "API key is required"}

      (empty? (:endpoint config))
      {:valid? false :error "Endpoint URL is required"}

      (empty? (:model config))
      {:valid? false :error "Model selection is required"}

      :else
      {:valid? true})))

(defn test-connection!
  "Test the API connection. Returns a promise."
  []
  (js/Promise.
   (fn [resolve reject]
     (let [config (core/get-config)]
       (-> (js/fetch (:endpoint config)
                     #js {:method "POST"
                          :headers #js {"Content-Type" "application/json"
                                        "Authorization" (str "Bearer " (:api-key config))}
                          :body (js/JSON.stringify
                                 #js {:model (:model config)
                                      :messages #js [#js {:role "user"
                                                          :content "Say 'OK' and nothing else."}]
                                      :max_tokens 10})})
           (.then (fn [response]
                    (if (.-ok response)
                      (resolve {:success true})
                      (.then (.json response)
                             (fn [data]
                               (resolve {:success false
                                         :error (or (.-message (.-error data))
                                                    (str "HTTP " (.-status response)))}))))))
           (.catch (fn [err]
                     (resolve {:success false
                               :error (.-message err)}))))))))
