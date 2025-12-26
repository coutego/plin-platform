(ns plin-platform.build-single-file
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            ["fs" :as fs]
            ["path" :as path]
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

(defn generate-template-html [head-config]
  (let [;; Generate script tags from config
        script-tags (when-let [scripts (:scripts head-config)]
                      (str/join "\n    "
                        (map (fn [script]
                               (if (string? script)
                                 (str "<script src=\"" script "\"></script>")
                                 (let [{:keys [src async defer type crossorigin]} script
                                       attrs (cond-> (str "src=\"" src "\"")
                                               async (str " async")
                                               defer (str " defer")
                                               type (str " type=\"" type "\"")
                                               crossorigin (str " crossorigin=\"" crossorigin "\""))]
                                   (str "<script " attrs "></script>"))))
                             scripts)))
        
        ;; Generate stylesheet links from config
        style-tags (when-let [styles (:styles head-config)]
                     (str/join "\n    "
                       (map (fn [style]
                              (let [href (if (string? style) style (:href style))
                                    media (when (map? style) (:media style))]
                                (if media
                                  (str "<link rel=\"stylesheet\" href=\"" href "\" media=\"" media "\">")
                                  (str "<link rel=\"stylesheet\" href=\"" href "\">"))))
                            styles)))
        
        ;; Generate inline styles
        inline-styles (when-let [inline (:inline-styles head-config)]
                        (str "<style>" inline "</style>"))
        
        ;; Generate Tailwind config script
        tailwind-config-script (when-let [tw-config (:tailwind head-config)]
                                 (str "<script>tailwind.config = " (js/JSON.stringify (clj->js tw-config)) ";</script>"))
        
        ;; Combine all head injections
        head-injections (str/join "\n    " (remove nil? [script-tags style-tags inline-styles tailwind-config-script]))]
    
    (str "<!DOCTYPE html>
<html lang=\"en\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>PLIN App</title>

    <script crossorigin src=\"https://unpkg.com/react@18/umd/react.production.min.js\"></script>
    <script crossorigin src=\"https://unpkg.com/react-dom@18/umd/react-dom.production.min.js\"></script>

    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.js\"></script>
    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.reagent.js\"></script>
    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.pprint.js\"></script>
    <script src=\"https://unpkg.com/scittle@0.7.28/dist/scittle.promesa.js\"></script>

    <script src=\"https://cdn.tailwindcss.com\"></script>
    <script src=\"https://cdn.tailwindcss.com?plugins=typography\"></script>
    
    " (when (seq head-injections) (str head-injections "\n    ")) "
    <script src=\"https://cdn.jsdelivr.net/npm/marked/marked.min.js\"></script>
    <script src=\"https://cdn.jsdelivr.net/npm/alasql@4.4.0/dist/alasql.min.js\"></script>
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
        /* Custom scrollbar for webkit */
        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        ::-webkit-scrollbar-track {
            background: #f1f1f1; 
        }
        ::-webkit-scrollbar-thumb {
            background: #c1c1c1; 
            border-radius: 4px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: #a8a8a8; 
        }
    </style>
</head>
<body class=\"bg-gray-100\">
    <div id=\"app\"></div>
    
    <!-- Inject Manifest for the single file app -->
    <script>
      window.SYSTEM_MANIFEST = __MANIFEST_JSON__;
      window.APP_MODE = \"demo\";
    </script>

    <!-- Application Code -->
    <script type=\"application/x-scittle\">
      ;; --- FILE CONTENT START ---
      __CONTENT__
      ;; --- FILE CONTENT END ---
    </script>
</body>
</html>")))

(def user-root (js/process.cwd))

;; We calculate the classpath root (the directory containing the namespaces)
;; Local Dev: .../src/plin_platform -> .../src
;; NBB Cache: .../nbb-deps/plin_platform -> .../nbb-deps
(def classpath-root (path/resolve (path/dirname *file*) ".."))

(defn resolve-framework-file [relative-path]
  ;; Strategy 1: Local Development (Repo structure)
  ;; relative-path is like "libs/malli/core.cljs" or "src/plin_platform/..."
  ;; classpath-root is ".../src"
  ;; We look in ".../src/../<relative-path>" -> ".../<relative-path>"
  (let [repo-path (path/resolve classpath-root ".." relative-path)]
    (if (fs/existsSync repo-path)
      repo-path
      ;; Strategy 2: NBB Cache (Flattened structure)
      ;; classpath-root is ".../nbb-deps"
      ;; We strip "libs/" or "src/" prefix and look inside classpath-root
      (let [stripped (cond
                       (str/starts-with? relative-path "libs/") (subs relative-path 5)
                       (str/starts-with? relative-path "src/") (subs relative-path 4)
                       :else relative-path)
            flattened-path (path/join classpath-root stripped)]
        (if (fs/existsSync flattened-path)
          flattened-path
          ;; Fallback to repo-path for error message consistency if neither found
          repo-path)))))

(defn resolve-path [file-path]
  (cond
    ;; Framework internal files
    (or (str/starts-with? file-path "libs/")
        (str/starts-with? file-path "src/plin_platform/"))
    (resolve-framework-file file-path)

    ;; User files
    :else
    (path/join user-root file-path)))

(defn read-file [file-path]
  (let [full-path (resolve-path file-path)]
    (try
      (fs/readFileSync full-path "utf8")
      (catch :default e
        (println "Error reading file:" full-path)
        (js/console.error e)
        (throw e)))))

(defn get-manifest []
  ;; 1. Load User Manifest
  (let [root-manifest (path/join user-root "manifest.edn")
        public-manifest (path/join user-root "public/manifest.edn")
        path (cond 
               (fs/existsSync root-manifest) "manifest.edn"
               (fs/existsSync public-manifest) "public/manifest.edn"
               :else "manifest.edn")
        user-manifest (edn/read-string (read-file path))
        
        ;; 2. Check for Opt-out
        config-entry (first (filter :config user-manifest))
        include-platform? (if (and config-entry 
                                   (contains? (:config config-entry) :include-platform?)
                                   (false? (get-in config-entry [:config :include-platform?])))
                            false
                            true)]
    
    ;; 3. Merge with Platform Manifest if needed
    (if include-platform?
      (let [platform-manifest-path "libs/plinpt/manifest.edn"]
        (println "Loading Platform Manifest from:" (resolve-path platform-manifest-path))
        (let [platform-manifest (edn/read-string (read-file platform-manifest-path))]
          (vec (concat platform-manifest user-manifest))))
      user-manifest)))

(defn get-head-config []
  ;; Extract head config from manifest
  (let [root-manifest (path/join user-root "manifest.edn")
        public-manifest (path/join user-root "public/manifest.edn")
        path (cond 
               (fs/existsSync root-manifest) "manifest.edn"
               (fs/existsSync public-manifest) "public/manifest.edn"
               :else "manifest.edn")
        user-manifest (edn/read-string (read-file path))
        config-entry (first (filter :config user-manifest))]
    (get-in config-entry [:config :head])))

(defn get-manifest-files []
  (let [manifest (get-manifest)
        ;; For the single file build, we assume "demo" mode
        active-tags #{:shared :ui :demo}]
    (->> manifest
         (filter (fn [item]
                   (let [item-tags (set (map keyword (:tags item)))]
                     (some active-tags item-tags))))
         (mapcat :files)
         vec)))

(defn -main [& args]
  (println "Building single file application (via nbb)...")
  
  ;; Ensure target directory exists in user root
  (let [target-dir (path/join user-root "target")]
    (when-not (fs/existsSync target-dir)
      (fs/mkdirSync target-dir)))

  (let [libs ["libs/malli/core.cljs"
              "libs/malli/error.cljs"
              "libs/pluggable/container.cljc"
              "libs/pluggable/root_plugin.cljc"
              "libs/pluggable/core.cljc"
              "libs/injectable/easy.cljc"
              "libs/injectable/container.cljc"
              "libs/injectable/core.cljc"
              "libs/plin/core.cljc"
              "libs/plin/boot.cljc"]
        plugins (get-manifest-files)
        ;; Use the framework's client bootstrapper
        main-entry ["src/plin_platform/client_boot.cljs"]
        
        all-files (concat libs plugins main-entry)
        
        ;; Get head config for template generation
        head-config (get-head-config)]
    
    (println "Found" (count all-files) "files to include.")
    (when head-config
      (println "Head config detected:" (pr-str head-config)))
    
    (let [content (str/join "\n\n;; ========================================================\n\n"
                            (map (fn [f] 
                                   (str ";; File: " f "\n"
                                        (read-file f)))
                                 all-files))
          ;; Escape closing script tags
          safe-content (str/replace content "</script>" "<\\u002fscript>")
          
          ;; Prepare Manifest JSON for injection
          manifest-data (get-manifest)
          manifest-json (js/JSON.stringify (clj->js manifest-data))
          
          ;; Generate template with head config
          template-html (generate-template-html head-config)
          
          ;; Replace placeholders
          final-html (-> template-html
                         (str/replace "__CONTENT__" safe-content)
                         (str/replace "__MANIFEST_JSON__" manifest-json))
          
          output-path (path/join user-root "target/index.html")]
      
      (fs/writeFileSync output-path final-html)
      (println "Successfully created" output-path))))
