(ns difficulty-api.integration-test
  (:require  [clojure.test :refer [deftest is use-fixtures]]
             [com.stuartsierra.component :as component]
             [cheshire.core :as json]
             [clojure.pprint :refer [pprint]]
             [difficulty-api.dispatch :as dispatch]
             [difficulty-api.db :as db]
             [difficulty-api.torn-api :as api]
             [difficulty-api.torn-api-test :refer [test-client
                                                   basic-info-test-data
                                                   battle-stats-test-data]]
             [difficulty-api.system :refer [test-system]]))

(defn mock-request [{:keys [uri request-method] :as req-map}]
  (merge req-map
         {:server-port 3000
          :server-name "127.0.0.1"
          :remote-addr "127.0.0.1"
          :scheme :http
          :headers {}}))

(def decode-response-body (comp json/decode slurp :body))

(def valid-api-key-http-client
  (reify api/HttpClient
    (http-get [this url]
      (cond
        (= "http://api.torn.com/user/?selections=basic&key=foo" url)
        {:body (json/encode basic-info-test-data)}

        (= "http://api.torn.com/user/?selections=battlestats&key=foo" url)
        {:body (json/encode battle-stats-test-data)}))))

(deftest adds-valid-api-key
  (let [system (component/start-system (test-system {:http-client valid-api-key-http-client}))]
    (try
      (is (= {"result" true}
             (decode-response-body
              ((get-in system [:app :app]) (mock-request {:uri "/api/apikey"
                                                          :query-string "api-key=foo"
                                                          :request-method :post})))))
      (finally component/stop system))))

(deftest adds-valid-player
  (let [system (component/start-system (test-system {}))]
    (try
      (is (= {:player/torn-id (get basic-info-test-data "player_id")
              :player/battle-stats (api/total-battle-stats battle-stats-test-data)
              :player/api-key "foo"}
             (do (dispatch/add-api-key valid-api-key-http-client (:db system) "foo")
                 (Thread/sleep 100)
                 (db/player-by-api-key (:db system) "foo"))))
      (finally (component/stop system)))))

(deftest doesnt-add-invalid-api-key
  (let [system (component/start-system
                (test-system {:http-client (test-client {:error {:code 2}})}))]
    (try
      (is (= {:error {:msg "Invalid API key" :api-key "bar"}}
             (-> ((get-in system [:app :app]) (mock-request {:uri "/api/apikey"
                                                             :query-string "api-key=bar"
                                                             :request-method :post}))
                 :body
                 (json/decode true))))
      (finally (component/stop system)))))
