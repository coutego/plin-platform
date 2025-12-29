(ns plinpt.p-session.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- State ---

(defonce session-state (r/atom {:current-user nil
                                :show-login? false}))

;; Store navigate function if injected
(defonce navigate-fn (atom nil))

;; --- Helpers ---

(defn- parse-json-field [val]
  (if (string? val)
    (try
      (js->clj (js/JSON.parse val) :keywordize-keys true)
      (catch :default _ val))
    val))

(defn- to-keyword [v]
  (if (string? v)
    (if (str/starts-with? v ":")
      (keyword (subs v 1))
      (keyword v))
    v))

(defn- normalize-set [val]
  (let [coll (if (string? val) (parse-json-field val) val)]
    (if (coll? coll)
      (set (map to-keyword coll))
      #{})))

(defn- normalize-user [user]
  (-> user
      (update :roles normalize-set)
      (update :groups normalize-set)
      (update :permissions normalize-set)
      (update :id to-keyword)))

(defn- get-initials [name]
  (when (and name (string? name) (seq name))
    (let [parts (str/split (str/trim name) #"\s+")]
      (if (= 1 (count parts))
        (str/upper-case (subs (first parts) 0 (min 2 (count (first parts)))))
        (str/upper-case (str (first (first parts)) (first (second parts))))))))

(defn- do-navigate! [path]
  (if-let [nav @navigate-fn]
    (nav path)
    (set! (.-hash js/location) path)))

;; --- Accessors ---

(defn get-current-user []
  (:current-user @session-state))

;; --- Logic / Permission Resolution ---

(defn get-permissions []
  (let [user (get-current-user)]
    (:permissions user)))

(defn can? [perm]
  (let [perms (get-permissions)]
    ;; Allow :perm/admin to grant all permissions
    (or (contains? perms :perm/admin)
        (contains? perms perm))))

(defn has-permission? [perm] (can? perm))
(defn has-all? [perms] (every? can? perms))
(defn has-any? [perms] (some can? perms))

;; --- Actions ---

(defn set-user! [user]
  (let [norm-user (when user (normalize-user user))]
    (js/console.log "Session: Setting user to" (:name norm-user) "with permissions" (clj->js (:permissions norm-user)))
    (swap! session-state assoc :current-user norm-user :show-login? false)
    (when norm-user
      (do-navigate! "/"))))

(defn open-login! []
  (swap! session-state assoc :show-login? true))

(defn close-login! []
  (swap! session-state assoc :show-login? false))

(defn logout! []
  (set-user! nil))

(defn show-profile! []
  ;; Default implementation: navigate to a profile page if it exists
  (do-navigate! "/profile"))

;; --- Derived User Data (for UI consumption) ---

(defn make-user-data-atom []
  "Creates a reactive track that derives user-data from session-state."
  (r/track
   (fn []
     (let [user (:current-user @session-state)]
       (if user
         {:logged? true
          :name (:name user)
          :initials (get-initials (:name user))
          :avatar-url (:avatar-url user)
          :roles (or (:roles user) #{})
          :permissions (or (:permissions user) #{})}
         {:logged? false
          :name nil
          :initials nil
          :avatar-url nil
          :roles #{}
          :permissions #{}})))))

(defn make-user-actions [router-navigate!]
  "Returns a map of user actions. Optionally uses router's navigate! if provided."
  (when router-navigate!
    (reset! navigate-fn router-navigate!))
  {:login! open-login!
   :logout! logout!
   :show-profile! show-profile!})

;; --- Components ---

(defn login-modal [user-service]
  (let [users-atom (r/atom nil)
        loading? (r/atom false)
        error (r/atom nil)
        
        load-users!
        (fn []
          (reset! loading? true)
          (-> ((:list user-service))
              (.then (fn [users] 
                       (let [norm-users (map normalize-user users)]
                         (reset! users-atom norm-users)
                         (reset! loading? false))))
              (.catch (fn [e] 
                        (js/console.error "Session: Error loading users" e)
                        (reset! error (str e))
                        (reset! loading? false)))))]
    
    ;; Load users when the component is created (mounted)
    (load-users!)

    (fn []
      (let [visible? (:show-login? @session-state)
            users @users-atom]
        (if visible?
          [:div {:class "fixed inset-0 z-50 flex items-center justify-center"}
           [:div {:class "fixed inset-0 bg-black opacity-50" :on-click #(close-login!)}]
           [:div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md z-50"}
            [:div {:class "flex justify-between items-center mb-4"}
             [:h2 {:class "text-xl font-bold text-gray-900"} "Select User"]
             [:button {:class "text-gray-400 hover:text-gray-500" :on-click #(close-login!)}
              [:span {:class "text-2xl"} "×"]]]

            (cond
              @loading? [:div {:class "text-center py-4"} "Loading users..."]
              @error [:div {:class "text-red-500 text-center py-4"} @error]
              (empty? users) [:div {:class "text-gray-500 text-center py-4"} "No users found."]
              :else
              [:div {:class "space-y-2 max-h-96 overflow-y-auto"}
               (for [user (sort-by :name users)]
                 ^{:key (:id user)}
                 [:button {:class "w-full text-left px-4 py-3 rounded hover:bg-blue-50 border border-gray-200 flex items-center justify-between group transition-colors"
                           :on-click #(set-user! user)}
                  [:div
                   [:div {:class "font-medium text-gray-900 group-hover:text-blue-700"} (:name user)]
                   [:div {:class "text-xs text-gray-500"}
                    (let [roles (:roles user)]
                      (if (empty? roles)
                        "No roles"
                        (str "Roles: " (str/join ", " (map name roles)))))]]
                  [:div {:class "h-2 w-2 rounded-full bg-gray-300 group-hover:bg-blue-500"}]])])

            [:div {:class "mt-6 text-center"}
             [:p {:class "text-xs text-gray-400"} "Mock Authentication - No password required"]]]]
          [:div {:style {:display "none"}}])))))

(defn user-widget
  "Default user widget implementation using user-data and user-actions."
  [user-data user-actions]
  (fn []
    (let [{:keys [logged? name initials]} @user-data
          {:keys [login! logout!]} user-actions]
      (if logged?
        [:div {:class "relative group h-full flex items-center"}
         [:button {:class "flex items-center space-x-3 focus:outline-none"}
          [:div {:class "h-8 w-8 rounded-full bg-blue-700 flex items-center justify-center text-white font-bold shadow-sm border border-blue-500"}
           initials]
          [:div {:class "text-left hidden md:block"}
           [:p {:class "text-sm font-medium text-white"} name]
           [:p {:class "text-xs text-blue-200"} "Logged in"]]]

         ;; Dropdown
         [:div {:class "absolute right-0 top-full pt-2 w-48 hidden group-hover:block z-50"}
          [:div {:class "bg-white rounded-md shadow-lg py-1 ring-1 ring-black ring-opacity-5"}
           [:div {:class "px-4 py-2 border-b border-gray-100"}
            [:p {:class "text-sm font-medium text-gray-900"} name]]
           [:a {:href "#" :class "block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                :on-click #(do (.preventDefault %) (logout!))}
            "Sign out"]]]]

        [:button {:class "inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none shadow-sm"
                  :on-click #(login!)}
         "Log in"]))))
