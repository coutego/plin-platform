(ns plinpt.p-app-shell.doc)

(def functional
  "# App Shell: Functional Overview

The **App Shell** is the foundational layer of the application. It serves as the main container that hosts all other features and ensures a consistent user experience across the platform.

### Key Responsibilities

1.  **Global Layout**:
    *   **Header**: Displays the top navigation bar, which is consistent across all pages.
    *   **Main Content Area**: The central region where the current page (e.g., Home, Admin, Reports) is displayed.
    *   **Overlays**: Manages global elements that float above the content, such as modal dialogs, notifications, or loading spinners.

2.  **Navigation & Routing**:
    *   The App Shell monitors the browser's URL (specifically the `#hash` portion) to determine which page to show.
    *   It handles navigation between different sections of the application without reloading the page (Single Page Application behavior).

3.  **Error Handling**:
    *   It wraps the entire application in a global **Error Boundary**.
    *   If a technical error occurs in any part of the application, the App Shell catches it and displays a user-friendly error message instead of letting the application crash or show a blank screen.
    *   It also handles **404 Not Found** errors (when a URL doesn't exist) and **403 Access Denied** errors (when a user tries to access a restricted page without permission).

### User Interaction
As a user, you interact with the App Shell constantly, though often implicitly. It is responsible for:
*   Showing you the correct page when you click a link.
*   Redirecting you to the Home page if you try to access a restricted area while logged out.
*   Displaying the \"Page Not Found\" screen if you enter an invalid URL.")

(def technical
  "# App Shell: Technical Implementation

The `p-app-shell` plugin implements the `i-app-shell` interface. It provides the root React component and the logic for mounting the application to the DOM.

### Core Components

#### 1. App Layout (`app-layout`)
This is the root component of the application. It is implemented as a **Form-2 Reagent component** to set up the routing event listeners during the component's initialization phase.

*   **Routing Listener**: It attaches a listener to the `window.onhashchange` event.
*   **Render Loop**:
    *   **Header**: Iterates through `::iapp/header-components` injected by other plugins and renders them inside an Error Boundary.
    *   **Content**: Determines the active route based on the current hash.
        *   Checks permissions (`:required-perm`).
        *   Handles redirects (Home if not logged in, 403 if logged in but unauthorized).
        *   Renders the matched component inside an Error Boundary.
    *   **Overlays**: Iterates through `::iapp/overlay-components` and renders them.

~~~clojure
(defn app-layout [header routes overlays can? user-atom]
  ;; Form-2 Component: Setup phase
  (let [hash-handler #(handle-hash-change routes)]
    (set! (.-onhashchange js/window) hash-handler)
    (handle-hash-change routes)
    
    ;; Return the render function
    (fn [& _]
      (let [active-route @current-route
            Page (:component active-route)
            ;; ...
            ]
        [:div {:class \"min-h-screen ...\"}
         ;; Global Header Region
         [:header
          (doall
           (for [[idx comp] (map-indexed vector header)]
             ^{:key idx} [error-boundary [comp]]))]

         ;; Main Content Region
         (let [content (cond
                         (not Page) [not-found-view]
                         ;; ... permission checks ...
                         :else [Page])]
           [:main
            [error-boundary content]])
         
         ;; Global Overlays Region
         (doall
          (for [[idx comp] (map-indexed vector overlays)]
            ^{:key idx} [error-boundary [comp]]))]))))
~~~

#### 2. Routing System
The routing is a simple client-side hash router.
*   **Route Definitions**: Plugins contribute routes via the `::iapp/routes` extension point.
*   **Matching Logic**:
    *   Supports exact matches (e.g., `/admin`).
    *   Supports path parameters (e.g., `/users/:id`).
*   **State**: The current route is stored in a Reagent atom `current-route`, triggering a re-render whenever the hash changes.

~~~clojure
(defn- match-route [route-defs path]
  (or (some (fn [def]
              (let [def-path (:path def)]
                (if (str/includes? def-path \":\")
                  ;; Handle routes with parameters (e.g. /history/:id)
                  (let [path-parts (str/split path #\"/\")
                        def-parts (str/split def-path #\"/\")]
                    (when (= (count path-parts) (count def-parts))
                      (when (every? (fn [[p d]] 
                                      (or (= p d) 
                                          (str/starts-with? d \":\")))
                                    (map vector path-parts def-parts))
                        def)))
                  ;; Handle exact matches
                  (when (= def-path path)
                    def))))
            route-defs)
      nil))
~~~

#### 3. Error Boundaries
The plugin provides a custom `error-boundary` Higher-Order Component (HOC) created with `reagent.core/create-class`.
*   It implements the `component-did-catch` lifecycle method.
*   It captures JavaScript errors in its child tree.
*   It displays a fallback UI (Compact or Block style) and logs the error to the console.
*   It prevents the entire application from unmounting due to a local component failure.

~~~clojure
(r/create-class
  {:display-name \"ErrorBoundary\"

   :component-did-catch
   (fn [this error info]
     (js/console.error \"Error Boundary caught:\" error)
     (swap! state assoc :has-error true :error error))

   :reagent-render
   (fn [& args]
     (let [{:keys [has-error error show-details]} @state]
       (if has-error
         [:<>
          [block-error-ui state error]
          (when show-details
            [error-modal error ...])]

         ;; Success State
         (into [:<>] children))))})
~~~

### Extension Points Consumed

The App Shell aggregates contributions from other plugins to build the UI:

*   `::iapp/routes`: A collection of route maps defining the application's navigation structure.
*   `::iapp/header-components`: Components to be rendered in the top header (e.g., Navigation Bar, User Widget).
*   `::iapp/overlay-components`: Components to be rendered as global overlays (e.g., Login Modal).

### Beans Provided

*   `::iapp/ui`: The main `app-layout` component, ready to be mounted.
*   `::mount`: A function that mounts `::iapp/ui` to the DOM element with ID `app`.")
