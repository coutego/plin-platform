(ns plinpt.p-admin-authorization.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- State ---

(defonce ui-state (r/atom {:active-tab :users
                           :modal nil})) ;; nil, :edit-user, :edit-group, :edit-role, :edit-perm

(defonce form-state (r/atom {}))

;; --- Helpers ---

(defn- parse-json-field [val]
  (if (string? val)
    (try
      (js->clj (js/JSON.parse val) :keywordize-keys true)
      (catch :default _ val))
    val))

(defn- normalize-entity [entity]
  (-> entity
      (update :roles parse-json-field)
      (update :groups parse-json-field)
      (update :permissions parse-json-field)
      ;; Ensure sets for UI toggling
      (update :roles #(set %))
      (update :groups #(set %))
      (update :permissions #(set %))))

(defn- open-modal! [type data]
  (reset! form-state (or data {}))
  (swap! ui-state assoc :modal type))

(defn- close-modal! []
  (swap! ui-state assoc :modal nil)
  (reset! form-state {}))

(defn- save-entity! [service reload-fn data]
  (let [prefix (:id-prefix data)
        suffix (:id-suffix data)
        id (if prefix
             (keyword (str prefix suffix))
             (:id data))
        ;; Remove UI-only fields
        clean-data (dissoc data :id-prefix :id-suffix :new? :id)
        ;; Ensure ID is in payload for creation if needed
        payload (assoc clean-data :id id)]

    (if (:new? data)
      (-> ((:create service) payload)
          (.then (fn [] (reload-fn) (close-modal!)))
          (.catch (fn [e] (js/console.error "Save failed" e) (js/alert (str "Save failed: " e)))))
      (-> ((:update service) id payload)
          (.then (fn [] (reload-fn) (close-modal!)))
          (.catch (fn [e] (js/console.error "Update failed" e) (js/alert (str "Update failed: " e))))))))

(defn- delete-entity! [service reload-fn id]
  (when (js/confirm (str "Are you sure you want to delete " id "?"))
    (-> ((:delete service) id)
        (.then (fn [] (reload-fn)))
        (.catch (fn [e] (js/console.error "Delete failed" e))))))

(defn- toggle-selection [set-val item]
  (if (contains? set-val item)
    (disj set-val item)
    (conj (or set-val #{}) item)))

(defn- get-id-parts [id]
  (if (keyword? id)
    (let [s (subs (str id) 1) ;; remove :
          parts (str/split s #"/")]
      (if (= 2 (count parts))
        {:id-prefix (str (first parts) "/") :id-suffix (second parts)}
        {:id-prefix "" :id-suffix s}))
    {:id-prefix "" :id-suffix (str id)}))

;; --- Icons ---

(defn icon-users []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"}]])

(defn icon-groups []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"}]])

(defn icon-roles []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 14l9-5-9-5-9 5 9 5z"}]
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 14l6.16-3.422a12.083 12.083 0 01.665 6.479A11.952 11.952 0 0012 20.055a11.952 11.952 0 00-6.824-2.998 12.078 12.078 0 01.665-6.479L12 14z"}]])

(defn icon-permissions []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-5 w-5" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"}]])

(defn tab-label [text icon]
  [:div {:class "flex items-center gap-2 px-2"}
   icon
   [:span text]])

;; --- Components ---

(defn loading-spinner []
  [:div {:class "flex flex-col justify-center items-center py-12 space-y-4"}
   [:div {:class "animate-spin rounded-full h-12 w-12 border-4 border-gray-200 border-t-blue-600"}]
   [:div {:class "text-gray-400 text-sm font-medium"} "Loading authorization data..."]])

(defn- modal-wrapper [title on-save body]
  [:div {:class "fixed inset-0 z-50 flex items-center justify-center overflow-x-hidden overflow-y-auto outline-none focus:outline-none"}
   [:div {:class "fixed inset-0 bg-black opacity-50" :on-click close-modal!}]
   [:div {:class "relative w-full max-w-2xl mx-auto my-6 z-50"}
    [:div {:class "relative flex flex-col w-full bg-white border-0 rounded-lg shadow-lg outline-none focus:outline-none"}
     ;; Header
     [:div {:class "flex items-start justify-between p-5 border-b border-solid border-gray-200 rounded-t"}
      [:h3 {:class "text-xl font-semibold text-gray-800"} title]
      [:button {:class "p-1 ml-auto bg-transparent border-0 text-black opacity-50 float-right text-3xl leading-none font-semibold outline-none focus:outline-none hover:opacity-100"
                :on-click close-modal!}
       [:span "×"]]]
     ;; Body
     [:div {:class "relative p-6 flex-auto max-h-[70vh] overflow-y-auto"}
      body]
     ;; Footer
     [:div {:class "flex items-center justify-end p-6 border-t border-solid border-gray-200 rounded-b gap-2"}
      [:button {:class "text-gray-500 background-transparent font-bold uppercase px-6 py-2 text-sm outline-none focus:outline-none ease-linear transition-all duration-150 hover:text-gray-800"
                :type "button"
                :on-click close-modal!}
       "Cancel"]
      [:button {:class "bg-blue-600 text-white active:bg-blue-700 font-bold uppercase text-sm px-6 py-3 rounded shadow hover:shadow-lg outline-none focus:outline-none ease-linear transition-all duration-150"
                :type "button"
                :on-click on-save}
       "Save"]]]]])

(defn- multi-select [label options selected-set on-toggle]
  [:div {:class "mb-4"}
   [:label {:class "block text-gray-700 text-sm font-bold mb-2"} label]
   [:div {:class "border rounded max-h-40 overflow-y-auto p-2 bg-gray-50"}
    (if (empty? options)
      [:div {:class "text-gray-400 italic text-sm"} "No options available"]
      (for [opt options]
        (let [id (:id opt)]
          ^{:key id}
          [:div {:class "flex items-center mb-1"}
           [:input {:type "checkbox"
                    :class "mr-2"
                    :checked (contains? selected-set id)
                    :on-change #(on-toggle id)}]
           [:span {:class "text-sm"} (or (:name opt) (:description opt) (str id))]])))]])

(defn- prefixed-input [label prefix value on-change disabled?]
  [:div {:class "mb-4"}
   [:label {:class "block text-gray-700 text-sm font-bold mb-2"} label]
   [:div {:class "flex"}
    [:span {:class "inline-flex items-center px-3 rounded-l-md border border-r-0 border-gray-300 bg-gray-50 text-gray-500 text-sm"}
     prefix]
    [:input {:class (str "shadow appearance-none border rounded-r-md w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline "
                         (if disabled? "bg-gray-100" ""))
             :value value
             :disabled disabled?
             :on-change on-change}]]])

;; --- Forms ---

(defn- user-form [data-state]
  (let [groups (:groups @data-state)
        roles (:roles @data-state)
        permissions (:permissions @data-state)
        new? (:new? @form-state)]
    [:form
     [:div {:class "mb-4"}
      [:label {:class "block text-gray-700 text-sm font-bold mb-2"} "User ID"]
      [:input {:class (str "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline "
                           (if-not new? "bg-gray-100" ""))
               :value (:id @form-state)
               :disabled (not new?)
               :on-change #(swap! form-state assoc :id (-> % .-target .-value))}]]
     [:div {:class "mb-4"}
      [:label {:class "block text-gray-700 text-sm font-bold mb-2"} "Name"]
      [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
               :value (:name @form-state)
               :on-change #(swap! form-state assoc :name (-> % .-target .-value))}]]

     [multi-select "Groups" groups (set (:groups @form-state))
      #(swap! form-state update :groups toggle-selection %)]

     [multi-select "Direct Roles" roles (set (:roles @form-state))
      #(swap! form-state update :roles toggle-selection %)]

     [multi-select "Direct Permissions" permissions (set (:permissions @form-state))
      #(swap! form-state update :permissions toggle-selection %)]]))

(defn- group-form [data-state]
  (let [roles (:roles @data-state)
        permissions (:permissions @data-state)
        new? (:new? @form-state)]
    [:form
     [prefixed-input "Group ID" (:id-prefix @form-state) (:id-suffix @form-state)
      #(swap! form-state assoc :id-suffix (-> % .-target .-value))
      (not new?)]

     [:div {:class "mb-4"}
      [:label {:class "block text-gray-700 text-sm font-bold mb-2"} "Description"]
      [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
               :value (:description @form-state)
               :on-change #(swap! form-state assoc :description (-> % .-target .-value))}]]

     [multi-select "Roles" roles (set (:roles @form-state))
      #(swap! form-state update :roles toggle-selection %)]

     [multi-select "Permissions" permissions (set (:permissions @form-state))
      #(swap! form-state update :permissions toggle-selection %)]]))

(defn- role-form [data-state]
  (let [permissions (:permissions @data-state)
        new? (:new? @form-state)]
    [:form
     [prefixed-input "Role ID" (:id-prefix @form-state) (:id-suffix @form-state)
      #(swap! form-state assoc :id-suffix (-> % .-target .-value))
      (not new?)]

     [:div {:class "mb-4"}
      [:label {:class "block text-gray-700 text-sm font-bold mb-2"} "Description"]
      [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
               :value (:description @form-state)
               :on-change #(swap! form-state assoc :description (-> % .-target .-value))}]]

     [multi-select "Permissions" permissions (set (:permissions @form-state))
      #(swap! form-state update :permissions toggle-selection %)]]))

(defn- permission-form []
  (let [new? (:new? @form-state)]
    [:form
     [prefixed-input "Permission ID" (:id-prefix @form-state) (:id-suffix @form-state)
      #(swap! form-state assoc :id-suffix (-> % .-target .-value))
      (not new?)]

     [:div {:class "mb-4"}
      [:label {:class "block text-gray-700 text-sm font-bold mb-2"} "Description"]
      [:input {:class "shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
               :value (:description @form-state)
               :on-change #(swap! form-state assoc :description (-> % .-target .-value))}]]]))

;; --- Debug Tool ---

(defn permission-debugger [auth-state current-user-id get-user-permissions]
  ;; Note: This debugger currently relies on auth-state having :users, which might not be true
  ;; if auth-state is just the session state. This needs a separate fix to fetch data.
  [:div "Debugger temporarily unavailable (needs data access update)"])

;; --- Main Page ---

(defn auth-page [can? user-service group-service role-service permission-service list-page data-table icon-add tabs]
  (r/with-let [data-state (r/atom {:users [] :groups [] :roles [] :permissions [] :loading? true})
               load-data! (fn []
                            (swap! data-state assoc :loading? true)
                            (js/console.log "Loading Admin Auth Data...")
                            (-> (js/Promise.all
                                 (clj->js
                                  [(-> ((:list user-service)) (.then #(map normalize-entity %)))
                                   (-> ((:list group-service)) (.then #(map normalize-entity %)))
                                   (-> ((:list role-service)) (.then #(map normalize-entity %)))
                                   (-> ((:list permission-service)) (.then #(map normalize-entity %)))]))
                                (.then (fn [results]
                                         (let [[users groups roles permissions] (js->clj results)]
                                           (swap! data-state assoc
                                                  :users users
                                                  :groups groups
                                                  :roles roles
                                                  :permissions permissions
                                                  :loading? false))))
                                (.catch (fn [e]
                                          (js/console.error "Error loading data" e)
                                          (swap! data-state assoc :loading? false)))))]
    
    ;; Initial Load
    (load-data!)

    (fn []
      (let [active-tab (:active-tab @ui-state)
            modal (:modal @ui-state)
            state @data-state
            loading? (:loading? state)]
        [:div
         (if (and can? (not (can? :perm/admin)))
           [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
            [:div {:class "bg-red-50 border-l-4 border-red-400 p-4"}
             [:div {:class "flex"}
              [:div {:class "ml-3"}
               [:p {:class "text-sm text-red-700"}
                "Access Denied. You do not have permission to view this page."]]]]]
           [:div
            [list-page
             "Authorization Management"
             "Manage users, groups, roles and permissions."
             [{:label (str "Add " (case active-tab
                                    :users "User"
                                    :groups "Group"
                                    :roles "Role"
                                    :permissions "Permission"))
               :icon icon-add
               :primary? true
               :disabled loading?
               :on-click (case active-tab
                           :users #(open-modal! :edit-user {:id "" :name "" :groups #{} :roles #{} :permissions #{} :new? true})
                           :groups #(open-modal! :edit-group {:id-prefix "group/" :id-suffix "" :description "" :roles #{} :permissions #{} :new? true})
                           :roles #(open-modal! :edit-role {:id-prefix "role/" :id-suffix "" :description "" :roles #{} :permissions #{} :new? true})
                           :permissions #(open-modal! :edit-perm {:id-prefix "perm/" :id-suffix "" :description "" :new? true}))}]

             [:div {:class "px-4 sm:px-0"}
              [tabs
               [{:id :users :label [tab-label "Users" [icon-users]]}
                {:id :groups :label [tab-label "Groups" [icon-groups]]}
                {:id :roles :label [tab-label "Roles" [icon-roles]]}
                {:id :permissions :label [tab-label "Permissions" [icon-permissions]]}]
               active-tab
               #(swap! ui-state assoc :active-tab %)]

              ;; Content
              (if loading?
                [loading-spinner]
                (case active-tab
                  :users
                  [data-table
                   [{:label "Name" :key :name}
                    {:label "ID" :render (fn [row] (str (:id row)))}]
                   (:users state)
                   (fn [row] (open-modal! :edit-user (assoc row :new? false)))]

                  :groups
                  [data-table
                   [{:label "ID" :render (fn [row] (str (:id row)))}
                    {:label "Description" :key :description}]
                   (:groups state)
                   (fn [row] (open-modal! :edit-group (merge row (get-id-parts (:id row)) {:new? false})))]

                  :roles
                  [data-table
                   [{:label "ID" :render (fn [row] (str (:id row)))}
                    {:label "Description" :key :description}]
                   (:roles state)
                   (fn [row] (open-modal! :edit-role (merge row (get-id-parts (:id row)) {:new? false})))]

                  :permissions
                  [data-table
                   [{:label "ID" :render (fn [row] (str (:id row)))}
                    {:label "Description" :key :description}]
                   (:permissions state)
                   (fn [row] (open-modal! :edit-perm (merge row (get-id-parts (:id row)) {:new? false})))]
                  ))]]

           ;; Modals
           (when modal
             (case modal
               :edit-user
               [modal-wrapper (if (:new? @form-state) "Add User" "Edit User")
                #(save-entity! user-service load-data! @form-state)
                [user-form data-state]]

               :edit-group
               [modal-wrapper (if (:new? @form-state) "Add Group" "Edit Group")
                #(save-entity! group-service load-data! @form-state)
                [group-form data-state]]

               :edit-role
               [modal-wrapper (if (:new? @form-state) "Add Role" "Edit Role")
                #(save-entity! role-service load-data! @form-state)
                [role-form data-state]]

               :edit-perm
               [modal-wrapper (if (:new? @form-state) "Add Permission" "Edit Permission")
                #(save-entity! permission-service load-data! @form-state)
                [permission-form]])) ])]))))

(defn icon-lock []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"}]])
