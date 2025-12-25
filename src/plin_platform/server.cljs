(ns plin-platform.server
  (:require [plin.boot :as boot]
            [clojure.string :as str]
            ["http" :as http]
            ["fs" :as fs]
            ["path" :as path]
            [promesa.core :as p]
            
            ;; Interfaces
            [plinpt.i-devdoc :as i-devdoc]
            [plinpt.i-service-invoker :as i-invoker]
            [plinpt.i-db :as i-db]
            [plinpt.i-tracer :as i-tracer]
            [plinpt.i-service-authorization :as i-service-auth]
            
            ;; Implementations
            [plinpt.p-alasql-db :as p-db]
            [plinpt.p-service-invoker-demo :as p-invoker]
            [plinpt.p-service-authorization :as p-service-auth]))

;; --- Embedded Index HTML Fallback ---
;; Used if public/index.html cannot be found in the framework path (e.g. nbb cache issues)

(def index-html-content
  "<!DOCTYPE html>
<html lang=\"en\">

<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>ClojureScript Dashboard</title>

    <script crossorigin src=\"https://unpkg.com/react@18/umd/react.production.min.js\"></script>
    <script crossorigin src=\"https://unpkg.com/react-dom@18/umd/react-dom.production.min.js\"></script>

    <script>
        window.SCITTLE_NREPL_WEBSOCKET_PORT = 1337;
    </script>

    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.js\"></script>
    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.reagent.js\"></script>
    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.nrepl.js\"></script>
    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.pprint.js\"></script>
    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.promesa.js\"></script>

    <script src=\"https://cdn.tailwindcss.com\"></script>
    <script src=\"https://cdn.tailwindcss.com?plugins=typography\"></script>

    <script src=\"https://cdn.jsdelivr.net/npm/marked/marked.min.js\"></script>
    <script src=\"https://cdn.jsdelivr.net/npm/alasql@4.16.0/dist/alasql.min.js\"></script>
    <script src=\"https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js\"></script>
    <script src=\"https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js\"></script>

    <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css\">
    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js\"></script>
    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/clojure.min.js\"></script>

    <style>
        body {
            margin: 0;
            font-family: -apple-system, sans-serif;
        }
    </style>


    <script>
        window.addEventListener(\"load\", function() {
            setTimeout(function() {
                // The script creates 'window.ws_nrepl' when connected
                if (window.ws_nrepl) {
                    console.log(\"✅ WebSocket Wrapper found: window.ws_nrepl\");
                    console.log(\"Ready state:\", window.ws_nrepl.readyState); // 1 = Connected
                } else {
                    console.error(\"❌ window.ws_nrepl is missing. Connection failed.\");
                }
            }, 500); // Give it a moment to initialize
        });
    </script>
</head>

<body class=\"bg-gray-100\">
    <div id=\"app\"></div>

    <script>
        var v = Date.now();
        console.log(\"App Version (v):\", v);
        function load(path) {
            document.write('<script type=\"application/x-scittle\" src=\"' + path + '?v=' + v + '\"><\\/script>');
        }

        // --- CONFIGURATION START ---
        
        // 1. Check URL params for mode (e.g. index.html?mode=client)
        const urlParams = new URLSearchParams(window.location.search);
        const urlMode = urlParams.get('mode');

        // 2. Default to demo if not specified
        window.APP_MODE = urlMode || \"demo\"; 
        
        console.log(\"🚀 Starting App in mode:\", window.APP_MODE);
        
        // 3. Fetch and Parse Manifests
        function fetchEdn(path) {
            var xhr = new XMLHttpRequest();
            xhr.open(\"GET\", path, false); // Synchronous
            xhr.send(null);
            if (xhr.status === 200) {
                return xhr.responseText;
            } else {
                console.warn(\"Failed to load \" + path);
                return null;
            }
        }

        function parseEDN(edn) {
            if (!edn) return [];
            try {
                return scittle.core.eval_string(\"(cljs.core/clj->js \" + edn + \"\\n)\");
            } catch (e) {
                console.error(\"Failed to parse EDN\", e);
                return [];
            }
        }

        // A. Load User Manifest
        var userEdn = fetchEdn(\"manifest.edn\");
        var userManifest = parseEDN(userEdn);
        
        // B. Check for Config (Opt-out of platform plugins)
        // We look for a special entry: {:config {:include-platform? false}}
        var configEntry = userManifest.find(function(item) { return item.config; });
        var includePlatform = true;
        if (configEntry && configEntry.config && configEntry.config['include-platform?'] === false) {
            includePlatform = false;
        }

        // C. Load Platform Manifest (if not opted out)
        var platformManifest = [];
        if (includePlatform) {
            var platformEdn = fetchEdn(\"libs/plinpt/manifest.edn\");
            platformManifest = parseEDN(platformEdn);
        }

        // D. Merge
        window.SYSTEM_MANIFEST = platformManifest.concat(userManifest);
        console.log(\"📦 Loaded Plugins:\", window.SYSTEM_MANIFEST.length, \"(Platform: \" + platformManifest.length + \")\");

        // --- CONFIGURATION END ---

        // Load Libs (Static)
        load(\"libs/malli/core.cljs\");
        load(\"libs/malli/error.cljs\");
        load(\"libs/pluggable/container.cljc\");
        load(\"libs/pluggable/root_plugin.cljc\");
        load(\"libs/pluggable/core.cljc\");
        load(\"libs/injectable/easy.cljc\");
        load(\"libs/injectable/container.cljc\");
        load(\"libs/injectable/core.cljc\");
        load(\"libs/plin/core.cljc\");
        load(\"libs/plin/boot.cljc\");

        // Load Plugins Dynamically
        var profiles = {
            \"demo\": [\"shared\", \"ui\", \"demo\"],
            \"client\": [\"shared\", \"ui\", \"client\"]
        };
        var activeTags = new Set(profiles[window.APP_MODE]);

        window.SYSTEM_MANIFEST.forEach(function(plugin) {
            // Skip config entries
            if (plugin.config) return;

            var hasTag = plugin.tags.some(function(t) { return activeTags.has(t); });
            // Only load CLJS plugins (or unspecified type) via script tags
            var isCljs = !plugin.type || plugin.type === 'cljs';
            
            if (hasTag && isCljs) {
                // Load all files associated with this plugin
                plugin.files.forEach(function(file) {
                    load(file);
                });
            }
        });

        // Main Entry Point
        load(\"client_boot.cljs\");
    </script>
</body>

</html>")

;; 1. Determine Roots
(def user-root (js/process.cwd))

;; In NBB, __dirname might not point where we expect if running from a JAR/Git cache.
;; A robust way is to find where this file is located.
;; This file is in src/plin_platform/server.cljs, so we go up two levels to get to the package root.
(def framework-root
  (path/resolve (path/dirname *file*) "../.."))

(defn resolve-path [url]
  (let [clean-url (first (str/split url #"\?"))]
    (let [candidates
          (cond
            ;; A. Framework Assets (Libs)
            (str/starts-with? clean-url "/libs/")
            (let [rel (subs clean-url 1)             ;; libs/malli/core.cljs
                  stripped (subs clean-url 6)]       ;; malli/core.cljs
              [;; 1. Direct in framework root
               (path/join framework-root rel)
               (path/join framework-root stripped)
               ;; 2. Inside nbb-deps (if it exists)
               (path/join framework-root "nbb-deps" rel)
               (path/join framework-root "nbb-deps" stripped)])

            ;; B. Framework Bootstrapper (Special Case)
            (= clean-url "/client_boot.cljs")
            (do
              [(path/join framework-root "src/plin_platform/client_boot.cljs")
               (path/join framework-root "plin_platform/client_boot.cljs")
               (path/join framework-root "nbb-deps" "src/plin_platform/client_boot.cljs")
               (path/join framework-root "nbb-deps" "plin_platform/client_boot.cljs")])

            ;; C. Root (Index)
            (or (= clean-url "/") (= clean-url "/index.html"))
            (do
              [(path/join framework-root "public/index.html")
               (path/join framework-root "index.html")
               (path/join framework-root "nbb-deps" "public/index.html")
               (path/join framework-root "nbb-deps" "index.html")])

            ;; D. User Files (src, manifest, public assets)
            ;;    AND Fallback for implicit libs/ requests (Scittle auto-resolution)
            :else
            (let [rel (if (str/starts-with? clean-url "/")
                        (subs clean-url 1)
                        clean-url)]
              [;; 1. User Root
               (path/join user-root rel)
               ;; 2. Framework Libs (Implicit)
               (path/join framework-root "libs" rel)
               ;; 3. Framework NBB Deps (Implicit)
               (path/join framework-root "nbb-deps" rel)
               ;; 4. Framework NBB Deps Libs (Implicit)
               (path/join framework-root "nbb-deps" "libs" rel)]))]
      
      candidates)))

;; Define the server profile plugins
(def plugins
  [i-devdoc/plugin
   i-invoker/plugin
   i-db/plugin
   i-tracer/plugin
   i-service-auth/plugin
   
   p-db/plugin
   p-invoker/plugin
   p-service-auth/plugin])

;; Bootstrap the system
(def state (atom nil))

(-> (boot/bootstrap! plugins)
    (p/then (fn [result] (reset! state result)))
    (p/catch (fn [err] (println "Error bootstrapping: " err))))

;; Helper to get the invoke function from the container
(defn get-invoker []
  (try
    (let [s @state]
      (if s
        (let [container (:container s)]
          (::i-invoker/invoke container))
        (do
          (println "Warning: Server state is nil (bootstrapping?)")
          nil)))
    (catch :default e
      (println "Error in get-invoker:" e)
      nil)))

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
              (do
                ;; Fallback for index.html if missing from cache
                (if (and (str/ends-with? file-path "index.html") (empty? (rest paths)))
                  (do
                    (.writeHead res 200 #js {"Content-Type" "text/html" "Cache-Control" "no-cache"})
                    (.end res index-html-content))
                  (try-read-file res (rest paths) content-type)))
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
        
        ;; Use the new resolve-path logic
        resolved-paths (resolve-path url)]
    
    ;; try-read-file expects a list of paths
    (try-read-file res resolved-paths content-type)))

(defn to-json-compatible [x]
  (cond
    (nil? x) x
    (keyword? x) (subs (str x) 1) ;; Convert :ns/name to "ns/name"
    (symbol? x) (str x)
    (map? x) (let [obj (js-obj)]
               (doseq [[k v] x]
                 (aset obj (if (keyword? k) (name k) (str k)) (to-json-compatible v)))
               obj)
    (set? x) (let [arr (js/Array.)]
               (doseq [item x]
                 (.push arr (to-json-compatible item)))
               arr)
    (sequential? x) (let [arr (js/Array.)]
                      (doseq [item x]
                        (.push arr (to-json-compatible item)))
                      arr)
    (js/Array.isArray x) (let [arr (js/Array.)]
                           (doseq [item x]
                             (.push arr (to-json-compatible item)))
                           arr)
    :else x))

(defn handle-request [req res]
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
                       endpoint (keyword ns-str name-str)
                       invoke (get-invoker)]

                   (if invoke
                     (do
                       (-> (invoke endpoint clj-body)
                           (p/then (fn [result]
                                     (try
                                       (let [js-data (to-json-compatible result)
                                             json-str (js/JSON.stringify js-data)]
                                         (.writeHead res 200 #js {"Content-Type" "application/json"})
                                         (.end res json-str))
                                       (catch :default e
                                         (println "Error during serialization:" e)
                                         (throw e)))))
                           (p/catch (fn [err]
                                      (js/console.error "Invoke Error:" err)
                                      (.writeHead res 500 #js {"Content-Type" "application/json"})
                                      (.end res (js/JSON.stringify #js {:error (str err)}))))))
                     (do
                       (println "System not ready (Invoker not found)")
                       (.writeHead res 500)
                       (.end res "System not ready (Invoker not found)"))))
                 (catch :default e
                   (js/console.error "Server Error:" e)
                   (.writeHead res 400)
                   (.end res (str "Bad Request: " e)))))))
      
      (= method "GET")
      (serve-static req res)
      
      :else
      (do
        (.writeHead res 404)
        (.end res "Not Found")))))

(defn start-server [port]
  (let [server (http/createServer handle-request)]
    (.on server "error"
         (fn [e]
           (if (= (.-code e) "EADDRINUSE")
             (do
               (println "Port" port "is busy, trying" (inc port) "...")
               (start-server (inc port)))
             (js/console.error "Server error:" e))))
    (.listen server port
             (fn []
               (println "Server listening on port" port)
               (println "Framework Root:" framework-root)
               (println "User Root:" user-root)
               (println "Application available at:")
               (println (str "- http://localhost:" port "/?mode=demo   (Demo mode - everything running in the browser)"))
               (println (str "- http://localhost:" port "/?mode=client (Client/server mode - services and DB running in a nodejs server)"))))))

(defn -main [& args]
  (println "Starting nbb server...")
  (start-server 8000))
