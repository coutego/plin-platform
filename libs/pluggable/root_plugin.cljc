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
   (with a deprecation warning) unless strict mode is enabled."
  [plugin key strict?]
  (let [contrib (get-in plugin [:contributions key])]
    (if (not (nil? contrib))
      contrib
      (when-not strict?
        (let [root-val (get plugin key)]
          (when (not (nil? root-val))
            ;; :beans is a special key that is expected to be at the root
            (when (not= key :beans)
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
  [{:keys [db plugins] :as acc} ;; FIXME: do we need the destructuring?
   {:keys [extensions]}]
  (when-let [errors (m/explain extensions-schema extensions)]
    (throw (ex-info "Invalid extensions schema" {:errors (me/humanize errors)})))

  {:db      (:db (reduce process-extension acc extensions))
   :plugins (rest plugins)})

(defn- loader
  "The main plugin loader function.

   Iterates through all plugins, processing their extensions in dependency order.
   This function is registered as the `:loader` for the root plugin.

   Parameters:
   - `db`: The initial database/configuration map
   - `plugins`: Vector of all plugins to load

   Returns the final db after all plugins have been processed."
  [db plugins]
  (:db (reduce load-plugin {:db db :plugins plugins} plugins)))

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

(defn process-bean-redefs
  "Extension handler for `:bean-redefs`. Enables wrapping existing beans.

   ## Purpose

   Sometimes you need to modify the output of a bean defined by another plugin
   without completely replacing its definition. This is the **Decorator Pattern**
   applied to dependency injection.

   ## Usage

   In your plugin's `:contributions` map:

   ```clojure
   :contributions
   {:bean-redefs
    {::other-plugin/some-bean [my-wrapper-fn :orig]}}
   ```

   ## How It Works

   1. The original bean definition is moved to a generated unique key
      (e.g., `::other-plugin/some-bean-ORIG-12345`)
   2. The `:orig` placeholder in your spec is replaced with that unique key
   3. Your new definition is registered at the original key
   4. When the container builds beans, your wrapper receives the original bean's
      output as an argument

   ## Syntax Options

   ### Simple Vector Syntax

   ```clojure
   {:bean-redefs
    {::target/bean [wrapper-fn :orig additional-arg]}}
   ```

   The `:orig` keyword is the default placeholder.

   ### Map Syntax (with custom placeholder)

   ```clojure
   {:bean-redefs
    {::target/bean {:spec [wrapper-fn :original-bean]
                    :placeholder :original-bean}}}
   ```

   Use this when `:orig` conflicts with your arguments or for clarity.

   ### Map Syntax (with constructor)

   ```clojure
   {:bean-redefs
    {::target/bean {:spec {:constructor [wrapper-fn :orig]}
                    :placeholder :orig}}}
   ```

   ## Example: Modifying a Navigation Item

   ```clojure
   (defn move-to-section [nav-item]
     (assoc nav-item :section \"My Section\"))

   (def plugin
     (plin/plugin
      {:deps [other/plugin]
       :contributions
       {:bean-redefs
        {::other/nav-item [move-to-section :orig]}}}))
   ```

   ## Warnings

   - If the target bean does not exist, a warning is printed and the
     redefinition is skipped
   - The placeholder keyword MUST appear in your spec, otherwise the
     original bean will never be injected

   Parameters:
   - `db`: Current configuration database
   - `vals`: Vector of redef maps contributed by plugins

   Returns updated db with bean definitions modified."
  [db vals]
  (let [redefs (apply merge vals)]
    (reduce-kv
     (fn [current-db target-key redef-config]
       (let [;; Normalize config: support both vector and map syntax
             config (if (and (map? redef-config)
                             (or (:spec redef-config) (:placeholder redef-config)))
                      redef-config
                      {:spec redef-config})
             spec (:spec config)
             placeholder (or (:placeholder config) :orig)

             ;; Look up the original bean definition
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
     db
     redefs)))

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
   - `:bean-redefs` extension point - For wrapping existing beans"
  {:id     :root-plugin
   :loader loader

   :extensions
   [{:key :bean-redefs
     :doc "Allows wrapping or redefining existing beans without replacing them entirely.

           Contributions should be a map of `{target-key redef-spec}` where:
           - `target-key` is the fully-qualified key of the bean to wrap
           - `redef-spec` is either:
             - A vector: `[wrapper-fn :orig other-args...]`
             - A map: `{:spec [wrapper-fn :placeholder] :placeholder :placeholder}`

           The `:orig` (or custom placeholder) keyword is replaced with a reference
           to the original bean, which will be injected at runtime."
     :handler process-bean-redefs}]})
