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

(declare ^:dynamic system)

(defn with-system [f]
  (binding [system (component/start (test-system {}))]
    (f)
    (component/stop system)))

(use-fixtures :each with-system)

(deftest adds-valid-api-key
  (let [http-client
        (reify api/HttpClient
          (http-get [this url]
            (cond
              (= "http://api.torn.com/user/?selections=basic&key=foo" url)
              {:body (json/encode basic-info-test-data)}

              (= "http://api.torn.com/user/?selections=battlestats&key=foo" url)
              {:body (json/encode battle-stats-test-data)})))]
    (is (= {:player/torn-id (get basic-info-test-data "player_id")
            :player/battle-stats (api/total-battle-stats battle-stats-test-data)
            :player/api-key "foo"}
           (do (pprint (:db system))
               (dispatch/add-api-key http-client (:db system) "foo")
               (Thread/sleep 100)
               (db/player-by-api-key (:db system) "foo"))))))

