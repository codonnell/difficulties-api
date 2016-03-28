(ns difficulty-api.integration-test
  (:require  [clojure.test :refer [deftest is use-fixtures]]
             [com.stuartsierra.component :as component]
             [cheshire.core :as json]
             [clojure.pprint :refer [pprint]]
             [datomic.api :as d]
             [difficulty-api.dispatch :as dispatch]
             [difficulty-api.db :as db]
             [difficulty-api.torn-api :as api]
             [difficulty-api.torn-api-test :refer [test-client
                                                   basic-info-test-data
                                                   battle-stats-test-data]]
             [difficulty-api.system :refer [test-system]]))

(declare ^:dynamic system)

(defn create-and-destroy-system [f]
  (binding [system (test-system {})]
    (try
      (f)
      (finally (component/stop-system system)))))

(use-fixtures :each create-and-destroy-system)

(defn mock-request [{:keys [uri request-method] :as req-map}]
  (merge req-map
         {:server-port 3000
          :server-name "127.0.0.1"
          :remote-addr "127.0.0.1"
          :scheme      :http
          :headers     {}}))

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
  (let [system (component/start-system (assoc-in system [:app :http-client] valid-api-key-http-client))]
    (is (= {"result" true}
           (decode-response-body
            ((get-in system [:app :app]) (mock-request {:uri "/api/apikey"
                                                        :query-string "api-key=foo"
                                                        :request-method :post})))))))

(deftest adds-valid-player
  (let [system (component/start-system system)]
    (is (= {:player/torn-id      (get basic-info-test-data "player_id")
            :player/battle-stats (api/total-battle-stats battle-stats-test-data)
            :player/api-key      "foo"}
           (do (dispatch/add-api-key valid-api-key-http-client (:db system) "foo")
               (Thread/sleep 100)
               (db/player-by-api-key (:db system) "foo"))))))

(deftest doesnt-add-invalid-api-key
  (let [system (component/start-system
                (assoc-in system [:app :http-client] (test-client {:error {:code 2}})))]
    (is (= {:error {:msg "Invalid API key" :api-key "bar"}}
           (-> ((get-in system [:app :app]) (mock-request {:uri "/api/apikey"
                                                           :query-string "api-key=bar"
                                                           :request-method :post}))
               :body
               (json/decode true))))))

(def existing-attack-tx
  [{:db/id (d/tempid :db.part/user)
    :attack/torn-id 1
    :attack/attacker {:player/torn-id 2}
    :attack/defender {:player/torn-id 5}
    :attack/timestamp-started (java.util.Date. (long 3))
    :attack/timestamp-ended (java.util.Date. (long 500))
    :attack/result [:db/ident :attack.result/mug]}])

(def add-test-player-tx
  [{:db/id (d/tempid :db.part/user)
    :player/torn-id 2
    :player/api-key "bar"
    :player/battle-stats 35.2}])

(def attack-resp
  {:attacks {1 {:timestamp_started 3
                :timestamp_ended 500
                :attacker_id 2
                :attacker_name "buzz lightyear"
                :attacker_faction ""
                :defender_id 5
                :defender_name "woody"
                :defender_faction 500
                :result "Mug"
                :respect_gain 0}
             2 {:timestamp_started 100
                :timestamp_ended 50000
                :attacker_id ""
                :attacker_name ""
                :attacker_faction ""
                :defender_id 4
                :defender_name "superman"
                :defender_faction 500
                :result "Lose"
                :respect_gain 3.3}
             3 {:timestamp_started 50001
                :timestamp_ended 60000
                :attacker_id ""
                :attacker_name ""
                :attacker_faction ""
                :defender_id 4
                :defender_name "superman"
                :defender_faction 500
                :result "Lose"
                :respect_gain 3.3}}})

(deftest update-attacks-test
  (let [system (component/start-system
                (assoc-in system [:app :http-client] (test-client attack-resp)))
        attacks (fn [] (d/q '[:find (pull ?attack attack-pull)
                              :in $ attack-pull
                              :where [?attack :attack/torn-id]]
                         (d/db (get-in system [:db :conn])) db/attack-pull))]
    (d/transact (get-in system [:db :conn]) existing-attack-tx)
    (is (= 1 (count (attacks))))
    (is (thrown? RuntimeException
                 (dispatch/update-attacks (get-in system [:app :http-client]) (:db system) "bar")))
    (d/transact (get-in system [:db :conn]) add-test-player-tx)
    (dispatch/update-attacks (get-in system [:app :http-client]) (:db system) "bar")
    (is (= 3 (count (attacks))))))

(def difficulties-test-data
  [{:db/id #db/id [:db.part/user -1]
    :player/api-key "foo"
    :player/torn-id 1
    :player/battle-stats 5.0}
   {:db/id #db/id [:db.part/user -2]
    :player/torn-id 2
    :player/battle-stats 10.0}
   {:db/id #db/id [:db.part/user -3]
    :player/torn-id 3
    :player/battle-stats 20.0}
   {:db/id #db/id [:db.part/user -4]
    :player/torn-id 4}
   {:db/id #db/id [:db.part/user -5]
    :player/torn-id 5}
   {:db/id #db/id [:db.part/user -6]
    :player/torn-id 6}
   {:db/id #db/id [:db.part/user -7]
    :player/torn-id 7}
   {:db/id #db/id [:db.part/user -8]
    :attack/torn-id 1
    :attack/attacker #db/id [:db.part/user -2]
    :attack/defender #db/id [:db.part/user -4]
    :attack/timestamp-started (java.util.Date. (long 1))
    :attack/timestamp-ended (java.util.Date. (long 100))
    :attack/result :attack.result/hospitalize}
   {:db/id #db/id [:db.part/user -9]
    :attack/torn-id 2
    :attack/attacker #db/id [:db.part/user -2]
    :attack/defender #db/id [:db.part/user -5]
    :attack/timestamp-started (java.util.Date. (long 1))
    :attack/timestamp-ended (java.util.Date. (long 100))
    :attack/result :attack.result/lose}
   {:db/id #db/id [:db.part/user -11]
    :attack/torn-id 4
    :attack/attacker #db/id [:db.part/user -1]
    :attack/defender #db/id [:db.part/user -6]
    :attack/timestamp-started (java.util.Date. (long 1))
    :attack/timestamp-ended (java.util.Date. (long 100))
    :attack/result :attack.result/mug}
   {:db/id #db/id [:db.part/user -12]
    :attack/torn-id 5
    :attack/attacker #db/id [:db.part/user -3]
    :attack/defender #db/id [:db.part/user -6]
    :attack/timestamp-started (java.util.Date. (long 1))
    :attack/timestamp-ended (java.util.Date. (long 100))
    :attack/result :attack.result/stalemate}
   {:db/id #db/id [:db.part/user -13]
    :attack/torn-id 6
    :attack/attacker #db/id [:db.part/user -2]
    :attack/defender #db/id [:db.part/user -7]
    :attack/timestamp-started (java.util.Date. (long 1))
    :attack/timestamp-ended (java.util.Date. (long 100))
    :attack/result :attack.result/hospitalize}
   {:db/id #db/id [:db.part/user -14]
    :attack/torn-id 7
    :attack/attacker #db/id [:db.part/user -2]
    :attack/defender #db/id [:db.part/user -7]
    :attack/timestamp-started (java.util.Date. (long 1))
    :attack/timestamp-ended (java.util.Date. (long 100))
    :attack/result :attack.result/timeout}])
