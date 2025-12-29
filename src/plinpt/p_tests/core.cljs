(ns plinpt.p-tests.core)

(defn test-page [can?]
  [:div {:class "p-6"}
   [:h1 {:class "text-2xl font-bold mb-4"} "Test Page"]
   [:div {:class "text-lg"}
    [:span "Do I have the permission :perm/admin? "]
    (if (can? :perm/admin)
      [:span {:class "font-bold text-green-600"} "Yes"]
      [:span {:class "font-bold text-red-600"} "No"])]])

(defn create-test-page [can?]
  (fn [] [test-page can?]))

(defn make-route [component]
  {:path "/test"
   :component component})

;; --- Buggy Components ---

(defn buggy-component []
  (throw (js/Error. "Simulated Component Error!")))

(defn buggy-icon []
  (throw (js/Error. "Simulated Icon Error!")))
