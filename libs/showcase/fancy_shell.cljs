(ns showcase.fancy-shell
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-application :as iapp]
            [plinpt.i-router :as irouter]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-session :as isession]
            [plinpt.i-head-config :as ihead]))

;; --- State ---

(defonce shell-state (r/atom {:sidebar-open? true
                              :glitch? false}))

;; --- Glitch Effect ---

(defn trigger-glitch! []
  (swap! shell-state assoc :glitch? true)
  (js/setTimeout #(swap! shell-state assoc :glitch? false) 150))

;; --- Helpers ---

(defn- normalize-path [path]
  (if (or (str/blank? path) (= path "#"))
    "/"
    (str/replace path #"^#" "")))

(defn- path-matches? [current-path item-path]
  (let [current (normalize-path current-path)
        target (normalize-path item-path)]
    (or (= current target)
        (and (not= target "/")
             (str/starts-with? current (str target "/"))))))

;; --- Components ---

(defn ascii-logo []
  [:pre {:class "text-[10px] leading-tight font-mono text-green-500 select-none"
         :style {:text-shadow "0 0 10px rgba(34,197,94,0.5)"}}
   "██████╗ ██╗     ██╗███╗   ██╗\n"
   "██╔══██╗██║     ██║████╗  ██║\n"
   "██████╔╝██║     ██║██╔██╗ ██║\n"
   "██╔═══╝ ██║     ██║██║╚██╗██║\n"
   "██║     ███████╗██║██║ ╚████║\n"
   "╚═╝     ╚══════╝╚═╝╚═╝  ╚═══╝"])

(defn scanline-overlay []
  [:div {:class "pointer-events-none fixed inset-0 z-50 opacity-[0.03]"
         :style {:background "repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,0,0,0.3) 2px, rgba(0,0,0,0.3) 4px)"}}])

(defn terminal-prompt []
  (let [time-str (r/atom "")]
    (fn []
      (reset! time-str (.toLocaleTimeString (js/Date.)))
      [:div {:class "font-mono text-xs text-green-600 flex items-center gap-2"}
       [:span {:class "text-green-400"} "root@plin"]
       [:span {:class "text-gray-600"} ":"]
       [:span {:class "text-blue-400"} "~"]
       [:span {:class "text-gray-600"} "$"]
       [:span {:class "text-gray-500 ml-2"} @time-str]
       [:span {:class "animate-pulse text-green-400"} "█"]])))

(defn nav-item [item current-path navigate!]
  (let [active? (path-matches? current-path (:full-route item))
        label (:label item)]
    [:a {:href (str "#" (:full-route item))
         :on-click (fn [e]
                     (.preventDefault e)
                     (trigger-glitch!)
                     (navigate! (:full-route item)))
         :class (str "block px-4 py-2 font-mono text-sm border-l-2 transition-all duration-100 "
                     (if active?
                       "border-green-500 bg-green-500/10 text-green-400"
                       "border-transparent text-gray-500 hover:border-green-800 hover:text-green-600 hover:bg-green-900/20"))}
     [:span {:class "mr-2 text-green-700"} ">"]
     [:span {:class (when active? "glitch-text")} label]]))

(defn sidebar [structure current-path navigate! user-data user-actions]
  (let [open? (:sidebar-open? @shell-state)
        {:keys [logged? name initials]} @user-data
        {:keys [login! logout!]} user-actions]
    [:aside {:class (str "fixed left-0 top-0 h-full bg-black border-r border-green-900/50 flex flex-col z-40 transition-all duration-300 "
                         (if open? "w-64" "w-0 overflow-hidden"))}
     
     ;; Header
     [:div {:class "p-4 border-b border-green-900/30"}
      [ascii-logo]
      [:div {:class "mt-3"}
       [terminal-prompt]]]
     
     ;; Navigation
     [:nav {:class "flex-1 py-4 overflow-y-auto overflow-x-hidden scrollbar-thin"}
      [:div {:class "px-4 mb-2 text-[10px] font-mono text-green-800 uppercase tracking-widest"} 
       "// navigation"]
      (doall
       (for [item (sort-by :order structure)]
         ^{:key (:id item)}
         [nav-item item current-path navigate!]))]
     
     ;; User Section
     [:div {:class "p-4 border-t border-green-900/30 bg-green-950/20"}
      [:div {:class "text-[10px] font-mono text-green-800 uppercase tracking-widest mb-2"} 
       "// session"]
      (if logged?
        [:div {:class "space-y-2"}
         [:div {:class "flex items-center gap-3"}
          [:div {:class "w-8 h-8 rounded border border-green-700 bg-green-900/30 flex items-center justify-center font-mono text-green-400 text-sm"}
           initials]
          [:div {:class "flex-1 min-w-0"}
           [:div {:class "font-mono text-sm text-green-400 truncate"} name]
           [:div {:class "font-mono text-[10px] text-green-700"} "authenticated"]]]
         [:button {:on-click logout!
                   :class "w-full mt-2 px-3 py-1.5 font-mono text-xs border border-red-900/50 text-red-500 hover:bg-red-900/20 hover:border-red-700 transition-all"}
          "[logout]"]]
        [:button {:on-click login!
                  :class "w-full px-3 py-1.5 font-mono text-xs border border-green-700 text-green-500 hover:bg-green-900/30 hover:text-green-400 transition-all"}
         "[authenticate]"])]
     
     ;; Footer
     [:div {:class "p-4 border-t border-green-900/30"}
      [:div {:class "font-mono text-[10px] text-green-900"}
       "sys.version: 0.1.0"
       [:br]
       "status: " [:span {:class "text-green-600"} "ONLINE"]]]]))

(defn toggle-button []
  [:button {:on-click #(swap! shell-state update :sidebar-open? not)
            :class "fixed top-4 left-4 z-50 w-10 h-10 bg-black border border-green-800 text-green-500 font-mono text-lg hover:bg-green-900/30 hover:border-green-600 transition-all flex items-center justify-center"}
   (if (:sidebar-open? @shell-state) "«" "»")])

(defn breadcrumb-bar [current-path]
  [:div {:class "h-10 bg-black/80 border-b border-green-900/30 flex items-center px-4 font-mono text-sm"}
   [:span {:class "text-green-700"} "location: "]
   [:span {:class "text-green-400"} (or current-path "/")]
   [:span {:class "ml-4 text-gray-700"} "|"]
   [:span {:class "ml-4 text-gray-600"} "press "]
   [:kbd {:class "px-1.5 py-0.5 bg-gray-900 border border-gray-700 text-gray-400 text-xs rounded mx-1"} "Ctrl+K"]
   [:span {:class "text-gray-600"} " for command palette"]])

(defn main-content [current-route can?]
  (let [route @current-route
        Page (:component route)
        path (:path route)]
    [:main {:class "flex-1 overflow-auto bg-gray-950"}
     [breadcrumb-bar path]
     [:div {:class "p-6 min-h-full"}
      (cond
        (not Page)
        [:div {:class "flex flex-col items-center justify-center h-96 font-mono"}
         [:pre {:class "text-red-500 text-center mb-4"
                :style {:text-shadow "0 0 10px rgba(239,68,68,0.5)"}}
          "╔═══════════════════════════════╗\n"
          "║     ERROR 404: NOT FOUND      ║\n"
          "╚═══════════════════════════════╝"]
         [:div {:class "text-gray-600 text-sm"} "The requested resource does not exist."]]
        
        (and (:required-perm route) (not (can? (:required-perm route))))
        [:div {:class "flex flex-col items-center justify-center h-96 font-mono"}
         [:pre {:class "text-yellow-500 text-center mb-4"
                :style {:text-shadow "0 0 10px rgba(234,179,8,0.5)"}}
          "╔═══════════════════════════════╗\n"
          "║   ACCESS DENIED: FORBIDDEN    ║\n"
          "╚═══════════════════════════════╝"]
         [:div {:class "text-gray-600 text-sm"} "Insufficient permissions to access this resource."]]
        
        :else
        [:div {:class (when (:glitch? @shell-state) "animate-pulse")}
         [Page]])]]))

(defn hacker-shell [structure current-route navigate! setup! homepage
                    overlays login-modal can? user-data user-actions]
  (r/create-class
   {:component-did-mount
    (fn []
      (let [home-path (or (:route homepage) "/")]
        (setup! home-path)))
    
    :reagent-render
    (fn []
      (let [route @current-route
            path (:path route)
            sidebar-open? (:sidebar-open? @shell-state)]
        [:div {:class "min-h-screen bg-black text-gray-300 font-sans"}
         
         ;; Scanline effect
         [scanline-overlay]
         
         ;; Sidebar
         [sidebar structure path navigate! user-data user-actions]
         
         ;; Toggle button (visible when sidebar closed)
         (when-not sidebar-open?
           [toggle-button])
         
         ;; Main area
         [:div {:class (str "min-h-screen flex flex-col transition-all duration-300 "
                            (if sidebar-open? "ml-64" "ml-0"))}
          
          ;; Header bar
          [:header {:class "h-12 bg-black border-b border-green-900/30 flex items-center justify-between px-4"}
           (when sidebar-open?
             [:button {:on-click #(swap! shell-state update :sidebar-open? not)
                       :class "text-green-700 hover:text-green-500 font-mono text-sm"}
              "[collapse]"])
           [:div {:class "flex-1"}]
           [:div {:class "font-mono text-xs text-gray-700"}
            "PLIN PLATFORM // UNDERGROUND EDITION"]]
          
          ;; Content
          [main-content current-route can?]]
         
         ;; Login Modal
         (when login-modal [login-modal])
         
         ;; Overlays
         (for [[idx comp] (map-indexed vector overlays)]
           ^{:key idx} [comp])]))}))

;; --- Custom Styles ---

(def custom-styles
  "
  @keyframes glitch {
    0% { transform: translate(0); }
    20% { transform: translate(-2px, 2px); }
    40% { transform: translate(-2px, -2px); }
    60% { transform: translate(2px, 2px); }
    80% { transform: translate(2px, -2px); }
    100% { transform: translate(0); }
  }
  
  .glitch-text {
    animation: glitch 0.1s ease-in-out;
  }
  
  /* Custom scrollbar for hacker theme */
  .scrollbar-thin::-webkit-scrollbar {
    width: 4px;
  }
  .scrollbar-thin::-webkit-scrollbar-track {
    background: transparent;
  }
  .scrollbar-thin::-webkit-scrollbar-thumb {
    background: #166534;
    border-radius: 2px;
  }
  .scrollbar-thin::-webkit-scrollbar-thumb:hover {
    background: #22c55e;
  }
  
  /* Terminal cursor blink */
  @keyframes blink {
    0%, 50% { opacity: 1; }
    51%, 100% { opacity: 0; }
  }
  ")

(def plugin
  (plin/plugin
   {:doc "A hacker/underground themed shell with terminal aesthetics."
    :deps [iapp/plugin irouter/plugin iauth/plugin isession/plugin ihead/plugin]
    
    :contributions
    {::ihead/inline-styles [custom-styles]
     
     ;; Override the main application UI with our hacker shell
     ::iapp/ui ::ui}
    
    :beans
    {;; The hacker shell component
     ::ui
     ^{:doc "Hacker Shell UI component."
       :reagent-component true}
     [partial hacker-shell
      ::iapp/structure
      ::irouter/current-route
      ::irouter/navigate!
      ::irouter/setup!
      ::iapp/homepage
      ::iapp/overlay-components
      ::isession/login-modal
      ::iauth/can?
      ::isession/user-data
      ::isession/user-actions]}}))
