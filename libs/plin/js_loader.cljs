(ns plin.js-loader
  "Loads JavaScript plugins and wraps them as CLJS plugin structures.
   
   JS plugins are loaded via fetch+eval, giving CLJS full control over
   loading order. The plugin file should return a plugin definition object.
   
   Example JS plugin:
   
   return {
     doc: 'My plugin description',
     deps: ['plinpt.i-application'],
     beans: {
       'my-page': { type: 'react-component', value: MyComponent },
       'my-icon': { type: 'hiccup', value: ['svg', {...}, ['path', {...}]] }
     },
     contributions: {
       'plinpt.i-application/nav-items': [{ id: 'my-page', ... }]
     }
   };
   
   Bean types:
   - 'react-component': A React component function. Will be wrapped for Reagent.
   - 'hiccup': A Hiccup data structure (array). Will be converted to CLJS vectors.
   - (default): Any other value, stored as-is."
  (:require [clojure.string :as str]))

;; =============================================================================
;; Namespace Derivation
;; =============================================================================

(defn- derive-namespace
  "Derives the namespace string from a manifest entry's id (as string).
   Converts to use underscores instead of hyphens to avoid scittle issues.
   
   Handles both CLJS maps (from js->clj) and raw JS objects (from #js {}).
   
   Examples:
   - \"showcase.hello-react\" -> \"showcase.hello_react\"
   - \"showcase/hello-react\" -> \"showcase.hello_react\""
  [manifest-entry]
  (let [;; Handle both CLJS maps and JS objects
        id-str (if (map? manifest-entry)
                 (get manifest-entry "id")
                 (aget manifest-entry "id"))
        raw-ns (if (and id-str (str/includes? id-str "/"))
                 (str/replace id-str "/" ".")
                 (or id-str "unknown"))]
    (str/replace raw-ns "-" "_")))

;; =============================================================================
;; Safe JS->CLJ Conversion
;; =============================================================================

(defn- js-arr->vec
  "Converts a JS array to a Clojure vector."
  [js-arr]
  (when js-arr
    (vec (for [i (range (.-length js-arr))]
           (aget js-arr i)))))

;; =============================================================================
;; CLJS->JS Conversion for Injected Values
;; =============================================================================

(defn- convert-keyword-to-string
  "Converts a CLJS keyword to a string representation."
  [kw]
  (if (keyword? kw)
    (if-let [ns (namespace kw)]
      (str ns "/" (name kw))
      (name kw))
    (str kw)))

(declare cljs->js-deep)

(defn- cljs-map->js-obj
  "Converts a CLJS map to a JS object with string keys.
   seen is a set of objects already visited (for cycle detection)."
  [m seen depth]
  (let [obj (js-obj)]
    (doseq [[k v] m]
      (let [key-str (if (keyword? k)
                      (convert-keyword-to-string k)
                      (str k))]
        (aset obj key-str (cljs->js-deep v seen depth))))
    obj))

(defn- cljs-set->js-set
  "Converts a CLJS set to a JS Set."
  [s seen depth]
  (let [js-set (js/Set.)]
    (doseq [item s]
      (.add js-set (cljs->js-deep item seen depth)))
    js-set))

;; =============================================================================
;; Atom Wrapper with Enhanced Methods
;; =============================================================================

(defn- create-atom-wrapper
  "Creates an enhanced atom wrapper with swap, reset, and watch methods.
   
   The wrapper provides:
   - ___isAtom: boolean marker
   - value: cached dereferenced value (at conversion time)
   - deref(): get fresh value
   - swap(fn): update atom value, fn receives JS value and should return JS value
   - reset(val): replace atom value entirely
   - watch(callback): subscribe to changes, returns unwatch function"
  [atom-ref seen depth]
  (let [derefed @atom-ref
        ;; Debounce state for watch callbacks
        debounce-timeout (atom nil)
        debounce-ms 16 ;; ~1 animation frame
        
        wrapper (js-obj)]
    
    ;; Marker
    (aset wrapper "___isAtom" true)
    
    ;; Cached value (at conversion time)
    (aset wrapper "value" (cljs->js-deep derefed seen (inc depth)))
    
    ;; Deref - get fresh value
    (aset wrapper "deref" 
          (fn [] 
            (cljs->js-deep @atom-ref #{} 0)))
    
    ;; Swap - update with function
    ;; fn receives JS value, should return JS value
    ;; We auto-convert JS->CLJS for storage
    (aset wrapper "swap"
          (fn [f]
            (swap! atom-ref 
                   (fn [old-cljs]
                     (let [old-js (cljs->js-deep old-cljs #{} 0)
                           new-js (f old-js)
                           new-cljs (js->clj new-js :keywordize-keys true)]
                       new-cljs)))
            ;; Return the new JS value
            (cljs->js-deep @atom-ref #{} 0)))
    
    ;; Reset - replace value entirely
    (aset wrapper "reset"
          (fn [new-val]
            (let [new-cljs (js->clj new-val :keywordize-keys true)]
              (reset! atom-ref new-cljs))
            ;; Return the new JS value
            (cljs->js-deep @atom-ref #{} 0)))
    
    ;; Watch - subscribe to changes with debouncing
    (aset wrapper "watch"
          (fn [callback]
            (let [watch-key (keyword (str "js-watch-" (random-uuid)))]
              (add-watch atom-ref watch-key
                         (fn [_ _ _ new-val]
                           ;; Debounce rapid updates
                           (when-let [timeout @debounce-timeout]
                             (js/clearTimeout timeout))
                           (reset! debounce-timeout
                                   (js/setTimeout
                                    (fn []
                                      (reset! debounce-timeout nil)
                                      (callback (cljs->js-deep new-val #{} 0)))
                                    debounce-ms))))
              ;; Return unwatch function
              (fn []
                (when-let [timeout @debounce-timeout]
                  (js/clearTimeout timeout))
                (remove-watch atom-ref watch-key)))))
    
    wrapper))

;; =============================================================================
;; Main CLJS->JS Conversion
;; =============================================================================

(defn cljs->js-deep
  "Recursively converts CLJS data structures to JS equivalents.
   - Maps become JS objects with string keys
   - Vectors/lists become JS arrays
   - Sets become JS Sets
   - Keywords become strings
   - Atoms are wrapped with enhanced methods (deref, swap, reset, watch)
   - Functions are passed through as-is
   - Other values pass through
   
   Includes cycle detection and depth limiting to prevent infinite recursion."
  ([v] (cljs->js-deep v #{} 0))
  ([v seen depth]
   (cond
     ;; Depth limit to prevent runaway recursion
     (> depth 50)
     "[max depth exceeded]"
     
     ;; Nil
     (nil? v) nil
     
     ;; Already seen this object (cycle detection)
     (and (or (map? v) (set? v) (vector? v) (list? v) (seq? v))
          (contains? seen v))
     "[circular reference]"
     
     ;; Atom - create enhanced wrapper
     (satisfies? IDeref v)
     (create-atom-wrapper v seen depth)
     
     ;; Function - pass through
     (fn? v) v
     
     ;; Keyword - convert to string
     (keyword? v) (convert-keyword-to-string v)
     
     ;; Map - convert to JS object
     (map? v) 
     (cljs-map->js-obj v (conj seen v) (inc depth))
     
     ;; Set - convert to JS Set
     (set? v) 
     (cljs-set->js-set v (conj seen v) (inc depth))
     
     ;; Vector/List/Seq - convert to JS array
     (or (vector? v) (list? v) (seq? v))
     (let [arr (array)
           new-seen (conj seen v)]
       (doseq [item v]
         (.push arr (cljs->js-deep item new-seen (inc depth))))
       arr)
     
     ;; String, number, boolean - pass through
     :else v)))

;; =============================================================================
;; Hiccup Conversion
;; =============================================================================

(declare convert-hiccup)

(defn- js-obj->map
  "Converts a JS object to a Clojure map with keyword keys."
  [js-obj]
  (when (and js-obj (object? js-obj) (not (array? js-obj)))
    (let [keys (js/Object.keys js-obj)]
      (into {}
            (for [i (range (.-length keys))]
              (let [k (aget keys i)
                    v (aget js-obj k)]
                [(keyword k) (convert-hiccup v)]))))))

(defn- convert-hiccup
  "Recursively converts a JS Hiccup structure to CLJS.
   - Arrays become vectors
   - Objects become maps with keyword keys
   - Strings/numbers/etc pass through"
  [v]
  (cond
    ;; JS Array - convert to vector, recursively convert children
    (array? v)
    (let [len (.-length v)]
      (loop [i 0
             result []]
        (if (< i len)
          (recur (inc i) (conj result (convert-hiccup (aget v i))))
          result)))
    
    ;; JS Object (but not array) - convert to map with keyword keys
    (and (some? v) (object? v) (not (fn? v)))
    (js-obj->map v)
    
    ;; Pass through primitives (strings, numbers, keywords, nil, etc.)
    :else v))

;; =============================================================================
;; React Component Wrapping
;; =============================================================================

(defn- wrap-react-component
  "Wraps a React component for use with Reagent.
   Creates a Reagent component function that uses React.createElement directly.
   This avoids issues with adapt-react-class and hooks."
  [react-component]
  ;; Return a function that Reagent will call as a component
  ;; This function returns a vector that tells Reagent to render a native React component
  (fn reagent-wrapper []
    ;; Use :> to tell Reagent to render a native React component
    [:> react-component]))

;; =============================================================================
;; Bean Wrapping
;; =============================================================================

(defn- make-qualified-keyword
  "Creates a qualified keyword."
  [ns-str local-name]
  (keyword ns-str local-name))

(defn- parse-bean-ref
  "Parses a bean reference string into a qualified keyword.
   'plin.boot/api' -> :plin.boot/api"
  [ref-str]
  (if (str/includes? ref-str "/")
    (let [[ns-part name-part] (str/split ref-str #"/" 2)]
      (keyword ns-part name-part))
    (keyword ref-str)))

(defn- wrap-beans
  "Wraps all JS beans into CLJS bean format with qualified keys.
   
   Bean types:
   - 'react-component': Wrapped for Reagent compatibility
   - 'hiccup': Converted from JS arrays/objects to CLJS vectors/maps
   - (default): Stored as-is
   
   For beans with inject, we use a constructor pattern.
   For beans without inject, we use [:= value] directly."
  [js-beans ns-str]
  (when js-beans
    (let [bean-keys (js/Object.keys js-beans)]
      (into {}
            (for [i (range (.-length bean-keys))]
              (let [k (aget bean-keys i)
                    bean-def (aget js-beans k)
                    bean-type (aget bean-def "type")
                    bean-doc (aget bean-def "doc")
                    bean-value (aget bean-def "value")
                    bean-inject (aget bean-def "inject")
                    safe-key (str/replace k "-" "_")
                    qualified-key (make-qualified-keyword ns-str safe-key)
                    
                    ;; Determine bean type
                    is-react-component? (= bean-type "react-component")
                    is-hiccup? (= bean-type "hiccup")
                    
                    ;; Check if inject is present (even if empty array)
                    has-inject? (and (some? bean-inject) (pos? (.-length bean-inject)))
                    
                    _ (when has-inject?
                        (js/console.log "wrap-beans: bean" k "has inject:" (js-arr->vec bean-inject)))
                    
                    ;; Build the bean spec based on type and injection needs
                    bean-spec (cond
                                ;; Hiccup type - convert to CLJS data structure
                                is-hiccup?
                                [:= (convert-hiccup bean-value)]
                                
                                ;; React component with injection
                                (and is-react-component? has-inject?)
                                (let [inject-arr (js-arr->vec bean-inject)
                                      dep-keys (mapv parse-bean-ref inject-arr)
                                      _ (js/console.log "wrap-beans: react component with injection, dep-keys:" (pr-str dep-keys))
                                      ;; Create a constructor that:
                                      ;; 1. Receives the injected dependencies
                                      ;; 2. Converts them to JS-friendly format
                                      ;; 3. Calls the JS factory function with them
                                      ;; 4. Wraps the result for Reagent
                                      factory-fn (fn [& injected-args]
                                                   (js/console.log "JS bean factory called, converting args to JS...")
                                                   (let [;; Convert CLJS args to JS-friendly format
                                                         js-args (mapv cljs->js-deep injected-args)
                                                         _ (js/console.log "JS bean factory converted args:" (pr-str (mapv type js-args)))
                                                         ;; Call the JS factory with converted args
                                                         react-comp (apply bean-value js-args)]
                                                     (js/console.log "JS factory returned:" react-comp)
                                                     ;; Wrap for Reagent
                                                     (wrap-react-component react-comp)))]
                                  {:constructor (into [factory-fn] dep-keys)})
                                
                                ;; React component without injection
                                is-react-component?
                                [:= (wrap-react-component bean-value)]
                                
                                ;; Other types with injection
                                has-inject?
                                (let [inject-arr (js-arr->vec bean-inject)
                                      dep-keys (mapv parse-bean-ref inject-arr)
                                      ;; Create a wrapper that converts args before calling
                                      wrapper-fn (fn [& injected-args]
                                                   (let [js-args (mapv cljs->js-deep injected-args)]
                                                     (apply bean-value js-args)))]
                                  {:constructor (into [wrapper-fn] dep-keys)})
                                
                                ;; Default - store as-is
                                :else
                                [:= bean-value])]
                [qualified-key bean-spec]))))))

;; =============================================================================
;; Contribution Wrapping
;; =============================================================================

(defn- convert-js-value
  "Recursively converts a JS value to CLJS, but does NOT resolve bean refs.
   Bean refs are kept as-is for later resolution."
  [v]
  (cond
    ;; JS Array
    (and (some? v) (array? v))
    (mapv convert-js-value (js-arr->vec v))
    
    ;; JS Object (but not null or function)
    (and (some? v) (object? v) (not (fn? v)))
    (let [obj-keys (js/Object.keys v)]
      (into {}
            (for [i (range (.-length obj-keys))]
              (let [k (aget obj-keys i)]
                [(keyword k) (convert-js-value (aget v k))]))))
    
    ;; Pass through other values (strings, numbers, booleans, functions, etc.)
    :else v))

(defn- parse-contribution-key
  "Parses a contribution key string into a qualified keyword."
  [key-str]
  (if (str/includes? key-str "/")
    (let [[ns-part name-part] (str/split key-str #"/" 2)]
      (keyword ns-part name-part))
    (keyword key-str)))

(defn- create-nav-item-bean
  "Creates a nav-item bean that uses the constructor pattern to inject component and icon.
   Returns [bean-key bean-spec]."
  [nav-item ns-str idx]
  (let [;; Extract the bean references
        component-ref (:component nav-item)
        icon-ref (:icon nav-item)
        
        ;; Create qualified keywords for the referenced beans
        component-bean-key (when (and component-ref (string? component-ref))
                            (make-qualified-keyword ns-str (str/replace component-ref "-" "_")))
        icon-bean-key (when (and icon-ref (string? icon-ref))
                        (make-qualified-keyword ns-str (str/replace icon-ref "-" "_")))
        
        ;; Create the nav-item bean key
        nav-item-id (or (:id nav-item) (str "nav-item-" idx))
        nav-item-bean-key (make-qualified-keyword ns-str (str "nav_item_" (str/replace (str nav-item-id) "-" "_")))
        
        ;; Build the base nav-item data (without component/icon as they'll be injected)
        base-nav-item (dissoc nav-item :component :icon)
        
        ;; Build constructor based on what needs to be injected
        ;; The constructor is [fn & bean-keys-to-inject]
        bean-spec (cond
                    (and component-bean-key icon-bean-key)
                    {:constructor [(fn [comp icon]
                                     (assoc base-nav-item :component comp :icon icon))
                                   component-bean-key
                                   icon-bean-key]}
                    
                    component-bean-key
                    {:constructor [(fn [comp]
                                     (assoc base-nav-item :component comp))
                                   component-bean-key]}
                    
                    icon-bean-key
                    {:constructor [(fn [icon]
                                     (assoc base-nav-item :icon icon))
                                   icon-bean-key]}
                    
                    :else
                    [:= base-nav-item])]
    
    [nav-item-bean-key bean-spec]))

(defn- process-nav-items-contribution
  "Processes nav-items contributions specially to use the bean constructor pattern.
   Returns {:extra-beans {...} :contribution-value [...bean-keys...]}"
  [nav-items ns-str]
  (let [items (if (array? nav-items)
                (mapv convert-js-value (js-arr->vec nav-items))
                nav-items)]
    (loop [idx 0
           extra-beans {}
           bean-keys []]
      (if (>= idx (count items))
        {:extra-beans extra-beans
         :contribution-value bean-keys}
        (let [item (nth items idx)
              [bean-key bean-spec] (create-nav-item-bean item ns-str idx)]
          (recur (inc idx)
                 (assoc extra-beans bean-key bean-spec)
                 (conj bean-keys bean-key)))))))

(defn- wrap-contributions
  "Wraps JS contributions into CLJS format.
   Returns {:contributions {...} :extra-beans {...}}"
  [js-contribs ns-str]
  (if-not js-contribs
    {:contributions {} :extra-beans {}}
    (let [contrib-keys (js/Object.keys js-contribs)]
      (loop [i 0
             contributions {}
             extra-beans {}]
        (if (>= i (.-length contrib-keys))
          {:contributions contributions :extra-beans extra-beans}
          (let [k (aget contrib-keys i)
                contrib-key (parse-contribution-key k)
                contrib-val (aget js-contribs k)]
            ;; Special handling for nav-items
            (if (= contrib-key :plinpt.i-application/nav-items)
              (let [result (process-nav-items-contribution contrib-val ns-str)]
                (recur (inc i)
                       (assoc contributions contrib-key (:contribution-value result))
                       (merge extra-beans (:extra-beans result))))
              ;; Regular contribution - just convert the value
              (recur (inc i)
                     (assoc contributions contrib-key (convert-js-value contrib-val))
                     extra-beans))))))))

;; =============================================================================
;; Extension Wrapping
;; =============================================================================

(defn- wrap-extension-handler
  "Wraps a JS extension handler function for CLJS<->JS conversion."
  [js-handler]
  (fn [db vals]
    (let [js-db (clj->js db)
          js-vals (clj->js vals)
          js-result (js-handler js-db js-vals)]
      (js->clj js-result :keywordize-keys true))))

(defn- wrap-extension
  "Wraps a single JS extension definition."
  [js-ext ns-str]
  (let [key-str (or (aget js-ext "key") "")
        ext-doc (aget js-ext "doc")
        ext-handler (aget js-ext "handler")
        ext-spec (aget js-ext "spec")
        ext-key (if (str/includes? key-str "/")
                  (parse-contribution-key key-str)
                  (make-qualified-keyword ns-str (str/replace key-str "-" "_")))]
    (cond-> {:key ext-key
             :handler (wrap-extension-handler ext-handler)}
      ext-doc (assoc :doc ext-doc)
      ext-spec (assoc :spec ext-spec))))

(defn- wrap-extensions
  "Wraps JS extensions into CLJS format."
  [js-extensions ns-str]
  (when (and js-extensions (pos? (.-length js-extensions)))
    (mapv #(wrap-extension % ns-str) (js-arr->vec js-extensions))))

;; =============================================================================
;; Main Wrapping Function
;; =============================================================================

(defn wrap-js-plugin
  "Wraps a JS plugin definition into a CLJS plugin map."
  [js-def manifest-entry]
  (let [ns-str (derive-namespace manifest-entry)
        plugin-id (keyword ns-str "plugin")
        
        ;; Extract fields from JS definition using aget for safety
        js-doc (aget js-def "doc")
        js-deps (aget js-def "deps")
        js-extensions (aget js-def "extensions")
        js-beans (aget js-def "beans")
        js-contribs (aget js-def "contributions")
        
        ;; Convert deps - use explicit loop to avoid issues
        deps (when (and js-deps (pos? (.-length js-deps)))
               (let [dep-arr js-deps
                     len (.-length dep-arr)]
                 (loop [i 0
                        result []]
                   (if (< i len)
                     (recur (inc i) (conj result (keyword (aget dep-arr i) "plugin")))
                     result))))
        
        ;; Wrap beans
        wrapped-beans (or (wrap-beans js-beans ns-str) {})
        
        ;; Wrap contributions (may produce extra beans for nav-items)
        contrib-result (wrap-contributions js-contribs ns-str)
        contributions (:contributions contrib-result)
        extra-beans (:extra-beans contrib-result)
        
        ;; Merge all beans
        all-beans (merge wrapped-beans extra-beans)
        
        ;; Wrap extensions
        wrapped-extensions (when js-extensions (wrap-extensions js-extensions ns-str))
        
        ;; Build plugin map incrementally using assoc
        plugin {:id plugin-id}
        plugin (if js-doc (assoc plugin :doc js-doc) plugin)
        plugin (if (seq deps) (assoc plugin :deps deps) plugin)
        plugin (if wrapped-extensions (assoc plugin :extensions wrapped-extensions) plugin)
        plugin (if (seq all-beans) (assoc plugin :beans all-beans) plugin)
        plugin (if (seq contributions) (assoc plugin :contributions contributions) plugin)]
    
    (js/console.log "wrap-js-plugin: created plugin" (str plugin-id))
    (js/console.log "wrap-js-plugin: contributions" (pr-str contributions))
    plugin))

;; =============================================================================
;; Loading Functions
;; =============================================================================

;; Atom to store results - avoids JS<->CLJS conversion issues
(defonce ^:private js-plugin-results (atom []))

(defn- entry-enabled?
  "Checks if a manifest entry is enabled.
   Returns true if :enabled is not explicitly set to false."
  [entry-clj]
  (let [enabled (get entry-clj "enabled")]
    ;; Only skip if explicitly set to false
    (not (false? enabled))))

(defn load-js-plugins
  "Loads all JS plugins from manifest entries.
   Skips entries where :enabled is explicitly set to false.
   Returns a native JS Promise that resolves to a CLJS vector of plugin maps."
  [js-entries]
  (js/console.log "load-js-plugins: received" (count js-entries) "entries")
  (reset! js-plugin-results [])
  
  ;; Filter out disabled entries first
  (let [enabled-entries (filterv (fn [entry]
                                   (let [entry-clj (if (map? entry) entry (js->clj entry))
                                         enabled? (entry-enabled? entry-clj)
                                         id (get entry-clj "id")]
                                     (when (not enabled?)
                                       (js/console.log "load-js-plugins: skipping disabled plugin" id))
                                     enabled?))
                                 js-entries)]
    (js/console.log "load-js-plugins: loading" (count enabled-entries) "enabled plugins")
    
    (if (empty? enabled-entries)
      (js/Promise.resolve [])
      (let [entries-arr (clj->js enabled-entries)]
        (js/Promise.
         (fn [resolve reject]
           (let [pending (atom (count enabled-entries))
                 
                 on-complete (fn []
                               (js/console.log "load-js-plugins: all complete, results:" (count @js-plugin-results))
                               ;; Return the CLJS vector directly, not converted to JS
                               (resolve @js-plugin-results))
                 
                 load-one (fn [entry idx]
                            (let [entry-clj (js->clj entry)
                                  files (get entry-clj "files")
                                  url (first files)]
                              (js/console.log "load-js-plugins: loading" url)
                              (if url
                                (-> (js/fetch url)
                                    (.then (fn [response]
                                             (.text response)))
                                    (.then (fn [code]
                                             (js/console.log "load-js-plugins: got code for" url "length:" (.-length code))
                                             ;; Execute via script element
                                             (let [result-key (str "__PLIN_RESULT_" idx "__")
                                                   wrapped-code (str "window['" result-key "'] = (function(){" code "})();")
                                                   script-el (js/document.createElement "script")]
                                               (set! (.-type script-el) "text/javascript")
                                               (set! (.-text script-el) wrapped-code)
                                               (.appendChild js/document.head script-el)
                                               (let [js-def (aget js/window result-key)]
                                                 (.removeChild js/document.head script-el)
                                                 (js-delete js/window result-key)
                                                 (if js-def
                                                   (do
                                                     (js/console.log "load-js-plugins: wrapping" url)
                                                     (try
                                                       (let [plugin (wrap-js-plugin js-def entry-clj)]
                                                         (js/console.log "load-js-plugins: successfully wrapped" url)
                                                         ;; Store in atom to preserve CLJS data structures
                                                         (swap! js-plugin-results conj plugin))
                                                       (catch :default e
                                                         (js/console.error "load-js-plugins: wrap-js-plugin failed for" url e))))
                                                   (js/console.warn "load-js-plugins: js-def is nil for" url))
                                                 (swap! pending dec)
                                                 (when (zero? @pending)
                                                   (on-complete))))))
                                    (.catch (fn [err]
                                              (js/console.error "load-js-plugins: failed to load" url err)
                                              (swap! pending dec)
                                              (when (zero? @pending)
                                                (on-complete)))))
                                (do
                                  (swap! pending dec)
                                  (when (zero? @pending)
                                    (on-complete))))))]
             
             ;; Start loading all plugins
             (dotimes [i (.-length entries-arr)]
               (load-one (aget entries-arr i) i)))))))))

;; =============================================================================
;; Utility for Dynamic Loading
;; =============================================================================

(defn load-js-plugin-from-code
  "Loads a JS plugin from source code string.
   Returns a Promise that resolves to the wrapped CLJS plugin map."
  [code plugin-id]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [manifest-entry {"id" (if (keyword? plugin-id)
                                    (if (namespace plugin-id)
                                      (str (namespace plugin-id) "." (name plugin-id))
                                      (name plugin-id))
                                    (str plugin-id))}
             result-key (str "__PLIN_DYNAMIC_" (js/Date.now) "__")
             wrapped-code (str "window['" result-key "'] = (function(){" code "})();")
             script-el (js/document.createElement "script")]
         (set! (.-type script-el) "text/javascript")
         (set! (.-text script-el) wrapped-code)
         (.appendChild js/document.head script-el)
         (let [js-def (aget js/window result-key)]
           (.removeChild js/document.head script-el)
           (js-delete js/window result-key)
           (if js-def
             (resolve (wrap-js-plugin js-def manifest-entry))
             (reject (js/Error. "JS plugin returned nil/undefined")))))
       (catch :default e
         (reject e))))))
