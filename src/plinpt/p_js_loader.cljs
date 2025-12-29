(ns plinpt.p-js-loader
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [reagent.core :as r]
            [clojure.string :as str]
            [plinpt.i-app-shell :as app-shell]
            [plinpt.i-dynamic-loader :as i-loader]
            [plinpt.p-dynamic-loader.core :as loader-core]))

;; Use def instead of defonce so it resets on code reload, allowing re-fetching
(def loaded-ids (atom #{}))

(defn str->kw [s]
  (if (str/includes? s "/")
    (let [[ns-part name-part] (str/split s #"/")]
      (keyword ns-part name-part))
    (keyword s)))

(defn resolve-dep [dep-str]
  (try
    (let [sym-str (str dep-str "/plugin")]
      (js/scittle.core.eval_string sym-str))
    (catch :default e
      (js/console.warn "JS Loader: Could not resolve dependency" dep-str)
      nil)))

(defn wrapper-factory [react-comp prop-names & args]
  ;; (js/console.log "JS Loader: wrapper-factory called. Props:" (clj->js prop-names))
  (let [deps (take (count prop-names) args)
        injected-props (zipmap (map keyword prop-names) deps)
        adapted-comp (r/adapt-react-class react-comp)]
    (fn [& args]
      (let [[props children] (if (map? (first args))
                               [(first args) (rest args)]
                               [{} args])
            final-props (merge props injected-props)]
        ;; (js/console.log "JS Loader: Rendering wrapper with props:" (keys final-props))
        (into [adapted-comp final-props] children)))))

(defn process-beans [js-beans plugin-id]
  ;; (js/console.log "JS Loader: Processing beans for" plugin-id)
  (if-not js-beans
    {}
    (reduce (fn [acc k]
              (let [v (aget js-beans k)
                    bean-name k
                    bean-key (keyword (name plugin-id) bean-name)
                    val (js->clj v :keywordize-keys true)
                    type (:type val)
                    raw-value (.-value v)
                    
                    ;; Check for dependencies
                    deps-def (.-deps v) ;; JS array
                    has-deps? (and deps-def (> (.-length deps-def) 0))]
                
                ;; (js/console.log "JS Loader: Processing bean" bean-key "Type:" type "Has deps:" has-deps?)
                
                (assoc acc bean-key
                       (cond
                         (= type "react-component")
                         (if has-deps?
                           (let [parsed-deps (mapv (fn [d]
                                                     (if (string? d)
                                                       {:ref (str->kw d) :prop (name (str->kw d))}
                                                       {:ref (str->kw (:ref d)) :prop (:prop d)}))
                                                   (js->clj deps-def :keywordize-keys true))
                                 prop-names (mapv :prop parsed-deps)
                                 dep-keys (mapv :ref parsed-deps)]
                             ^{:doc (or (:doc val) "React Component with Deps") 
                               :reagent-component true}
                             (into [wrapper-factory raw-value prop-names] dep-keys))
                           
                           ^{:doc (or (:doc val) "React Component") 
                             :reagent-component true}
                           [:= (r/adapt-react-class raw-value)])

                         :else
                         ^{:doc (or (:doc val) "Value")}
                         [:= raw-value]))))
            {}
            (js/Object.keys js-beans))))

(defn process-contributions [js-contribs js-beans plugin-id]
  ;; (js/console.log "JS Loader: Processing contributions for" plugin-id)
  (if-not js-contribs
    {}
    (reduce (fn [acc k]
              (let [v (aget js-contribs k)
                    contrib-key (str->kw k)
                    data (js->clj v :keywordize-keys true)]
                (assoc acc contrib-key
                       (if (vector? data)
                         (mapv (fn [item]
                                 (cond
                                   ;; Handle maps (e.g. routes, nav items)
                                   (map? item)
                                   (reduce-kv (fn [m item-k item-v]
                                                (assoc m item-k
                                                       (if (and (or (= item-k :component) (= item-k :icon)) 
                                                                (string? item-v))
                                                         ;; Try to resolve from local beans
                                                         ;; item-v might be a string or keyword, ensure string for aget
                                                         (let [lookup-key (if (keyword? item-v) (name item-v) item-v)]
                                                           (if (aget js-beans lookup-key)
                                                             (keyword (name plugin-id) lookup-key)
                                                             item-v))
                                                         item-v)))
                                              {}
                                              item)
                                   
                                   ;; Handle raw strings (e.g. overlay components list)
                                   (string? item)
                                   (if (aget js-beans item)
                                     (keyword (name plugin-id) item)
                                     item)
                                   
                                   :else item))
                               data)
                         data))))
            {}
            (js/Object.keys js-contribs))))

(defn register-js-plugin [sys-api id js-def]
  (try
    ;;(js/console.log "JS Loader: Registering plugin" id)
    (let [deps-js (or (.-deps js-def) #js [])
          deps (->> (js->clj deps-js)
                    (map resolve-dep)
                    (remove nil?)
                    vec)
          
          js-beans (.-beans js-def)
          beans (process-beans js-beans id)
          contribs (process-contributions (.-contributions js-def) js-beans id)
          
          plugin-map {:id (keyword id)
                      :doc (or (.-doc js-def) "JS Plugin")
                      :deps deps
                      :beans beans
                      :contributions contribs}]
      
      ;; (js/console.log "JS Loader: Plugin map created" plugin-map)
      ((:register-plugin! sys-api) plugin-map))
    (catch :default e
      (js/console.error "JS Loader: Failed to register JS plugin" e)
      (js/Promise.reject e))))

(defn handle-js-file [sys-api file-name source]
  (try
    ;;(js/console.log "JS Loader: Handling file" file-name "Size:" (count source))
    ;; Wrap source in a function to execute it and get the returned object
    (let [factory (js/Function. source)
          js-def (factory)
          id (str/replace file-name #"\.js$" "")]
      
      (if js-def
        (do
          ;;(js/console.log "JS Loader: Executed JS, got definition" js-def)
          (-> (register-js-plugin sys-api id js-def)
              (.then (fn [] 
                       (when (and loader-core/state @loader-core/state)
                         (swap! loader-core/state assoc :loading? false :success (str "Successfully loaded JS plugin: " id)))))))
        (throw (ex-info "JS file did not return a plugin definition" {}))))
    (catch :default e
      (js/console.error "JS Loader: Error handling file" e)
      (when (and loader-core/state @loader-core/state)
        (swap! loader-core/state assoc :loading? false :error (str "JS Load Error: " (.-message e)))))))

(defn load-manifest-plugins [sys-api]
  (let [manifest (js->clj (.-SYSTEM_MANIFEST js/window) :keywordize-keys true)
        js-plugins (filter #(= (:type %) "js") manifest)]
    
    ;; (when (seq js-plugins)
    ;;   (js/console.log "JS Loader: Found" (count js-plugins) "JS plugins in manifest."))

    (doseq [p js-plugins]
      (let [id (:id p)
            file-path (first (:files p))
            ;; Add cache buster
            url (str file-path "?v=" (.now js/Date))]
        (when-not (contains? @loaded-ids id)
          ;(js/console.log "JS Loader: Fetching" url)
          (swap! loaded-ids conj id)
          (-> (js/fetch url)
              (.then (fn [res]
                       (if (.-ok res)
                         (.text res)
                         (throw (ex-info (str "Fetch failed: " (.-status res) " " (.-statusText res)) {})))))
              (.then (fn [source]
                       (handle-js-file sys-api (name id) source)))
              (.catch (fn [e]
                        (js/console.error "JS Loader: Failed to load manifest plugin" id e)
                        (swap! loaded-ids disj id)))))))))

(defn loader-component [sys-api & _]
  (r/create-class
   {:component-did-mount
    (fn []
      (load-manifest-plugins sys-api))
    
    :reagent-render
    (fn []
      [:span {:style {:display "none"}}])}))

(def plugin
  (plin/plugin
   {:doc "Enables loading of plain JS plugins."
    :deps [boot/plugin app-shell/plugin i-loader/plugin]
    
    :contributions
    {::app-shell/header-components [::loader-ui]
     ::i-loader/handlers [{:extension "js" :handler handle-js-file}]}

    :beans
    {::loader-ui
     ^{:doc "Hidden component that initializes the JS loader."
       :reagent-component true}
     [partial loader-component ::boot/api]}}))
