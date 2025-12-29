(ns plinpt.p-head-config
  (:require [plin.core :as plin]
            [plinpt.i-head-config :as ihead]))

;; --- Head Injection Logic ---

(defonce injected? (atom false))

(defn- inject-script! [src]
  (let [script (.createElement js/document "script")]
    (set! (.-src script) src)
    (set! (.-async script) false)
    (.appendChild (.-head js/document) script)))

(defn- inject-style! [href]
  (let [link (.createElement js/document "link")]
    (set! (.-rel link) "stylesheet")
    (set! (.-href link) href)
    (.appendChild (.-head js/document) link)))

(defn- inject-inline-style! [css]
  (let [style (.createElement js/document "style")]
    (set! (.-textContent style) css)
    (.appendChild (.-head js/document) style)))

(defn- inject-tailwind-config! [config]
  (when config
    (let [script (.createElement js/document "script")]
      (set! (.-textContent script) (str "tailwind.config = " (js/JSON.stringify (clj->js config))))
      (.appendChild (.-head js/document) script))))

(defn make-inject-fn
  "Creates the head injection function with all collected contributions."
  [scripts styles tailwind-config inline-styles]
  (fn []
    (when-not @injected?
      ;; Inject stylesheets first
      (doseq [href styles]
        (inject-style! href))
      
      ;; Inject inline styles
      (doseq [css inline-styles]
        (inject-inline-style! css))
      
      ;; Inject scripts
      (doseq [src scripts]
        (inject-script! src))
      
      ;; Inject Tailwind config after Tailwind script loads
      (when tailwind-config
        (js/setTimeout #(inject-tailwind-config! tailwind-config) 100))
      
      (reset! injected? true))
    nil))

(def plugin
  (plin/plugin
   {:doc "Head configuration for theme with custom Tailwind config, fonts, and styles."
    :deps [ihead/plugin]
    
    :contributions
    {::ihead/scripts ["https://cdn.tailwindcss.com"
                      "https://cdn.jsdelivr.net/npm/apexcharts"]
     
     ::ihead/styles ["https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"]
     
     ::ihead/tailwind-config {:theme
                              {:extend
                               {:colors
                                {:slate {:50 "#f8fafc"
                                        :100 "#f1f5f9"
                                        :200 "#e2e8f0"
                                        :300 "#cbd5e1"
                                        :400 "#94a3b8"
                                        :500 "#64748b"
                                        :600 "#475569"
                                        :700 "#334155"
                                        :800 "#1e293b"
                                        :900 "#0f172a"}
                                 :ec {:blue "#003399"
                                      :blue2 "#0057B8"
                                      :yellow "#FFCC00"}}
                                :fontFamily {:sans ["Inter" "sans-serif"]}
                                :borderRadius {:2xl "1rem"
                                              :3xl "1.5rem"}}}}
     
     ::ihead/inline-styles ["body {
            font-family: 'Inter', sans-serif;
            background-color: #f8fafc;
            background-image:
                radial-gradient(1200px circle at 20% 0%, rgba(0,51,153,0.03), transparent 45%),
                radial-gradient(900px circle at 90% 20%, rgba(255,204,0,0.05), transparent 40%);
        }

        /* Custom scrollbar */
        .scrollbar-thin::-webkit-scrollbar { width: 5px; height: 5px; }
        .scrollbar-thin::-webkit-scrollbar-track { background: transparent; }
        .scrollbar-thin::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 10px; }
        .scrollbar-thin::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

        /* Sidebar Transitions */
        aside {
            transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            will-change: width;
        }
        main {
            transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            will-change: margin-left;
        }

        /* Sidebar State: Collapsed */
        body.sidebar-closed aside {
            width: 4.5rem; /* w-16 plus border approx */
        }
        body.sidebar-closed aside .sidebar-text {
            display: none;
            opacity: 0;
        }
        body.sidebar-closed aside .group {
            justify-content: center;
            padding-left: 0;
            padding-right: 0;
        }
        body.sidebar-closed aside .sidebar-icon {
            margin-right: 0;
        }
        body.sidebar-closed main {
            margin-left: 4.5rem;
        }"]}
    
    :beans
    {::ihead/inject!
     ^{:doc "Injects all collected head configuration (scripts, styles, tailwind config) into the document head."
       :api {:args [] :ret :nil}}
     [make-inject-fn 
      ::ihead/scripts 
      ::ihead/styles 
      ::ihead/tailwind-config 
      ::ihead/inline-styles]}}))
