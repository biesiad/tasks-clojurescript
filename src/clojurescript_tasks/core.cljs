(ns clojurescript-tasks.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [ajax.core :refer [GET POST json-request-format]]))

(def tasks-url "https://cfassignment.herokuapp.com/greg/tasks")

(defn new-id [ids] (+ 1 (if (empty? ids) 0 (apply max ids))))

(rf/reg-event-db
 :delete-task
 (fn [db [_ id]]
   (assoc db :tasks (into [] (filter #(not= id (:id %)) (:tasks db))))))

(rf/reg-event-db
 :close-alert
 (fn [db [_ id]]
   (assoc db :alerts (into [] (filter #(not= id (:id %))) (:alerts db)))))

(rf/reg-event-db
 :add-task
 (fn [db _]
   (assoc
    (assoc db
           :tasks
           (conj (:tasks db) {:id (new-id (map :id (:tasks db)))
                              :title ""}))
    :loading? false)))

(rf/reg-event-db
 :update-task
 (fn [db [_ id value]]
   (assoc db
          :tasks
          (map (fn [task]
                 (if (= (:id task) id)
                   (assoc task :title value)
                   task))
               (:tasks db)))))

(rf/reg-event-db
 :save-success
 (fn [db _] (assoc db :saving? false)))

(rf/reg-event-db
 :save-tasks
 (fn [db _]
   (POST
     tasks-url
     {:params {:tasks (:tasks db)}
      :format (json-request-format)
      :handler #(rf/dispatch [:save-success %])
      :error-handler #(rf/dispatch [:save-error %])
      :response-format :json
      :keywords? true})
   (assoc db :saving? true)))

(rf/reg-event-db
 :save-success
 (fn [db _] (assoc db
                   :saving? false
                   :alerts (conj (:alerts db) {:id (new-id (map :id (:alerts db)))
                                               :message "Tasks saved!"
                                               :type "success"}))))

(rf/reg-event-db
 :save-error
 (fn [db _]
   (assoc db
          :saving? false
          :alerts (conj (:alerts db) {:id (new-id (map :id (:alerts db)))
                                      :message "Oooops something went wrong..."
                                      :type "error"}))))

(rf/reg-event-db
 :load-tasks
 (fn [db _]
   (GET
     tasks-url
     {:handler #(rf/dispatch [:load-success %])
      :error-handler #(rf/dispatch [:load-error %])
      :response-format :json
      :keywords? true})
   (assoc db :loading? true)))

(rf/reg-event-db
 :load-success
 (fn [db [_ tasks]]
   (assoc db
          :loading? false
          :tasks (:tasks tasks))))

(rf/reg-event-db
 :load-error
 (fn [db _]
   (assoc db
          :loading? false
          :alerts (conj (:alerts db) {:id (new-id (map :id (:alerts db)))
                                      :message "Can't load tasks..."
                                      :type "error"}))))

(rf/reg-event-db
 :initialize
 (fn [_ _]
   {:tasks []
    :alerts []
    :saving? false
    :loading? false}))

(rf/dispatch-sync [:initialize])


(rf/reg-sub :tasks (fn [db v] (:tasks db)))

(rf/reg-sub :alerts (fn [db v] (:alerts db)))

(rf/reg-sub :loading? (fn [db v] (:loading? db)))

(rf/reg-sub :saving? (fn [db v] (:saving? db)))

(defn task-item [task]
  [:div.task {:class "task" :key (:id task)}
   [:input {:key (:id task)
            :class "task-input"
            :type "title"
            :value (:title task)
            :placeholder "Add title..."
            :autoFocus true
            :on-input #(rf/dispatch [:update-task (:id task) (.-value (.-target %))])}]
   [:i.fa.fa-trash-alt.task-delete {:on-click #(rf/dispatch [:delete-task (:id task)])}]])

(defn alert-item [alert]
  [:div {:class (str "alert alert-" (:type alert)) :key (:id alert)}
   (:message alert)
   (:id alert)
   [:i.fa.fa-times.alert-close {:on-click #(rf/dispatch [:close-alert (:id alert)])}]])

(defn app []
  (let [loading? @(rf/subscribe [:loading?])
        saving? @(rf/subscribe [:saving?])]
    [:div
     [:div.header]
     [:div.container
      [:div.subheader
       [:h1 "tasks"]
       [:div.subheader-buttons
        [:button.button
         {:on-click #(rf/dispatch [:add-task])
          :disabled (or loading? saving?)}
         "Add task"]
        [:button.button.button-primary
         {:on-click #(rf/dispatch [:save-tasks])
          :disabled (or loading? saving?)}
         "Save"]]]
      (if loading?
        [:i.fa.fa-circle-notch.fa-spin.fa-3x.fa-fw.loader]
        (map task-item @(rf/subscribe [:tasks])))
      [:div.alerts (map alert-item @(rf/subscribe [:alerts]))]]]))


(rf/dispatch [:load-tasks])

(r/render [app] (.getElementById js/document "app"))
