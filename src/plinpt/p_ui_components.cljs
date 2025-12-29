(ns plinpt.p-ui-components
  (:require [plin.core :as plin]
            [plinpt.i-ui-components :as iui]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-ui-components.core :as core]))

;; --- Plugin Definition ---

(def plugin
  (plin/plugin 
   {:doc "Implementation plugin providing concrete UI components for icons, headers, lists, tables, and sidebar layouts."
    :deps [iui/plugin idev/plugin]
   
   :contributions
   {::idev/plugins [{:id :ui-components
                     :description "Implementation of standard UI components."
                     :responsibilities "Provides Reagent components for icons, layout, and data display."
                     :type :infrastructure}]}

   :beans
   {::error-boundary
     ^{:doc "React error boundary component, to catch errors during rendering."
       :reagent-component true
       :api {:args [:hiccup] :ret :hiccup}}
     [partial core/error-boundary]

    ::iui/icon-add
    ^{:doc "Standard 'Add' icon component implementation."
      :reagent-component true
      :api {:args [] :ret :hiccup}}
    [:= core/icon-add]

    ::iui/icon-edit
    ^{:doc "Standard 'Edit' icon component implementation."
      :reagent-component true
      :api {:args [] :ret :hiccup}}
    [:= core/icon-edit]

    ::iui/icon-delete
    ^{:doc "Standard 'Delete' icon component implementation."
      :reagent-component true
      :api {:args [] :ret :hiccup}}
    [:= core/icon-delete]

    ::iui/icon-back
    ^{:doc "Standard 'Back' icon component implementation."
      :reagent-component true
      :api {:args [] :ret :hiccup}}
    [:= core/icon-back]
    
    ::iui/page-header
    ^{:doc "Standard page header component implementation."
      :reagent-component true
      :api {:args [["title" "Page Title" :string] 
                   ["subtitle" "This is a subtitle" :string] 
                   ["actions" [{:label "Primary Action" :primary? true}] :vector]] 
            :ret :hiccup}}
    [:= core/page-header]

    ::iui/list-page
    ^{:doc "Wrapper component for list views implementation."
      :reagent-component true
      :api {:args [["title" "List View" :string] 
                   ["subtitle" "Manage your items here" :string] 
                   ["actions" [{:label "Create New" :primary? true}] :vector] 
                   ["content" [:div "The list content goes here"] :component]] 
            :ret :hiccup}}
    [:= core/list-page]

    ::iui/detail-page
    ^{:doc "Wrapper component for detail views implementation."
      :reagent-component true
      :api {:args [["title" "Item Details" :string] 
                   ["subtitle" "Viewing specific item information" :string] 
                   ["actions" [{:label "Back" :primary? false}] :vector] 
                   ["content" [:div "The detail content goes here"] :component]] 
            :ret :hiccup}}
    [:= core/detail-page]

    ::iui/tabs
    [:= core/tabs]

    ::iui/data-table
    ^{:doc "Standard data table component implementation."
      :reagent-component true
      :api {:args [["columns" [{:label "ID" :key :id} {:label "Name" :key :name}] :vector] 
                   ["data" [{:id 1 :name "Example Item 1"} {:id 2 :name "Example Item 2"}] :vector] 
                   ["on-row-click" nil :fn]] 
            :ret :hiccup}}
    [:= core/data-table]

    ::iui/description-list
    ^{:doc "Standard key-value display component implementation."
      :reagent-component true
      :api {:args [["items" [{:label "Field A" :value "Value A"}
                             {:label "Field B" :value "Value B"}] :vector]] 
            :ret :hiccup}}
    [:= core/description-list]

    ::iui/sidebar-page
    ^{:doc "Responsive sidebar layout component implementation."
      :reagent-component true
      :api {:args [["title" "App Sidebar" :string] 
                   ["icon" nil :component] 
                   ["items" [{:id :home :label "Home"} {:id :profile :label "Profile"}] :vector] 
                   ["active-item" {:id :home} :map] 
                   ["on-select" nil :fn] 
                   ["render-content" '(fn [item] [:div "Content for " (:label item)]) :fn]] 
            :ret :hiccup}}
    [:= core/sidebar-page]}}))
