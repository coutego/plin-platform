(ns plinpt-extras.p-plugin-forge.core
  "Main state atom and initialization for Plugin Forge."
  (:require [reagent.core :as r]
            [plinpt-extras.p-plugin-forge.storage :as storage]))

(defonce state
  (r/atom
   {:config {:endpoint "https://openrouter.ai/api/v1/chat/completions"
             :model "google/gemini-3-pro-preview"
             :api-key nil
             :system-prompt nil}

    :chat {:messages []
           :streaming? false
           :current-stream ""
           :cost 0}

    :session {:total-cost 0}

    :library {}

    :hot-loaded {:plugin-id nil
                 :plugin-def nil
                 :enabled? true
                 :error nil
                 :error-expanded? false
                 :code nil}

    :overlay {:visible? false
              :position {:x 20 :y 20}
              :dragging? false}

    :ui {:active-tab :chat}
    
    :sys-api nil
    
    ;; Track disabled plugins by their namespace keyword
    :disabled-plugins #{}}))

(defn set-sys-api! [api]
  (swap! state assoc :sys-api api))

(defn get-sys-api []
  (:sys-api @state))

(defn init!
  "Initialize state from localStorage."
  []
  (let [saved-config (storage/load-config)
        saved-library (storage/load-library)
        saved-chat (storage/load-chat-history)]
    (when saved-config
      (swap! state update :config merge saved-config))
    (when saved-library
      (swap! state assoc :library saved-library))
    (when saved-chat
      (swap! state assoc-in [:chat :messages] saved-chat))))

(defn get-config []
  (:config @state))

(defn get-api-key []
  (get-in @state [:config :api-key]))

(defn get-endpoint []
  (get-in @state [:config :endpoint]))

(defn get-model []
  (get-in @state [:config :model]))

(defn get-system-prompt []
  (get-in @state [:config :system-prompt]))

(defn set-config! [k v]
  (swap! state assoc-in [:config k] v)
  (storage/save-config! (:config @state)))

(defn get-messages []
  (get-in @state [:chat :messages]))

(defn streaming? []
  (get-in @state [:chat :streaming?]))

(defn get-current-stream []
  (get-in @state [:chat :current-stream]))

(defn get-chat-cost []
  (get-in @state [:chat :cost]))

(defn get-session-cost []
  (get-in @state [:session :total-cost]))

(defn add-message! [role content]
  (let [msg {:role role
             :content content
             :timestamp (.now js/Date)}]
    (swap! state update-in [:chat :messages] conj msg)
    (storage/save-chat-history! (get-in @state [:chat :messages]))))

(defn set-streaming! [streaming?]
  (swap! state assoc-in [:chat :streaming?] streaming?))

(defn append-stream! [chunk]
  (swap! state update-in [:chat :current-stream] str chunk))

(defn clear-stream! []
  (swap! state assoc-in [:chat :current-stream] ""))

(defn finalize-stream!
  "Convert current stream to a message and clear it."
  []
  (let [content (get-in @state [:chat :current-stream])]
    (when (seq content)
      (add-message! :assistant content)
      (clear-stream!))))

(defn add-cost! [cost]
  (swap! state update-in [:chat :cost] + cost)
  (swap! state update-in [:session :total-cost] + cost))

(defn reset-chat-cost! []
  (swap! state assoc-in [:chat :cost] 0))

(defn clear-chat! []
  (swap! state assoc-in [:chat :messages] [])
  (swap! state assoc-in [:chat :cost] 0)
  (clear-stream!)
  (storage/save-chat-history! []))

(defn get-library []
  (:library @state))

(defn get-plugin-versions [plugin-id]
  (get-in @state [:library plugin-id :versions]))

(defn save-plugin-to-library! [plugin-id code]
  (let [version {:code code
                 :timestamp (.now js/Date)}]
    (swap! state update-in [:library plugin-id :versions] (fnil conj []) version)
    (storage/save-library! (:library @state))))

(defn delete-plugin-version! [plugin-id version-idx]
  (swap! state update-in [:library plugin-id :versions]
         (fn [versions]
           (vec (concat (subvec versions 0 version-idx)
                        (subvec versions (inc version-idx))))))
  (when (empty? (get-in @state [:library plugin-id :versions]))
    (swap! state update :library dissoc plugin-id))
  (storage/save-library! (:library @state)))

(defn delete-plugin! [plugin-id]
  (swap! state update :library dissoc plugin-id)
  (storage/save-library! (:library @state)))

(defn get-hot-loaded []
  (:hot-loaded @state))

(defn set-hot-loaded! [plugin-id plugin-def code]
  (swap! state assoc :hot-loaded
         {:plugin-id plugin-id
          :plugin-def plugin-def
          :enabled? true
          :error nil
          :error-expanded? false
          :code code})
  ;; Remove from disabled set when freshly loaded
  (swap! state update :disabled-plugins disj plugin-id)
  (swap! state assoc-in [:overlay :visible?] true))

(defn set-hot-load-error! [error]
  (swap! state assoc-in [:hot-loaded :error] error))

(defn toggle-error-expanded! []
  (swap! state update-in [:hot-loaded :error-expanded?] not))

(defn is-plugin-disabled? [plugin-id]
  (contains? (:disabled-plugins @state) plugin-id))

(defn toggle-hot-loaded-enabled! []
  (let [hot-loaded (get-hot-loaded)
        plugin-id (:plugin-id hot-loaded)
        currently-enabled? (:enabled? hot-loaded)]
    (swap! state assoc-in [:hot-loaded :enabled?] (not currently-enabled?))
    (if currently-enabled?
      ;; Disabling - add to disabled set
      (swap! state update :disabled-plugins conj plugin-id)
      ;; Enabling - remove from disabled set
      (swap! state update :disabled-plugins disj plugin-id))))

(defn clear-hot-loaded! []
  (swap! state assoc :hot-loaded
         {:plugin-id nil
          :plugin-def nil
          :enabled? true
          :error nil
          :error-expanded? false
          :code nil})
  (swap! state assoc-in [:overlay :visible?] false))

(defn get-overlay []
  (:overlay @state))

(defn set-overlay-visible! [visible?]
  (swap! state assoc-in [:overlay :visible?] visible?))

(defn set-overlay-position! [x y]
  (swap! state assoc-in [:overlay :position] {:x x :y y}))

(defn set-overlay-dragging! [dragging?]
  (swap! state assoc-in [:overlay :dragging?] dragging?))

(defn get-active-tab []
  (get-in @state [:ui :active-tab]))

(defn set-active-tab! [tab]
  (swap! state assoc-in [:ui :active-tab] tab))

(defn register-plugin!
  "Register a plugin with the PLIN container using the injected sys-api."
  [plugin-def]
  (if-let [sys-api (get-sys-api)]
    (if-let [register-fn (:register-plugin! sys-api)]
      (register-fn plugin-def)
      (js/Promise.reject (js/Error. "No register-plugin! function in sys-api")))
    (js/Promise.reject (js/Error. "No sys-api available. Please reload the page."))))

(defn unregister-plugin!
  "Unregister a plugin from the PLIN container."
  [plugin-id]
  (if-let [sys-api (get-sys-api)]
    (if-let [unregister-fn (:unregister-plugin! sys-api)]
      (unregister-fn plugin-id)
      ;; If no unregister function, just resolve (some platforms may not support it)
      (js/Promise.resolve))
    (js/Promise.reject (js/Error. "No sys-api available."))))
