(ns plinpt-extras.p-plugin-forge.storage
  "localStorage abstraction for Plugin Forge.")

(def ^:private config-key "plugin-forge:config")
(def ^:private library-key "plugin-forge:library")
(def ^:private chat-key "plugin-forge:chat-history")

(def ^:private max-storage-size (* 4 1024 1024)) ;; 4MB budget

(defn- safe-parse [json-str]
  (try
    (js->clj (js/JSON.parse json-str) :keywordize-keys true)
    (catch :default _
      nil)))

(defn- safe-stringify [data]
  (try
    (js/JSON.stringify (clj->js data))
    (catch :default _
      nil)))

(defn load-config []
  (some-> (.getItem js/localStorage config-key)
          safe-parse))

(defn save-config! [config]
  (when-let [json (safe-stringify config)]
    (.setItem js/localStorage config-key json)))

(defn load-library []
  (some-> (.getItem js/localStorage library-key)
          safe-parse))

(defn save-library! [library]
  (when-let [json (safe-stringify library)]
    (.setItem js/localStorage library-key json)))

(defn load-chat-history []
  (some-> (.getItem js/localStorage chat-key)
          safe-parse))

(defn save-chat-history! [messages]
  (when-let [json (safe-stringify messages)]
    ;; Check size before saving
    (if (< (count json) max-storage-size)
      (.setItem js/localStorage chat-key json)
      ;; Trim old messages if too large
      (let [trimmed (vec (take-last 50 messages))]
        (when-let [trimmed-json (safe-stringify trimmed)]
          (.setItem js/localStorage chat-key trimmed-json))))))

(defn get-storage-size []
  (let [config-size (count (or (.getItem js/localStorage config-key) ""))
        library-size (count (or (.getItem js/localStorage library-key) ""))
        chat-size (count (or (.getItem js/localStorage chat-key) ""))]
    (+ config-size library-size chat-size)))

(defn clear-all! []
  (.removeItem js/localStorage config-key)
  (.removeItem js/localStorage library-key)
  (.removeItem js/localStorage chat-key))

(defn export-library []
  "Export library as a downloadable JSON string."
  (.getItem js/localStorage library-key))

(defn import-library! [json-str]
  "Import library from JSON string."
  (when (safe-parse json-str)
    (.setItem js/localStorage library-key json-str)
    true))
