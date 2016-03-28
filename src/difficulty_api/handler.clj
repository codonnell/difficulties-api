(ns difficulty-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :as json]
            [difficulty-api.dispatch :as dispatch]
            [difficulty-api.torn-api :as api]))

(defn invalid-api-key-handler [^Exception e data request]
  (bad-request (json/encode {:error {:msg "Invalid API key" :api-key (:api-key data)}})))

(defn unknown-api-key-handler [^Exception e data request]
  (not-found (json/encode {:error {:msg "Unknown API key" :api-key (:api-key data)}})))

(defn wrap-logging [handler]
  (fn [req]
    (pprint req)
    (handler req)))

(defn app [http-client db]
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Difficulty-api"
                    :description "Compojure Api example"}
             :tags [{:name "api", :description "some apis"}]}}
     :exceptions {:handlers {:unknown-api-key unknown-api-key-handler
                             :invalid-api-key invalid-api-key-handler}}}

    (context "/api" []
      :tags ["api"]
      ;; :middleware [wrap-logging]

      (POST "/apikey" []
          :return {:result s/Bool}
          :query-params [api-key :- s/Str]
          :summary "adds api key to database"
          (ok {:result (dispatch/add-api-key http-client db api-key)}))

      (GET "/difficulties" []
        :return {:result {s/Int s/Keyword}}
        :query-params [api-key :- s/Str
                       torn-ids :- [s/Int]]
        :summary "returns a list of difficulties"
        (ok {:result (dispatch/difficulties db api-key torn-ids)})))))

(defrecord App [http-client db]
  component/Lifecycle

  (start [component]
    (assoc component :app (app http-client db)))
  (stop [component]
    (assoc component :app nil)))

(defn new-app [{:keys [http-client db] :as config}]
  (map->App config))
