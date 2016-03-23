(ns difficulty-api.torn-api-test
  (:require [difficulty-api.torn-api :as api]
            [cheshire.core :as json]
            [clojure.walk :refer [keywordize-keys]]
            [medley.core :refer [map-keys]]
            [clojure.test :refer [deftest is]]))

(defn test-client [resp]
  (reify
    api/HttpClient
    (http-get [this url] {:body (json/encode resp)})))

(deftest user-query-url-test
  (is (= "http://api.torn.com/user/?selections=basic&key=foo"
         (api/user-query-url "foo" ["basic"])))
  (is (= "http://api.torn.com/user/?selections=basic,profile&key=foo"
         (api/user-query-url "foo" ["basic" "profile"])))
  (is (= "http://api.torn.com/user/1234?selections=basic&key=foo"
         (api/user-query-url "foo" ["basic"] 1234)))
  (is (thrown? RuntimeException
               (api/user-query-url "" ["basic"])))
  (is (thrown? RuntimeException
               (api/user-query-url "foo" []))))

(def basic-info-test-data {"level" 1 "gender" "Male" "status" "Civilian" "player_id" 1234 "name" "foo"})

(deftest basic-info-test
  (is (= (keywordize-keys basic-info-test-data)
         (api/basic-info (test-client basic-info-test-data) "foo")))
  (is (thrown? RuntimeException
               (api/basic-info (test-client basic-info-test-data ""))))
  (is (thrown? RuntimeException
               (api/basic-info (test-client {:error {:code 2}})))))

(deftest valid-api-key-test
  (is (api/valid-api-key? (test-client basic-info-test-data) "foo"))
  (is (not (api/valid-api-key? (test-client {:error {:code 2}}) "bar")))
  (is (not (api/valid-api-key? (test-client {:error {:code 1}}) ""))))

(def battle-stats-test-data {:strength 1.0 :speed 2.0 :dexterity 3.0 :defense 4.0
                             :strength_modifier -2 :speed_modifier 0
                             :dexterity_modifier -4 :defense_modifier -5
                             :strength_info ["foo" "bar"]
                             :speed_info []
                             :dexterity_info ["test"]
                             :defense_info ["baz"]})

(deftest battle-stats-test
  (is (= (keywordize-keys battle-stats-test-data)
         (api/battle-stats (test-client battle-stats-test-data) "foo")))
  (is (thrown? RuntimeException
               (api/battle-stats (test-client battle-stats-test-data) "")))
  (is (thrown? RuntimeException
               (api/battle-stats (test-client {:error {:code 2}}) ""))))

(deftest attacks-test
  (let [info {"attacks" {"1" {"defender_faction" 1
                              "attacker_faction" 2
                              "defender_name" "foo"
                              "attacker_name" "bar"
                              "defender_id" 3
                              "attacker_id" 4
                              "result" "Hospitalize"
                              "respect_gain" 1.5
                              "timestamp_started" 10
                              "timestamp_ended" 500}
                         "2" {"defender_faction" 2
                              "attacker_faction" 1
                              "defender_name" "bar"
                              "attacker_name" nil
                              "defender_id" 4
                              "attacker_id" ""
                              "result" "Run away"
                              "respect_gain" 0.0
                              "timestamp_started" 5000
                              "timestamp_ended" 6000}}}]
    (is (= (-> info
               (keywordize-keys)
               (update :attacks (fn [m] (map-keys (comp #(Integer/parseInt %) name) m)))
               (assoc-in [:attacks 2 :attacker_id] nil)
               (update-in [:attacks 1 :timestamp_started] api/long->Date)
               (update-in [:attacks 1 :timestamp_ended] api/long->Date)
               (update-in [:attacks 2 :timestamp_started] api/long->Date)
               (update-in [:attacks 2 :timestamp_ended] api/long->Date))
           (api/attacks (test-client info) "foo")))
    (is (thrown? RuntimeException
                 (api/attacks (test-client info) ""))))
  (is (thrown? RuntimeException
               (api/attacks (test-client {:error {:code 2}}) ""))))
