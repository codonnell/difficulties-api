(ns difficulty-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [clojure.string :as string]
            [difficulty-api.dispatch :as dispatch]
            [difficulty-api.torn-api :as api]))

(defn invalid-api-key-handler [^Exception e data request]
  (bad-request (json/encode {:error {:msg "Invalid API key" :api-key (:api-key data)}})))

(defn unknown-api-key-handler [^Exception e data request]
  (not-found (json/encode {:error {:msg "Unknown API key" :api-key (:api-key data)}})))

(defn default-exception-handler [^Exception e data request]
  (log/error (str "Unhandled exception: " (.toString e) (.toString (.getStackTrace e)) data))
  (internal-server-error {:error "Unhandled server error. Please notify the developer."}))

(defn wrap-logging [handler]
  (fn [req]
    (log/info req)
    (handler req)))

(defn wrap-cors [handler]
  (fn [req]
    (let [response (handler req)]
      (assoc-in response [:headers "Access-Control-Allow-Origin"] "*"))))

(defn app [http-client db]
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Difficulty-api"
                    :description "Compojure Api example"}
             :tags [{:name "api", :description "some apis"}]}}
     :exceptions {:handlers {:unknown-api-key unknown-api-key-handler
                             :invalid-api-key invalid-api-key-handler
                             :compojure.api.exception/default default-exception-handler}}}

    (context "/api" []
      :tags ["api"]
      :middleware [wrap-cors]

      (POST "/apikey" []
          :return {:result s/Bool}
          :query-params [api-key :- s/Str]
          :summary "adds api key to database"
          (ok (do (log/info (format "Adding API Key: %s" api-key))
                  {:result (dispatch/add-api-key http-client db api-key)})))

      (POST "/difficulties" []
        :return {:result {s/Int s/Keyword}}
        :query-params [api-key :- s/Str]
        :body [body {:torn-ids [s/Int]}]
        :summary "returns a list of difficulties"
        (ok {:result (do (log/info (format "Getting difficulties for API key %s of IDs %s"
                                           api-key (string/join ", " (:torn-ids body))))
                         (dispatch/update-attacks-if-outdated http-client db api-key)
                         (dispatch/difficulties db api-key (:torn-ids body)))})))))

(defrecord App [http-client db]
  component/Lifecycle

  (start [component]
    (assoc component :app (app http-client db)))
  (stop [component]
    (assoc component :app nil)))

(defn new-app [{:keys [http-client db] :as config}]
  (map->App config))
