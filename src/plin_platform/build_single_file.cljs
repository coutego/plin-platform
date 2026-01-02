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

(def current-env "browser")
(def current-mode "demo")

(defn generate-template-html [head-config initially-disabled-ids]
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
        head-injections (str/join "\n    " (remove nil? [script-tags style-tags inline-styles tailwind-config-script]))
        
        ;; Generate disabled IDs array
        disabled-ids-json (js/JSON.stringify (clj->js (vec initially-disabled-ids)))]
    
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
    
    <!-- Inject Manifest and Config for the single file app -->
    <script>
      window.SYSTEM_MANIFEST = __MANIFEST_JSON__;
      window.APP_MODE = \"" current-mode "\";
      window.APP_ENV = \"" current-env "\";
      window.INITIALLY_DISABLED_IDS = " disabled-ids-json ";
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

;; Framework root: where plin-platform is installed
;; When running as dependency: node_modules/plin-platform
;; When running from repo: cwd
;; Use fs.realpathSync to resolve symlinks (important for npm link)
(def framework-root
  (let [nm-path (path/join user-root "node_modules/plin-platform")]
    (if (fs/existsSync nm-path)
      ;; Resolve symlinks to get the real path
      (fs/realpathSync nm-path)
      user-root)))

(defn file-exists? 
  "Check if file exists, resolving symlinks."
  [file-path]
  (try
    (fs/existsSync file-path)
    (catch :default _
      false)))

(defn resolve-path
  "Resolves a file path to an absolute path.
   
   Handles these cases:
   1. Framework paths (libs/*, src/plinpt/*, src/plinpt_extras/*, src/plin_platform/*) 
      - Look in framework-root first, then user-root
   2. User files - Look in user-root first, then framework-root
   
   Returns the first path that exists."
  [file-path]
  (let [fw-path (path/join framework-root file-path)
        user-path (path/join user-root file-path)
        ;; Determine if this is a framework path
        is-framework-path? (or (str/starts-with? file-path "libs/")
                               (str/starts-with? file-path "src/plinpt/")
                               (str/starts-with? file-path "src/plinpt_extras/")
                               (str/starts-with? file-path "src/plin_platform/"))
        ;; Check which paths exist
        fw-exists? (file-exists? fw-path)
        user-exists? (file-exists? user-path)]
    (cond
      ;; Framework path: prefer framework-root
      (and is-framework-path? fw-exists?) fw-path
      (and is-framework-path? user-exists?) user-path
      
      ;; User path: prefer user-root
      (and (not is-framework-path?) user-exists?) user-path
      (and (not is-framework-path?) fw-exists?) fw-path
      
      ;; Neither exists - return the expected path for error message
      is-framework-path? fw-path
      :else user-path)))

(defn read-file [file-path]
  (let [full-path (resolve-path file-path)]
    (if (file-exists? full-path)
      (fs/readFileSync full-path "utf8")
      (do
        (println "ERROR: File not found:" file-path)
        (println "  Resolved path:" full-path)
        (println "  Framework path:" (path/join framework-root file-path) "exists?" (file-exists? (path/join framework-root file-path)))
        (println "  User path:" (path/join user-root file-path) "exists?" (file-exists? (path/join user-root file-path)))
        (println "  Framework root:" framework-root)
        (println "  User root:" user-root)
        (throw (js/Error. (str "File not found: " file-path " (resolved to: " full-path ")")))))))

(defn read-edn-file [file-path]
  (when (fs/existsSync file-path)
    (try
      (edn/read-string (fs/readFileSync file-path "utf8"))
      (catch :default e
        (println "Warning: Could not parse" file-path ":" (.-message e))
        nil))))

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
        user-manifest (if user-manifest-path
                        (read-edn-file user-manifest-path)
                        [])
        
        config-entry (first (filter :config user-manifest))
        include-platform? (if (and config-entry 
                                   (contains? (:config config-entry) :include-platform?)
                                   (false? (get-in config-entry [:config :include-platform?])))
                            false
                            true)]
    
    (when user-manifest-path
      (println "User manifest:" user-manifest-path))
    
    (if include-platform?
      (let [platform-path (find-platform-manifest-path)]
        (if platform-path
          (do
            (println "Platform manifest:" platform-path)
            (let [platform-manifest (read-edn-file platform-path)]
              (vec (concat platform-manifest user-manifest))))
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

(defn should-load-plugin?
  "New filtering logic:
   Load if: (envs absent OR current-env in envs) AND (modes absent OR current-mode in modes)"
  [item env mode]
  (and
   ;; Not a config entry
   (not (:config item))
   ;; Check envs: absent means all environments
   (let [envs (:envs item)]
     (or (nil? envs) (empty? envs) (some #{(keyword env)} (map keyword envs))))
   ;; Check modes: absent means all modes
   (let [modes (:modes item)]
     (or (nil? modes) (empty? modes) (some #{(keyword mode)} (map keyword modes))))))

(defn get-initially-disabled-ids [manifest]
  (->> manifest
       (filter #(= (:enabled %) false))
       (map :id)
       (remove nil?)
       set))

(defn get-manifest-files []
  (let [manifest (get-manifest)]
    (->> manifest
         (filter #(should-load-plugin? % current-env current-mode))
         (mapcat :files)
         vec)))

(defn -main [& args]
  (println "Building single file application (via nbb)...")
  (println "Environment:" current-env "| Mode:" current-mode)
  (println "User root:" user-root)
  (println "Framework root:" framework-root)
  (println "")
  
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
              "libs/plin/bean_redefs.cljc"
              "libs/plin/core.cljc"
              "libs/plin/boot.cljc"]
        plugins (get-manifest-files)
        main-entry ["src/plin_platform/client_boot.cljs"]
        
        all-files (concat libs plugins main-entry)
        
        head-config (get-head-config)
        manifest-data (get-manifest)
        initially-disabled-ids (get-initially-disabled-ids manifest-data)]
    
    (println "")
    (println "Found" (count all-files) "files to include.")
    (when head-config
      (println "Head config detected:" (pr-str head-config)))
    (when (seq initially-disabled-ids)
      (println "Initially disabled:" initially-disabled-ids))
    
    (let [content (str/join "\n\n;; ========================================================\n\n"
                            (map (fn [f] 
                                   (str ";; File: " f "\n"
                                        (read-file f)))
                                 all-files))
          safe-content (str/replace content "</script>" "<\\u002fscript>")
          
          manifest-json (js/JSON.stringify (clj->js manifest-data))
          
          template-html (generate-template-html head-config initially-disabled-ids)
          
          final-html (-> template-html
                         (str/replace "__CONTENT__" safe-content)
                         (str/replace "__MANIFEST_JSON__" manifest-json))
          
          output-path (path/join user-root "target/index.html")]
      
      (fs/writeFileSync output-path final-html)
      (println "")
      (println "Successfully created" output-path))))
