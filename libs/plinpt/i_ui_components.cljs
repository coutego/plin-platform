(ns plinpt.i-ui-components
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for UI components, defining standard icons, layout components, and data display elements."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-ui-components
                      :description "Interface for UI Components."
                      :responsibilities "Defines standard UI component beans."
                      :type :infrastructure}]}

    :beans
    {::icon-add
     ^{:doc "Standard 'Add' icon component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]

     ::icon-edit
     ^{:doc "Standard 'Edit' icon component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]

     ::icon-delete
     ^{:doc "Standard 'Delete' icon component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]

     ::icon-back
     ^{:doc "Standard 'Back' icon component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]

     ::page-header
     ^{:doc "Standard page header component with title, subtitle, and actions."
       :reagent-component true
       :api {:args [["title" "User Profile" :string]
                    ["subtitle" "Manage your personal information and settings." :string]
                    ["actions" [{:label "Edit" :primary? true}] :vector]]
             :ret :hiccup}}
     [:= nil]

     ::list-page
     ^{:doc "Wrapper component for list views (master pages)."
       :reagent-component true
       :api {:args [["title" "Users" :string]
                    ["subtitle" "System users registry" :string]
                    ["actions" [{:label "Add User" :primary? true}] :vector]
                    ["content" [:div "List Content"] :component]]
             :ret :hiccup}}
     [:= nil]

     ::detail-page
     ^{:doc "Wrapper component for detail views."
       :reagent-component true
       :api {:args [["title" "User Details" :string]
                    ["subtitle" "Detailed view of user profile" :string]
                    ["actions" [{:label "Delete" :primary? false}] :vector]
                    ["content" [:div "Detail Content"] :component]]
             :ret :hiccup}}
     [:= nil]

     ::tabs
     ^{:doc "Standard tab navigation component."
       :reagent-component true
       :api {:args [["tabs" [{:id :users :label "Users"}
                             {:id :groups :label "Groups"}
                             {:id :roles :label "Roles"}] :vector]
                    ["active-id" :users :keyword]
                    ["on-change" nil :fn]]
             :ret :hiccup}}
     [:= nil]

     ::data-table
     ^{:doc "Standard data table component."
       :reagent-component true
       :api {:args [["columns" [{:label "Name" :key :name} {:label "Role" :key :role}] :vector]
                    ["data" [{:name "John Doe" :role "Admin"} {:name "Jane Smith" :role "User"}] :vector]
                    ["on-row-click" nil :fn]]
             :ret :hiccup}}
     [:= nil]

     ::description-list
     ^{:doc "Standard key-value display component for detail views."
       :reagent-component true
       :api {:args [["items" [{:label "Username" :value "jdoe"}
                             {:label "Email" :value "john@example.com"}
                             {:label "Status" :value "Active"}] :vector]]
             :ret :hiccup}}
     [:= nil]

     ::sidebar-page
     ^{:doc "Responsive sidebar layout component."
       :reagent-component true
       :api {:args [["title" "Admin Panel" :string]
                    ["icon" nil :component]
                    ["items" [{:id :dashboard :label "Dashboard"} {:id :settings :label "Settings"}] :vector]
                    ["active-item" {:id :dashboard} :map]
                    ["on-select" nil :fn]
                    ["render-content" nil :fn]]
             :ret :hiccup}}
     [:= nil]}}))
