(ns plinpt.p-server-boot
  "Server-side boot plugin.
   
   Provides the ::boot/boot-fn implementation that starts the HTTP server."
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-service-invoker :as i-invoker]
            [clojure.string :as str]
            ["http" :as http]
            ["fs" :as fs]
            ["path" :as path]))

;; --- Path Resolution ---

(def user-root (js/process.cwd))

;; Framework root detection:
;; When running as a dependency, we need to find node_modules/plin-platform
;; When running from the repo itself, use cwd
(def framework-root
  (let [cwd (js/process.cwd)
        ;; Check if plin-platform exists in node_modules (running as dependency)
        nm-path (path/join cwd "node_modules/plin-platform")]
    (if (fs/existsSync nm-path)
      nm-path
      ;; Running from the repo itself
      cwd)))

(defn resolve-path [url]
  (let [clean-url (first (str/split url #"\?"))]
    (cond
      ;; A. Framework Assets (Libs)
      (str/starts-with? clean-url "/libs/")
      (let [rel (subs clean-url 1)
            stripped (subs clean-url 6)]
        [(path/join framework-root rel)
         (path/join framework-root stripped)
         (path/join framework-root "nbb-deps" rel)
         (path/join framework-root "nbb-deps" stripped)])

      ;; B. Source files (src/)
      (str/starts-with? clean-url "/src/")
      (let [rel (subs clean-url 1)]
        [(path/join user-root rel)
         (path/join framework-root rel)
         (path/join framework-root "nbb-deps" rel)])

      ;; C. Framework Bootstrapper
      (= clean-url "/client_boot.cljs")
      [(path/join framework-root "src/plin_platform/client_boot.cljs")
       (path/join framework-root "plin_platform/client_boot.cljs")
       (path/join framework-root "nbb-deps" "src/plin_platform/client_boot.cljs")
       (path/join framework-root "nbb-deps" "plin_platform/client_boot.cljs")]

      ;; D. Root (Index)
      (or (= clean-url "/") (= clean-url "/index.html"))
      [(path/join user-root "public/index.html")
       (path/join user-root "index.html")
       (path/join framework-root "public/index.html")
       (path/join framework-root "index.html")
       (path/join framework-root "nbb-deps" "public/index.html")
       (path/join framework-root "nbb-deps" "index.html")]

      ;; E. Platform manifest (plinpt/plin.edn)
      (or (str/ends-with? clean-url "plinpt/plin.edn")
          (str/ends-with? clean-url "plinpt/manifest.edn"))
      [(path/join framework-root "src/plinpt/plin.edn")
       (path/join user-root "src/plinpt/plin.edn")
       (path/join framework-root "libs/plinpt/plin.edn")
       (path/join framework-root "nbb-deps/plinpt/plin.edn")]

      ;; F. User Files and Fallback
      :else
      (let [rel (if (str/starts-with? clean-url "/")
                  (subs clean-url 1)
                  clean-url)]
        [(path/join user-root rel)
         (path/join user-root "public" rel)
         (path/join framework-root rel)
         (path/join framework-root "public" rel)
         (path/join framework-root "libs" rel)
         (path/join framework-root "nbb-deps" rel)
         (path/join framework-root "nbb-deps" "libs" rel)]))))

;; --- MIME Types ---

(def mime-types
  {".html" "text/html"
   ".js"   "text/javascript"
   ".css"  "text/css"
   ".json" "application/json"
   ".png"  "image/png"
   ".jpg"  "image/jpeg"
   ".gif"  "image/gif"
   ".svg"  "image/svg+xml"
   ".ico"  "image/x-icon"
   ".cljs" "text/plain"
   ".cljc" "text/plain"
   ".edn"  "text/plain"})

;; --- Embedded Index HTML Fallback ---

(def index-html-fallback
  "<!DOCTYPE html>
<html><head><title>PLIN</title></head>
<body><div id=\"app\">Loading...</div>
<script>document.write('Error: index.html not found');</script>
</body></html>")

;; --- File Serving ---

(defn try-read-file [res paths content-type]
  (if (empty? paths)
    (do
      (.writeHead res 404)
      (.end res "Not Found"))
    (let [file-path (first paths)]
      (fs/readFile file-path
        (fn [err data]
          (if err
            (if (= (.-code err) "ENOENT")
              (if (and (str/ends-with? file-path "index.html") (empty? (rest paths)))
                (do
                  (.writeHead res 200 #js {"Content-Type" "text/html" "Cache-Control" "no-cache"})
                  (.end res index-html-fallback))
                (try-read-file res (rest paths) content-type))
              (do
                (.writeHead res 500)
                (.end res (str "Server Error: " (.-message err)))))
            (do
              (.writeHead res 200 #js {"Content-Type" content-type "Cache-Control" "no-cache"})
              (.end res data))))))))

(defn serve-static [req res]
  (let [url (.-url req)
        clean-url (first (str/split url #"\?"))
        ext (if (= clean-url "/") ".html" (path/extname clean-url))
        content-type (get mime-types ext "application/octet-stream")
        resolved-paths (resolve-path url)]
    (try-read-file res resolved-paths content-type)))

;; --- JSON Helpers ---

(defn to-json-compatible [x]
  (cond
    (nil? x) x
    (keyword? x) (subs (str x) 1)
    (symbol? x) (str x)
    (map? x) (let [obj (js-obj)]
               (doseq [[k v] x]
                 (aset obj (if (keyword? k) (name k) (str k)) (to-json-compatible v)))
               obj)
    (set? x) (to-array (map to-json-compatible x))
    (sequential? x) (to-array (map to-json-compatible x))
    (js/Array.isArray x) (to-array (map to-json-compatible x))
    :else x))

;; --- Request Handler Factory ---

(defn make-request-handler [invoke-fn]
  (fn [req res]
    (let [method (.-method req)
          url (.-url req)]

      (.setHeader res "Access-Control-Allow-Origin" "*")
      (.setHeader res "Access-Control-Allow-Methods" "POST, GET, OPTIONS")
      (.setHeader res "Access-Control-Allow-Headers" "Content-Type")
      
      (cond
        (= method "OPTIONS")
        (do (.writeHead res 200) (.end res))
        
        (and (= method "POST") (str/starts-with? url "/api/"))
        (let [body (atom "")]
          (.on req "data" #(swap! body str %))
          (.on req "end"
               (fn []
                 (try
                   (let [json-body (js/JSON.parse @body)
                         clj-body (js->clj json-body :keywordize-keys true)
                         endpoint-str (subs url 5)
                         [ns-str name-str] (str/split endpoint-str #"/")
                         endpoint (keyword ns-str name-str)]

                     (if invoke-fn
                       (-> (invoke-fn endpoint clj-body)
                           (.then (fn [result]
                                    (let [js-data (to-json-compatible result)
                                          json-str (js/JSON.stringify js-data)]
                                      (.writeHead res 200 #js {"Content-Type" "application/json"})
                                      (.end res json-str))))
                           (.catch (fn [err]
                                     (js/console.error "Invoke Error:" err)
                                     (.writeHead res 500 #js {"Content-Type" "application/json"})
                                     (.end res (js/JSON.stringify #js {:error (str err)})))))
                       (do
                         (.writeHead res 500)
                         (.end res "Invoker not available"))))
                   (catch :default e
                     (js/console.error "Server Error:" e)
                     (.writeHead res 400)
                     (.end res (str "Bad Request: " e)))))))
        
        (= method "GET")
        (serve-static req res)
        
        :else
        (do
          (.writeHead res 404)
          (.end res "Not Found"))))))

;; --- Server Startup ---

(defn start-server! [port invoke-fn]
  (let [handler (make-request-handler invoke-fn)
        server (http/createServer handler)]
    (.on server "error"
         (fn [e]
           (if (= (.-code e) "EADDRINUSE")
             (do
               (println "Port" port "is busy, trying" (inc port) "...")
               (start-server! (inc port) invoke-fn))
             (js/console.error "Server error:" e))))
    (.listen server port
             (fn []
               (println "Server listening on port" port)
               (println "Framework Root:" framework-root)
               (println "User Root:" user-root)
               (println "Application available at:")
               (println (str "- http://localhost:" port "/?mode=demo"))
               (println (str "- http://localhost:" port "/?mode=client"))))))

;; --- Boot Function ---

(defn server-boot-fn
  "The ::boot/boot-fn implementation for server environments.
   Extracts the invoker from the container and starts the HTTP server."
  [invoke-fn _container]
  (println "Starting server...")
  (start-server! 8000 invoke-fn))

;; --- Plugin Definition ---

(def plugin
  (plin/plugin
   {:doc "Server-side boot plugin. Starts the HTTP server."
    :deps [boot/plugin i-invoker/plugin]
    
    :beans
    {::boot/boot-fn
     ^{:doc "Boot function that starts the HTTP server."
       :api {:args [["container" {} :map]] :ret :nil}}
     {:constructor [(fn [invoke-fn]
                      (fn [container]
                        (server-boot-fn invoke-fn container)))
                    ::i-invoker/invoke]}}}))
