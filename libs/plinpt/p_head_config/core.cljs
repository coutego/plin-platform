(ns plinpt.p-head-config.core
  "Core logic for head configuration injection.")

(defonce ^:private injected-elements (atom #{}))

(defn- element-id [type identifier]
  (str "plin-head-" (name type) "-" (hash identifier)))

(defn- already-injected? [el-id]
  (or (contains? @injected-elements el-id)
      (.getElementById js/document el-id)))

(defn- mark-injected! [el-id]
  (swap! injected-elements conj el-id))

(defn- inject-script! [script-def]
  (let [src (if (string? script-def) script-def (:src script-def))
        el-id (element-id :script src)]
    (when (and src (not (already-injected? el-id)))
      (let [el (.createElement js/document "script")]
        (set! (.-id el) el-id)
        (set! (.-src el) src)
        (when (map? script-def)
          (when (:async? script-def) (set! (.-async el) true))
          (when (:defer? script-def) (set! (.-defer el) true))
          (when (:type script-def) (set! (.-type el) (:type script-def)))
          (when (:crossorigin script-def) (.setAttribute el "crossorigin" (:crossorigin script-def))))
        (.appendChild (.-head js/document) el)
        (mark-injected! el-id)
        (js/console.log "📦 Injected script:" src)))))

(defn- inject-style! [style-def]
  (let [href (if (string? style-def) style-def (:href style-def))
        el-id (element-id :style href)]
    (when (and href (not (already-injected? el-id)))
      (let [el (.createElement js/document "link")]
        (set! (.-id el) el-id)
        (set! (.-rel el) "stylesheet")
        (set! (.-href el) href)
        (when (and (map? style-def) (:media style-def))
          (set! (.-media el) (:media style-def)))
        (.appendChild (.-head js/document) el)
        (mark-injected! el-id)
        (js/console.log "🎨 Injected stylesheet:" href)))))

(defn- inject-inline-style! [css-string]
  (let [el-id (element-id :inline-style (hash css-string))]
    (when (and css-string (not (already-injected? el-id)))
      (let [el (.createElement js/document "style")]
        (set! (.-id el) el-id)
        (set! (.-textContent el) css-string)
        (.appendChild (.-head js/document) el)
        (mark-injected! el-id)
        (js/console.log "🎨 Injected inline styles")))))

(defn- inject-meta-tag! [meta-def]
  (let [identifier (or (:name meta-def) (:property meta-def) (str (hash meta-def)))
        el-id (element-id :meta identifier)]
    (when (and meta-def (not (already-injected? el-id)))
      (let [el (.createElement js/document "meta")]
        (set! (.-id el) el-id)
        (when (:name meta-def) (.setAttribute el "name" (:name meta-def)))
        (when (:property meta-def) (.setAttribute el "property" (:property meta-def)))
        (when (:content meta-def) (.setAttribute el "content" (:content meta-def)))
        (when (:charset meta-def) (.setAttribute el "charset" (:charset meta-def)))
        (when (:http-equiv meta-def) (.setAttribute el "http-equiv" (:http-equiv meta-def)))
        (.appendChild (.-head js/document) el)
        (mark-injected! el-id)
        (js/console.log "📋 Injected meta tag:" identifier)))))

(defn- apply-tailwind-config! [config]
  (when (and config (seq config) (exists? js/tailwind))
    (let [existing-config (or (.-config js/tailwind) #js {})
          ;; Deep merge the configs
          merged-config (js/Object.assign #js {} existing-config (clj->js config))]
      (set! (.-config js/tailwind) merged-config)
      (js/console.log "🎨 Applied Tailwind config:" (clj->js config)))))

(defn create-injector
  "Creates an inject! function that injects all collected head resources."
  [scripts styles inline-styles tailwind-config meta-tags]
  (fn inject! []
    (js/console.log "📦 Injecting head resources...")
    
    ;; Inject scripts
    (doseq [script scripts]
      (inject-script! script))
    
    ;; Inject stylesheets
    (doseq [style styles]
      (inject-style! style))
    
    ;; Inject inline styles
    (doseq [css inline-styles]
      (inject-inline-style! css))
    
    ;; Inject meta tags
    (doseq [meta-tag meta-tags]
      (inject-meta-tag! meta-tag))
    
    ;; Apply Tailwind config
    (when (seq tailwind-config)
      (apply-tailwind-config! tailwind-config))
    
    nil))
