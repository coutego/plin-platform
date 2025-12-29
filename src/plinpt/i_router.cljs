(ns plinpt.i-router
  "Interface plugin for client-side routing.
   
   Provides reactive routing state and navigation functions that can be
   consumed by any shell implementation.
   
   Routes are provided by i-application which extracts them from nav-items."
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface for client-side routing. Provides current route state and navigation functions."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-router
                      :description "Interface for client-side routing."
                      :responsibilities "Defines beans for route state, navigation, and route matching."
                      :type :infrastructure}]}
    
    :beans
    {::all-routes
     ^{:doc "All routes - provided by i-application."
       :api {:ret :vector}}
     [:= []]
     
     ::current-route
     ^{:doc "Reactive atom containing the current route.
             Value shape: {:path '/foo' :component <fn> :params {} :required-perm <kw>}
             Returns nil if no route matches."
       :api {:ret :atom}}
     [:= nil]
     
     ::navigate!
     ^{:doc "Function to navigate to a path. Updates the browser hash and current-route.
             Args: [path] - string path like '/users/123'"
       :api {:args [["path" "/home" :string]] :ret :nil}}
     [:= (fn [_path] nil)]
     
     ::match-route
     ^{:doc "Function to match a path against registered routes.
             Args: [path] - string path to match
             Returns: route map or nil"
       :api {:args [["path" "/home" :string]] :ret :map}}
     [:= (fn [_path] nil)]
     
     ::setup!
     ^{:doc "Function to initialize the router. Called once at startup.
             Args: [homepage-path] - default path to redirect to from root"
       :api {:args [["homepage-path" "/" :string]] :ret :nil}}
     [:= (fn [_homepage] nil)]
     
     ::initialized?
     ^{:doc "Atom indicating whether the router has been initialized."
       :api {:ret :atom}}
     [:= (atom false)]}}))
