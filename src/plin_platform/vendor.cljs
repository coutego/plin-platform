(ns plin-platform.vendor
  "Manages downloading and caching of external vendor dependencies for standalone builds."
  (:require [clojure.string :as str]
            ["fs" :as fs]
            ["path" :as path]))

;; ============================================================================
;; Vendor Dependencies Definition
;; ============================================================================

(def vendor-deps
  "All external dependencies needed for standalone build."
  [{:id :react
    :url "https://unpkg.com/react@18/umd/react.production.min.js"
    :filename "react.production.min.js"
    :type :js
    :description "React 18 production build"}
   
   {:id :react-dom
    :url "https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"
    :filename "react-dom.production.min.js"
    :type :js
    :description "React DOM 18 production build"}
   
   {:id :scittle
    :url "https://unpkg.com/scittle@0.7.28/dist/scittle.js"
    :filename "scittle.js"
    :type :js
    :description "Scittle - ClojureScript interpreter"}
   
   {:id :scittle-reagent
    :url "https://unpkg.com/scittle@0.7.28/dist/scittle.reagent.js"
    :filename "scittle.reagent.js"
    :type :js
    :description "Scittle Reagent integration"}
   
   {:id :scittle-pprint
    :url "https://unpkg.com/scittle@0.7.28/dist/scittle.pprint.js"
    :filename "scittle.pprint.js"
    :type :js
    :description "Scittle pretty print"}
   
   {:id :scittle-promesa
    :url "https://unpkg.com/scittle@0.7.28/dist/scittle.promesa.js"
    :filename "scittle.promesa.js"
    :type :js
    :description "Scittle Promesa integration"}
   
   {:id :tailwind-jit
    :url "https://cdn.tailwindcss.com/3.4.17?plugins=typography@0.5.16"
    :filename "tailwind-jit.js"
    :type :js
    :description "Tailwind CSS 3.4.17 JIT compiler with Typography"}
   
   {:id :marked
    :url "https://cdn.jsdelivr.net/npm/marked@12.0.0/marked.min.js"
    :filename "marked.min.js"
    :type :js
    :description "Marked - Markdown parser"}
   
   {:id :alasql
    :url "https://cdn.jsdelivr.net/npm/alasql@4.4.0/dist/alasql.min.js"
    :filename "alasql.min.js"
    :type :js
    :wrap-for-this? true  ;; Uses (function(root,factory){...})(this,...) UMD pattern
    :description "AlaSQL - In-browser SQL database"}
   
   {:id :mermaid
    :url "https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js"
    :filename "mermaid.min.js"
    :type :js
    :wrap-for-this? true  ;; Uses (function(root,factory){...})(this,...) UMD pattern
    :description "Mermaid - Diagram rendering"}
   
   {:id :svg-pan-zoom
    :url "https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js"
    :filename "svg-pan-zoom.min.js"
    :type :js
    :description "SVG Pan Zoom"}
   
   {:id :hljs
    :url "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"
    :filename "highlight.min.js"
    :type :js
    :description "Highlight.js core"}
   
   {:id :hljs-clojure
    :url "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/clojure.min.js"
    :filename "clojure.min.js"
    :type :js
    :description "Highlight.js Clojure language"}
   
   {:id :hljs-css
    :url "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css"
    :filename "github-dark.min.css"
    :type :css
    :description "Highlight.js GitHub Dark theme"}])

;; ============================================================================
;; Paths
;; ============================================================================

(def user-root (js/process.cwd))

(defn get-vendor-dir
  "Returns the vendor cache directory path in node_modules."
  []
  (path/join user-root "node_modules" ".cache" "plin-vendor"))

(defn get-vendor-path
  "Returns the full path for a vendor file."
  [filename]
  (path/join (get-vendor-dir) filename))

;; ============================================================================
;; Directory Management
;; ============================================================================

(defn ensure-vendor-dir!
  "Creates the vendor cache directory if it doesn't exist."
  []
  (let [vendor-dir (get-vendor-dir)]
    (when-not (fs/existsSync vendor-dir)
      (fs/mkdirSync vendor-dir #js {:recursive true})
      (println "Created vendor cache directory:" vendor-dir))
    vendor-dir))

;; ============================================================================
;; Download Logic
;; ============================================================================

(defn- format-bytes
  "Formats bytes into human readable string."
  [bytes]
  (cond
    (< bytes 1024) (str bytes " B")
    (< bytes (* 1024 1024)) (str (.toFixed (/ bytes 1024) 1) " KB")
    :else (str (.toFixed (/ bytes (* 1024 1024)) 2) " MB")))

(defn download-file!
  "Downloads a file from URL to destination path using fetch.
   Returns a Promise that resolves to the destination path.
   Shows download progress for large files."
  [url dest description]
  (let [start-time (js/Date.now)]
    (-> (js/fetch url)
        (.then (fn [response]
                 (if-not (.-ok response)
                   (throw (js/Error. (str "HTTP " (.-status response) " downloading " url)))
                   (let [total-size (js/parseInt (.get (.-headers response) "content-length") 10)
                         show-progress? (and (not (js/isNaN total-size)) (> total-size 102400))]
                     (when show-progress?
                       (print (str "  Downloading " description "...")))
                     ;; Get the response as array buffer
                     (.arrayBuffer response)))))
        (.then (fn [buffer]
                 (let [data (js/Buffer.from buffer)
                       elapsed (/ (- (js/Date.now) start-time) 1000)]
                   (fs/writeFileSync dest data)
                   (println (str "  Downloaded " description " (" (format-bytes (.-length data)) " in " (.toFixed elapsed 1) "s)"))
                   dest))))))

(defn is-cached?
  "Returns true if the vendor file is already cached."
  [dep]
  (let [cached-path (get-vendor-path (:filename dep))]
    (fs/existsSync cached-path)))

(defn ensure-cached!
  "Ensures a single dependency is cached.
   Downloads if missing, skips if present.
   Returns Promise<path>."
  [dep]
  (let [cached-path (get-vendor-path (:filename dep))]
    (if (fs/existsSync cached-path)
      (js/Promise.resolve cached-path)
      (do
        (ensure-vendor-dir!)
        (download-file! (:url dep) cached-path (:description dep))))))

(defn ensure-all-cached!
  "Ensures all vendor dependencies are cached.
   Downloads missing files with progress reporting.
   Returns Promise that resolves when all are cached."
  []
  (js/Promise.
   (fn [resolve reject]
     (let [missing (filterv #(not (is-cached? %)) vendor-deps)
           cached-count (- (count vendor-deps) (count missing))]
       
       (println "")
       (println "Vendor Dependencies:")
       (println (str "  " cached-count "/" (count vendor-deps) " already cached"))
       
       (if (empty? missing)
         (do
           (println "  All dependencies cached!")
           (resolve true))
         (do
           (println (str "  Downloading " (count missing) " missing dependencies..."))
           (println "")
           
           ;; Download sequentially to show progress clearly
           (let [download-seq (fn download-seq [deps]
                                (if (empty? deps)
                                  (js/Promise.resolve true)
                                  (-> (ensure-cached! (first deps))
                                      (.then (fn [_] (download-seq (rest deps))))
                                      (.catch (fn [err]
                                                (println (str "  ERROR: Failed to download " (:description (first deps))))
                                                (println (str "         " (.-message err)))
                                                (js/Promise.reject err))))))]
             (-> (download-seq missing)
                 (.then (fn [_]
                          (println "")
                          (println "  All dependencies downloaded!")
                          (resolve true)))
                 (.catch reject)))))))))

;; ============================================================================
;; Read Cached Content
;; ============================================================================

(defn read-vendor
  "Reads the cached content for a vendor dependency by ID.
   Returns the file content as a string.
   Throws if not cached."
  [id]
  (let [dep (first (filter #(= (:id %) id) vendor-deps))]
    (when-not dep
      (throw (js/Error. (str "Unknown vendor dependency: " id))))
    (let [cached-path (get-vendor-path (:filename dep))]
      (when-not (fs/existsSync cached-path)
        (throw (js/Error. (str "Vendor dependency not cached: " id " (run build:standalone to download)"))))
      (fs/readFileSync cached-path "utf8"))))

(defn read-vendor-by-type
  "Returns a map of {:js [...] :css [...]} with content for all vendor deps."
  []
  (let [js-deps (filter #(= (:type %) :js) vendor-deps)
        css-deps (filter #(= (:type %) :css) vendor-deps)]
    {:js (mapv (fn [dep]
                 {:id (:id dep)
                  :content (read-vendor (:id dep))
                  :description (:description dep)
                  :wrap-for-this? (:wrap-for-this? dep)})
               js-deps)
     :css (mapv (fn [dep]
                  {:id (:id dep)
                   :content (read-vendor (:id dep))
                   :description (:description dep)})
                css-deps)}))

(defn get-dep-by-id
  "Returns the dependency definition for a given ID."
  [id]
  (first (filter #(= (:id %) id) vendor-deps)))

;; ============================================================================
;; CLI Entry Point (for testing)
;; ============================================================================

(defn -main
  "Downloads all vendor dependencies. Can be run standalone for pre-caching."
  [& _args]
  (println "PLIN Platform - Vendor Dependency Manager")
  (println "=========================================")
  (-> (ensure-all-cached!)
      (.then (fn [_]
               (println "")
               (println "Done!")))
      (.catch (fn [err]
                (println "")
                (println "Failed:" (.-message err))
                (js/process.exit 1)))))
