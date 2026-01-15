(ns plin-platform.build-standalone
  "Builds a fully self-contained standalone HTML file with all dependencies inlined.
   
   Usage:
     nbb -m plin-platform.build-standalone [options]
   
   Options:
     --compress=gz   Also create gzip compressed version
     --compress=zip  Also create zip archive
     --compress=7z   Also create 7z archive  
     --compress=all  Create all compressed formats
   
   Output:
     target/standalone.html     - Self-contained HTML (~8-10MB)
     target/standalone.html.gz  - Gzip compressed (~2-3MB)
     target/standalone.zip      - Zip archive (~2-3MB)
     target/standalone.7z       - 7z archive (~2MB)"
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            ["fs" :as fs]
            ["path" :as path]
            ["zlib" :as zlib]
            ["child_process" :as child]
            [plin-platform.vendor :as vendor]
            ;; Require these namespaces to ensure nbb puts them in the classpath/cache
            [malli.core]
            [malli.error]
            [pluggable.container]
            [pluggable.root-plugin]
            [pluggable.core]
            [injectable.easy]
            [injectable.container]
            [injectable.core]
            [plin.core]
            [plin.boot]
            [plin-platform.client-boot]))

;; ============================================================================
;; Configuration
;; ============================================================================

(def current-env "browser")
(def current-mode "demo")

(def user-root (js/process.cwd))

(def framework-root
  (let [nm-path (path/join user-root "node_modules/@coutego/plin-platform")]
    (if (fs/existsSync nm-path)
      (fs/realpathSync nm-path)
      user-root)))

;; ============================================================================
;; File Utilities (copied from build_single_file.cljs for independence)
;; ============================================================================

(defn file-exists? [file-path]
  (try
    (fs/existsSync file-path)
    (catch :default _ false)))

(defn resolve-path [file-path]
  (let [fw-path (path/join framework-root file-path)
        user-path (path/join user-root file-path)
        is-framework-path? (or (str/starts-with? file-path "libs/")
                               (str/starts-with? file-path "src/plinpt/")
                               (str/starts-with? file-path "src/plinpt_extras/")
                               (str/starts-with? file-path "src/plin_platform/"))
        fw-exists? (file-exists? fw-path)
        user-exists? (file-exists? user-path)]
    (cond
      (and is-framework-path? fw-exists?) fw-path
      (and is-framework-path? user-exists?) user-path
      (and (not is-framework-path?) user-exists?) user-path
      (and (not is-framework-path?) fw-exists?) fw-path
      is-framework-path? fw-path
      :else user-path)))

(defn read-file [file-path]
  (let [full-path (resolve-path file-path)]
    (if (file-exists? full-path)
      (fs/readFileSync full-path "utf8")
      (throw (js/Error. (str "File not found: " file-path))))))

(defn read-edn-file [file-path]
  (when (fs/existsSync file-path)
    (try
      (edn/read-string (fs/readFileSync file-path "utf8"))
      (catch :default e
        (println "Warning: Could not parse" file-path ":" (.-message e))
        nil))))

;; ============================================================================
;; Manifest Handling (copied from build_single_file.cljs)
;; ============================================================================

(defn find-user-manifest-path []
  (let [candidates [(path/join user-root "plin.edn")
                    (path/join user-root "manifest.edn")
                    (path/join user-root "public/plin.edn")
                    (path/join user-root "public/manifest.edn")]]
    (first (filter fs/existsSync candidates))))

(defn find-platform-manifest-path []
  (let [candidates [(path/join framework-root "src/plinpt/plin.edn")
                    (path/join framework-root "libs/plinpt/plin.edn")
                    (path/join user-root "src/plinpt/plin.edn")
                    (path/join user-root "libs/plinpt/plin.edn")]]
    (first (filter fs/existsSync candidates))))

(defn get-manifest []
  (let [user-manifest-path (find-user-manifest-path)
        user-manifest (if user-manifest-path (read-edn-file user-manifest-path) [])
        config-entry (first (filter :config user-manifest))
        include-platform? (if (and config-entry 
                                   (contains? (:config config-entry) :include-platform?)
                                   (false? (get-in config-entry [:config :include-platform?])))
                            false true)]
    (when user-manifest-path
      (println "User manifest:" user-manifest-path))
    (if include-platform?
      (let [platform-path (find-platform-manifest-path)]
        (if platform-path
          (do
            (println "Platform manifest:" platform-path)
            (vec (concat (read-edn-file platform-path) user-manifest)))
          (do
            (println "Warning: Platform manifest not found")
            user-manifest)))
      user-manifest)))

(defn get-head-config []
  (let [user-manifest-path (find-user-manifest-path)]
    (when user-manifest-path
      (let [user-manifest (read-edn-file user-manifest-path)
            config-entry (first (filter :config user-manifest))]
        (get-in config-entry [:config :head])))))

(defn should-load-plugin? [item env mode]
  (and
   (not (:config item))
   (let [envs (:envs item)]
     (or (nil? envs) (empty? envs) (some #{(keyword env)} (map keyword envs))))
   (let [modes (:modes item)]
     (or (nil? modes) (empty? modes) (some #{(keyword mode)} (map keyword modes))))))

(defn is-js-plugin? [item] (= :js (:type item)))
(defn is-cljs-plugin? [item] (let [t (:type item)] (or (nil? t) (= :cljs t))))
(defn is-enabled? [item] (not (false? (:enabled item))))

(defn get-initially-disabled-ids [manifest]
  (->> manifest
       (filter #(= (:enabled %) false))
       (map :id)
       (remove nil?)
       set))

(defn get-cljs-manifest-files [manifest env mode]
  (->> manifest
       (filter #(should-load-plugin? % env mode))
       (filter is-cljs-plugin?)
       (mapcat :files)
       vec))

(defn get-js-plugin-entries [manifest env mode]
  (->> manifest
       (filter #(should-load-plugin? % env mode))
       (filter is-js-plugin?)
       (filter is-enabled?)
       vec))

;; ============================================================================
;; JS Plugin Embedding
;; ============================================================================

(defn escape-js-for-html 
  "Escapes JavaScript code for embedding in HTML script tags.
   Must handle all variations that browsers might interpret as closing tags."
  [code]
  (-> code
      ;; Escape </script> - the main culprit
      (str/replace "</script>" "<\\/script>")
      ;; Escape HTML comments that could break out  
      (str/replace "<!--" "<\\!--")
      ;; Note: We do NOT escape ]]> because:
      ;; 1. In HTML5, CDATA sections are not used in script tags
      ;; 2. The previous escape was breaking valid JS code like arr[n[0]]>x
      ))

(defn wrap-for-global-this
  "Wraps JavaScript code to ensure 'this' refers to 'window' when executed inline.
   Some UMD libraries use (function(root, factory){...})(this, ...) which breaks
   when 'this' is undefined in strict mode inside inline script tags.
   
   We use a more robust approach: execute the code with 'this' bound to window,
   and also ensure any references to 'this' at the top level resolve to window."
  [code id]
  ;; The issue is that UMD patterns like (function(l,ft){...})(this,...) 
  ;; capture 'this' as the first argument. In strict mode inside inline scripts,
  ;; 'this' is undefined. We need to replace the 'this' argument with 'window'.
  ;; 
  ;; Strategy: Run the code in a context where we explicitly set window properties
  ;; after execution if they weren't set.
  (let [;; For alasql specifically, we need to ensure window.alasql is set
        post-fix (case id
                   :alasql "\nif(typeof window.alasql === 'undefined' && typeof alasql !== 'undefined'){window.alasql=alasql;}"
                   :mermaid "\nif(typeof window.mermaid === 'undefined' && typeof mermaid !== 'undefined'){window.mermaid=mermaid;}"
                   "")]
    ;; Replace the UMD's `(this,` with `(window,` to fix the root binding
    (str (str/replace code #"\)\(this," ")(window,") post-fix)))

(defn generate-embedded-js-plugin [entry]
  (let [id (name (:id entry))
        files (:files entry)
        file-path (first files)]
    (when file-path
      (try
        (let [code (read-file file-path)
              escaped-code (escape-js-for-html code)
              safe-key (str/replace id #"[^a-zA-Z0-9_]" "_")]
          (str "    <script>\n"
               "      // Embedded JS Plugin: " id "\n"
               "      (function() {\n"
               "        try {\n"
               "          var pluginDef = (function() {\n"
               escaped-code "\n"
               "          })();\n"
               "          window.__PLIN_EMBEDDED_JS_PLUGINS__['" safe-key "'] = {\n"
               "            id: '" id "',\n"
               "            def: pluginDef,\n"
               "            files: ['" file-path "']\n"
               "          };\n"
               "        } catch (e) {\n"
               "          console.error('Failed to load embedded JS plugin: " id "', e);\n"
               "        }\n"
               "      })();\n"
               "    </script>\n"))
        (catch :default e
          (println "Warning: Could not read JS plugin file:" file-path (.-message e))
          nil)))))

(defn generate-all-js-plugin-scripts [js-entries]
  (if (empty? js-entries)
    ""
    (str/join "\n" (remove nil? (map generate-embedded-js-plugin js-entries)))))

;; ============================================================================
;; Standalone HTML Generation
;; ============================================================================

(defn generate-standalone-html
  "Generates a fully self-contained HTML file with all dependencies inlined."
  [head-config initially-disabled-ids js-plugin-scripts vendor-content app-content manifest-json]
  (let [;; Combine all CSS
        all-css (str/join "\n\n"
                          (map (fn [{:keys [id content description]}]
                                 (str "/* " description " (" (name id) ") */\n" content))
                               (:css vendor-content)))
        
        ;; Generate inline JS for each vendor dep
        ;; Some UMD libraries use (function(root,factory){...})(this,...) which breaks
        ;; when 'this' is undefined in strict mode inside inline script tags.
        ;; We fix those by replacing `(this,` with `(window,` in the UMD pattern.
        vendor-js-tags (str/join "\n    "
                                 (map (fn [{:keys [id content description wrap-for-this?]}]
                                        (let [processed-content (if wrap-for-this?
                                                                  (wrap-for-global-this content id)
                                                                  content)]
                                          (str "<script>/* " description " */\n"
                                               (escape-js-for-html processed-content)
                                               "\n</script>")))
                                      (:js vendor-content)))
        
        ;; User head config (scripts/styles from user manifest)
        user-script-tags (when-let [scripts (:scripts head-config)]
                           (str/join "\n    "
                                     (map (fn [script]
                                            (if (string? script)
                                              (str "<!-- External script (not inlined): " script " -->")
                                              (str "<!-- External script (not inlined): " (:src script) " -->")))
                                          scripts)))
        
        user-style-tags (when-let [styles (:styles head-config)]
                          (str/join "\n    "
                                    (map (fn [style]
                                           (let [href (if (string? style) style (:href style))]
                                             (str "<!-- External style (not inlined): " href " -->")))
                                         styles)))
        
        user-inline-styles (when-let [inline (:inline-styles head-config)]
                             inline)
        
        disabled-ids-json (js/JSON.stringify (clj->js (vec initially-disabled-ids)))]
    
    (str "<!DOCTYPE html>
<html lang=\"en\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>PLIN App (Standalone)</title>

    <!-- ============================================== -->
    <!-- INLINED CSS DEPENDENCIES                       -->
    <!-- ============================================== -->
    <style>
" all-css "

        /* Custom Application Styles */
        body {
            margin: 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
        }
        ::-webkit-scrollbar { width: 8px; height: 8px; }
        ::-webkit-scrollbar-track { background: #f1f1f1; }
        ::-webkit-scrollbar-thumb { background: #c1c1c1; border-radius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: #a8a8a8; }
" (when user-inline-styles (str "        " user-inline-styles)) "
    </style>

    <!-- ============================================== -->
    <!-- INLINED JS DEPENDENCIES                        -->
    <!-- ============================================== -->
    " vendor-js-tags "

    " (when user-script-tags (str "<!-- User-configured external scripts -->\n    " user-script-tags)) "
    " (when user-style-tags (str "<!-- User-configured external styles -->\n    " user-style-tags)) "
</head>
<body class=\"bg-gray-100\">
    <div id=\"app\">
        <div style=\"display: flex; align-items: center; justify-content: center; height: 100vh; flex-direction: column;\">
            <div style=\"font-size: 24px; color: #4a5568;\">Loading PLIN Application...</div>
            <div style=\"margin-top: 16px; color: #718096; font-size: 14px;\">Standalone build - all dependencies included</div>
        </div>
    </div>
    
    <!-- ============================================== -->
    <!-- APPLICATION CONFIGURATION                      -->
    <!-- ============================================== -->
    <script>
      window.SYSTEM_MANIFEST = " manifest-json ";
      window.APP_MODE = \"" current-mode "\";
      window.APP_ENV = \"" current-env "\";
      window.INITIALLY_DISABLED_IDS = " disabled-ids-json ";
      window.__PLIN_EMBEDDED_JS_PLUGINS__ = {};
      window.__PLIN_STANDALONE__ = true;
    </script>

    <!-- ============================================== -->
    <!-- EMBEDDED JS PLUGINS                            -->
    <!-- ============================================== -->
" js-plugin-scripts "

    <!-- ============================================== -->
    <!-- APPLICATION CODE (ClojureScript via Scittle)   -->
    <!-- ============================================== -->
    <script type=\"application/x-scittle\">
;; ============================================
;; PLIN Platform - Standalone Build
;; Generated: " (.toISOString (js/Date.)) "
;; ============================================

" app-content "
    </script>
</body>
</html>")))

;; ============================================================================
;; Compression
;; ============================================================================

(defn compress-gzip!
  "Compresses file using gzip. Returns Promise."
  [input-path output-path]
  (js/Promise.
   (fn [resolve reject]
     (let [input-stream (fs/createReadStream input-path)
           output-stream (fs/createWriteStream output-path)
           gzip (.createGzip zlib #js {:level 9})]
       (.on output-stream "finish" #(resolve output-path))
       (.on output-stream "error" reject)
       (.on input-stream "error" reject)
       (.pipe (.pipe input-stream gzip) output-stream)))))

(defn compress-zip!
  "Creates a zip archive. Returns Promise."
  [input-path output-path]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [input-filename (path/basename input-path)
             input-dir (path/dirname input-path)]
         (.execSync child (str "zip -j \"" output-path "\" \"" input-path "\"")
                    #js {:cwd input-dir :stdio "pipe"})
         (resolve output-path))
       (catch :default e
         (reject (js/Error. (str "zip compression failed: " (.-message e)
                                 "\nMake sure 'zip' is installed on your system."))))))))

(defn compress-7z!
  "Creates a 7z archive. Returns Promise."
  [input-path output-path]
  (js/Promise.
   (fn [resolve reject]
     (try
       ;; Remove existing file if present (7z won't overwrite)
       (when (fs/existsSync output-path)
         (fs/unlinkSync output-path))
       (.execSync child (str "7z a -mx=9 \"" output-path "\" \"" input-path "\"")
                  #js {:stdio "pipe"})
       (resolve output-path)
       (catch :default e
         (reject (js/Error. (str "7z compression failed: " (.-message e)
                                 "\nMake sure '7z' is installed on your system."))))))))

(defn format-file-size
  "Formats file size in human readable format."
  [path]
  (let [stats (fs/statSync path)
        bytes (.-size stats)]
    (cond
      (< bytes 1024) (str bytes " B")
      (< bytes (* 1024 1024)) (str (.toFixed (/ bytes 1024) 1) " KB")
      :else (str (.toFixed (/ bytes (* 1024 1024)) 2) " MB"))))

;; ============================================================================
;; CLI Argument Parsing
;; ============================================================================

(defn parse-args
  "Parses command line arguments."
  [args]
  (let [compress-arg (first (filter #(str/starts-with? % "--compress=") args))
        compress-value (when compress-arg
                         (keyword (subs compress-arg (count "--compress="))))]
    {:compress (case compress-value
                 :all [:gz :zip :7z]
                 :gz [:gz]
                 :zip [:zip]
                 :7z [:7z]
                 [])}))

;; ============================================================================
;; Main Build Process
;; ============================================================================

(defn -main [& args]
  (println "")
  (println "========================================")
  (println "PLIN Platform - Standalone Build")
  (println "========================================")
  (println "")
  (println "Environment:" current-env "| Mode:" current-mode)
  (println "User root:" user-root)
  (println "Framework root:" framework-root)
  
  (let [parsed-args (parse-args args)
        target-dir (path/join user-root "target")
        output-path (path/join target-dir "standalone.html")]
    
    ;; Ensure target directory exists
    (when-not (fs/existsSync target-dir)
      (fs/mkdirSync target-dir #js {:recursive true}))
    
    ;; Step 1: Ensure vendor dependencies are cached
    (-> (vendor/ensure-all-cached!)
        
        ;; Step 2: Build the standalone HTML
        (.then
         (fn [_]
           (println "")
           (println "Building standalone HTML...")
           (println "")
           
           (let [;; Read vendor content
                 vendor-content (vendor/read-vendor-by-type)
                 
                 ;; Get manifest and files
                 manifest-data (get-manifest)
                 cljs-plugins (get-cljs-manifest-files manifest-data current-env current-mode)
                 js-entries (get-js-plugin-entries manifest-data current-env current-mode)
                 
                 ;; Framework libs
                 libs ["libs/malli/core.cljs"
                       "libs/malli/error.cljs"
                       "libs/pluggable/container.cljc"
                       "libs/pluggable/root_plugin.cljc"
                       "libs/pluggable/core.cljc"
                       "libs/injectable/easy.cljc"
                       "libs/injectable/container.cljc"
                       "libs/injectable/core.cljc"
                       "libs/plin/core.cljc"
                       "libs/plin/boot.cljc"
                       "libs/plin/js_loader.cljs"]
                 
                 main-entry ["src/plin_platform/client_boot.cljs"]
                 all-cljs-files (concat libs cljs-plugins main-entry)
                 
                 head-config (get-head-config)
                 initially-disabled-ids (get-initially-disabled-ids manifest-data)
                 js-plugin-scripts (generate-all-js-plugin-scripts js-entries)
                 
                 ;; Build app content
                 app-content (str/join "\n\n;; ========================================================\n\n"
                                       (map (fn [f]
                                              (str ";; File: " f "\n"
                                                   (read-file f)))
                                            all-cljs-files))
                 safe-app-content (str/replace app-content "</script>" "<\\u002fscript>")
                 
                 manifest-json (js/JSON.stringify (clj->js manifest-data))
                 
                 ;; Generate final HTML
                 final-html (generate-standalone-html
                             head-config
                             initially-disabled-ids
                             js-plugin-scripts
                             vendor-content
                             safe-app-content
                             manifest-json)]
             
             (println "Found" (count all-cljs-files) "CLJS files")
             (println "Found" (count js-entries) "JS plugins")
             (println "Vendor JS deps:" (count (:js vendor-content)))
             (println "Vendor CSS deps:" (count (:css vendor-content)))
             (println "")
             
             ;; Write the file
             (fs/writeFileSync output-path final-html)
             (println "Created:" output-path)
             (println "Size:" (format-file-size output-path))
             
             output-path)))
        
        ;; Step 3: Compress if requested
        (.then
         (fn [html-path]
           (let [compress-formats (:compress parsed-args)]
             (if (empty? compress-formats)
               (js/Promise.resolve html-path)
               (do
                 (println "")
                 (println "Compressing...")
                 
                 (let [compress-promises
                       (map (fn [fmt]
                              (let [out-path (str html-path
                                                  (case fmt
                                                    :gz ".gz"
                                                    :zip ".zip"
                                                    :7z ".7z"))]
                                (-> (case fmt
                                      :gz (compress-gzip! html-path out-path)
                                      :zip (compress-zip! html-path out-path)
                                      :7z (compress-7z! html-path out-path))
                                    (.then (fn [p]
                                             (println "  Created:" (path/basename p) "(" (format-file-size p) ")")
                                             p))
                                    (.catch (fn [err]
                                              (println "  Warning:" (.-message err))
                                              nil)))))
                            compress-formats)]
                   (js/Promise.all (clj->js compress-promises))))))))
        
        ;; Done
        (.then
         (fn [_]
           (println "")
           (println "========================================")
           (println "Standalone build complete!")
           (println "========================================")
           (println "")))
        
        ;; Handle errors
        (.catch
         (fn [err]
           (println "")
           (println "ERROR:" (.-message err))
           (js/process.exit 1))))))
