(ns plinpt.p-admin.core)

(defn sort-sections [sections]
  (sort-by :order sections))

(defn admin-page [sections]
  (let [sorted (sort-sections sections)]
    [:div {:class "p-8"}
     [:h1 {:class "text-3xl font-bold text-gray-800 mb-6"} "Administration"]
     (if (empty? sorted)
       [:div {:class "bg-white p-6 rounded-lg shadow"}
        [:p {:class "text-gray-600"} "No admin sections registered."]]
       [:div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"}
        (for [{:keys [id label description href]} sorted]
          ^{:key id}
          [:a {:href (str "#" href)
               :class "block bg-white p-6 rounded-lg shadow hover:shadow-md transition-shadow border border-gray-100"}
           [:h2 {:class "text-xl font-semibold text-gray-800 mb-2"} label]
           [:p {:class "text-gray-600"} description]])])]))

(defn make-route [ui]
  {:path "/admin"
   :component ui})
