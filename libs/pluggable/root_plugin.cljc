(ns pluggable.root-plugin
  "The Root Plugin is automatically loaded by Pluggable as the very first plugin.
   It provides foundational extension points that all other plugins can use.

   ## Core Responsibilities

   1. **Extension Processing**: Implements the `:extensions` mechanism that allows
      plugins to declare extension points and handlers.

   2. **Bean Redefinition**: Provides the `:bean-redefs` extension point for wrapping
      or modifying beans defined by other plugins without replacing them entirely.

   ## Extension Points Provided

   ### `:extensions`
   Allows plugins to declare their own extension points. Each extension definition
   is a map with the following keys:

   | Key       | Required | Description |
   |-----------|----------|-------------|
   | `:key`    | Yes      | The keyword that other plugins will use to contribute |
   | `:handler`| Yes      | A function `(fn [db vals] ...)` that processes contributions |
   | `:doc`    | Yes      | Documentation string describing the extension |"
  (:require [malli.core :as m]
            [malli.error :as me]))

(def extension-schema
  [:map
   [:key keyword?]
   [:handler fn?]
   [:doc string?]
   [:spec {:optional true} [:or keyword? fn?]]])

(def extensions-schema
  "Schema for a collection of extension definitions.

   Plugins declare extensions as a vector of extension maps."
  [:maybe [:sequential extension-schema]])

;; =============================================================================
;; Extension Processing
;; =============================================================================

(defn- get-extension-value
  "Retrieves the value contributed by a plugin for a given extension key.

   Looks first in `:contributions`, then falls back to root-level keys
   (with a deprecation warning) unless strict mode is enabled.
   
   Special keys like :beans and :bean-redefs are expected at root level
   and don't trigger warnings."
  [plugin key strict?]
  (let [contrib (get-in plugin [:contributions key])]
    (if (not (nil? contrib))
      contrib
      (when-not strict?
        (let [root-val (get plugin key)]
          (when (not (nil? root-val))
            ;; :beans and :bean-redefs are special keys expected at the root
            (when-not (#{:beans :bean-redefs} key)
              (println "WARNING: Extension" key "found at root of plugin" (:id plugin) 
                       "- please move to :contributions"))
            root-val))))))

(defn- process-extension
  "Processes a single extension by collecting all plugin contributions and
   invoking the extension's handler.

   Parameters:
   - `acc`: Accumulator map with `:db` and `:plugins`
   - `extension`: The extension definition map

   Returns updated accumulator with handler results merged into `:db`."
  [{:keys [db plugins]}
   {:keys [key handler spec]}]
  (let [strict? (:pluggable/strict? db)
        vals (vec (filter #(not (nil? %)) (map #(get-extension-value % key strict?) plugins)))]
    (when spec
      (doall (for [val vals]
               (when-let [errors (m/explain spec val)]
                 (throw
                  (ex-info
                   (str "Wrong value for extension " key ": " (me/humanize errors))
                   {:errors errors}))))))
    {:db      (handler db vals)
     :plugins plugins}))

(defn- load-plugin
  "Loads a single plugin by processing all its declared extensions.

   Parameters:
   - `acc`: Accumulator map with `:db` and `:plugins`
   - `plugin`: The plugin being loaded (destructured for `:extensions`)

   Returns updated accumulator."
  [{:keys [db plugins] :as acc}
   {:keys [extensions]}]
  (when-let [errors (m/explain extensions-schema extensions)]
    (throw (ex-info "Invalid extensions schema" {:errors (me/humanize errors)})))

  {:db      (:db (reduce process-extension acc extensions))
   :plugins (rest plugins)})

;; =============================================================================
;; Bean Redefinition
;; =============================================================================

(defn- replace-placeholder
  "Recursively replaces a placeholder keyword in a bean specification.

   Handles both syntaxes:
   - **Vector (Easy Syntax)**: `[my-fn arg1 :orig arg2]`
   - **Map Syntax**: `{:constructor [my-fn :orig]}`

   Parameters:
   - `spec`: The bean specification (vector or map)
   - `placeholder`: The keyword to replace (e.g., `:orig`)
   - `replacement`: The value to substitute (typically the renamed original bean key)

   Returns the spec with all occurrences of placeholder replaced."
  [spec placeholder replacement]
  (cond
    ;; Handle "Easy" Syntax: [fn arg1 :orig]
    (vector? spec)
    (mapv (fn [arg] (if (= arg placeholder) replacement arg)) spec)

    ;; Handle Map Syntax: {:constructor [fn arg1 :orig]}
    (and (map? spec) (:constructor spec))
    (update spec :constructor replace-placeholder placeholder replacement)

    ;; Pass through other values unchanged
    :else
    spec))

(defn- process-single-bean-redef
  "Process a single bean redefinition.
   
   Parameters:
   - `current-db`: Current database state
   - `target-key`: The key of the bean to redefine
   - `redef-config`: The redefinition configuration (vector or map)
   
   Returns updated db."
  [current-db target-key redef-config]
  (let [;; Normalize config: support both vector and map syntax
        config (if (and (map? redef-config)
                        (or (:spec redef-config) (:placeholder redef-config)))
                 redef-config
                 {:spec redef-config})
        spec (:spec config)
        placeholder (or (:placeholder config) :orig)

        ;; Look up the original bean definition from db
        original-def (get-in current-db [:beans target-key])]

    (if-not original-def
      (do
        (println "WARNING: Cannot redefine bean" target-key
                 "- original not found. Skipping redefinition.")
        current-db)

      (let [;; Generate a unique key for the original bean
            ;; The random suffix prevents collisions if the same bean
            ;; is wrapped multiple times by different plugins
            orig-key (keyword (namespace target-key)
                              (str (name target-key) "-ORIG-" (rand-int 100000)))

            ;; Substitute the placeholder with the unique key
            new-spec (replace-placeholder spec placeholder orig-key)]

        (-> current-db
            ;; Preserve the original definition under the unique key
            (assoc-in [:beans orig-key] original-def)
            ;; Register the wrapper at the original key
            (assoc-in [:beans target-key] new-spec))))))

(defn- process-bean-redefs-for-plugin
  "Process all bean-redefs from a single plugin.
   
   Parameters:
   - `db`: Current database state
   - `plugin`: The plugin containing bean-redefs
   - `strict?`: Whether strict mode is enabled
   
   Returns updated db."
  [db plugin strict?]
  (let [redefs (or (get-in plugin [:contributions :bean-redefs])
                   (when-not strict? (get plugin :bean-redefs)))]
    (if redefs
      (reduce-kv process-single-bean-redef db redefs)
      db)))

(defn- process-all-bean-redefs
  "Process bean-redefs from all plugins in order.
   This runs AFTER all other extensions have been processed,
   so db[:beans] is fully populated.
   
   Parameters:
   - `db`: Database after all extensions processed
   - `plugins`: All plugins
   
   Returns updated db with bean redefinitions applied."
  [db plugins]
  (let [strict? (:pluggable/strict? db)]
    (reduce (fn [current-db plugin]
              (process-bean-redefs-for-plugin current-db plugin strict?))
            db
            plugins)))

(defn- loader-with-bean-redefs
  "Enhanced loader that properly handles bean-redefs as a post-processing step.

   The key insight is that bean-redefs needs to run AFTER all :beans extensions
   have been processed, so that db[:beans] contains all bean definitions.
   
   This loader:
   1. Processes all extensions normally (including :beans)
   2. After all extensions are done, processes :bean-redefs from all plugins"
  [db plugins]
  (let [;; First, process all extensions normally
        process-extensions-result
        (reduce load-plugin {:db db :plugins plugins} plugins)
        
        db-after-extensions (:db process-extensions-result)]
    
    ;; Then, process bean-redefs as a post-processing step
    (process-all-bean-redefs db-after-extensions plugins)))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  "The Root Plugin definition.

   This plugin is automatically prepended to the plugin list by `pluggable.core/load-plugins`.
   It must not be added manually.

   ## Provides

   - `:loader` - The core plugin loading mechanism
   - `:extensions` extension point - For plugins to declare their own hooks
   - `:bean-redefs` - Special handling for wrapping existing beans (processed after all extensions)"
  {:id     :root-plugin
   :loader loader-with-bean-redefs
   ;; Note: :bean-redefs is NOT an extension - it's handled specially by the loader
   ;; after all other extensions have been processed
   :extensions []})
