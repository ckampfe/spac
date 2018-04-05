(ns spac.core
  (:require [reagent.core :as reagent :refer [atom]]
            [replumb.core :as replumb]
            [re-frame.core :as rf]
            [cljs.spec.alpha :as s]
            [cljs.pprint :as pprint]
            [clojure.core.async :refer [<! go timeout]])
  (:import [goog.net XhrIo]))

(enable-console-print!)

(println "This text is printed from src/spac/core.cljs. Go ahead and edit it and see reloading in action.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fetch-file!
  "Very simple implementation of XMLHttpRequests that given a file path
  calls src-cb with the string fetched of nil in case of error.
  See doc at https://developers.google.com/closure/library/docs/xhrio"
  [file-url src-cb]
  (try
    (.send XhrIo file-url
           (fn [e]
             (if (.isSuccess (.-target e))
               (src-cb (.. e -target getResponseText))
               (src-cb nil))))
    (catch :default e
      (src-cb nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare update-spec-result)

(defn read-eval-call [s]
  (println s)
  (replumb/read-eval-call (merge (replumb/options
                                  :browser
                                  ["/src/cljs" "/js/compiled/out"]
                                  fetch-file!)
                                 {:context :statement})
                          #(rf/dispatch [:update-spec-result %]);;println
                          s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spec-eval-fn [sym]
  (list 'fn '[form] (list 's/explain-str sym 'form)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-spec-keyword [s]
  (second (last (cljs.reader/read-string (str "(" s ")")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn on-js-reload []

  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-update-latch [n]
  (go (while true
        (<! (timeout n))
        (rf/dispatch [:reset-spec-update-latch]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-db [db _]
  {:db {:namespace-and-require "(ns hello.core (:require [cljs.core] [cljs.spec.alpha :as s]))"
        :spec-def ""
        :test-data ""
        :spec-result ""
        :should-reconform-spec false}})

(defn update-spec-def [{:keys [db] :as cofx} [_ change]]
  {:db (assoc db
              :spec-def change
              :should-reconform-spec false)})

(defn update-test-data [{:keys [db] :as cofx} [_ change]]
  (let [spec-def (:spec-def db)
        spec-keyword (get-spec-keyword spec-def)
        spec-eval-fn (spec-eval-fn spec-keyword)
        ]

    (println spec-keyword)
    (println spec-eval-fn)

    {:db (-> db
            (assoc :test-data change)
            (#(if (:should-reconform-spec %)
                (do
                  (read-eval-call (str (:namespace-and-require db)
                                       " "
                                       spec-def
                                       " "
                                       (str "(" spec-eval-fn " " change ")")
                                       ))
                  (assoc % :should-reconform-spec false))
                %)))}))

(defn update-spec-result [{:keys [db] :as cofx} [_ change]]
  (println change)
  {:db (assoc db :spec-result (:value change))})

(defn reset-spec-update-latch [{:keys [db] :as cofx} [_ change]]
  {:db (assoc db :should-reconform-spec true)})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-event-fx
 :initialize-db
 initialize-db)

(rf/reg-event-fx
 :update-spec-def
 update-spec-def)

(rf/reg-event-fx
 :update-test-data
 update-test-data)

(rf/reg-event-fx
 :update-spec-result
 update-spec-result)

(rf/reg-event-fx
 :reset-spec-update-latch
 reset-spec-update-latch)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spec-def [db v]
  (:spec-def db))

(defn test-data [db v]
  (:test-data db))

(defn spec-result [db v]
  (:spec-result db))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rf/reg-sub
 :spec-def
 spec-def)

(rf/reg-sub
 :test-data
 test-data)

(rf/reg-sub
 :spec-result
 spec-result)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spec-def-ta []
  [:textarea {:type "text"
              :class "form-control"
              :rows "15"
              :value @(rf/subscribe [:spec-def])
              :on-change #(rf/dispatch [:update-spec-def (-> % .-target .-value)])
              }])

(defn test-data-ta []
  [:textarea {:type "text"
              :class "form-control"
              :rows "15"
              :value @(rf/subscribe [:test-data])
              :on-change #(rf/dispatch [:update-test-data (-> % .-target .-value)])
              }])

(defn result-display-ta []
  (when-let [parse-result @(rf/subscribe [:spec-result])]
    [:div
     [:textarea {:type "text"
                 :class "form-control"
                 :rows "15"
                 :value parse-result
                 :readOnly ""}]]))


(defn calling-component []
  [:div
   [:div
    [spec-def-ta]
    [test-data-ta]
    [result-display-ta]]])

(defn init []
  (reagent/render-component [calling-component]
                            (. js/document (getElementById "app")))
  (rf/dispatch [:initialize-db])
  (start-update-latch 1500))


(init)
