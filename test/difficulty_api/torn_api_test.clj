(ns difficulty-api.torn-api-test
  (:require [difficulty-api.torn-api :as api]
            [cheshire.core :as json]
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

(deftest basic-info-test
  (let [info {:level 1 :gender "Male" :status "Civilian" :player_id 1234 :name "foo"}]
    (is (= info
           (api/basic-info (test-client info) "foo")))
    (is (thrown? RuntimeException
                 (api/basic-info (test-client info "")))))
  (is (thrown? RuntimeException
               (api/basic-info (test-client {:error {:code 1}})))))

(deftest valid-api-key-test
  (let [info {:level 1 :gender "Male" :status "Civilian" :player_id 1234 :name "foo"}]
    (is (api/valid-api-key? (test-client info) "foo")))
  (is (not (api/valid-api-key? (test-client {:error {:code 2}}) "bar")))
  (is (not (api/valid-api-key? (test-client {:error {:code 1}}) ""))))
