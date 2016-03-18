(ns difficulty-api.db-test
  (:require [difficulty-api.db :as db]
            [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [deftest is use-fixtures]]))

(def test-uri "datomic:mem://difficulty-api-test")

;; -2 beat -4, so -4 should be easy for -3 and -4 should not have a difficulty for -1
;; -2 lost to -5, so -5 should be impossible for -1 and -5 should not have a difficulty for -3
;; -1 beat -6 and -3 lost to -6, so -6 should be medium for -1, -2, and -3
;; -2 beat -7 and lost to -7, so -7 should be impossible for -1, medium for -2, and easy for -3

(def test-data
  [{:db/id #db/id[:db.part/user -1]
    :player/torn-id 1
    :player/battle-stats 5.0}
   {:db/id #db/id[:db.part/user -2]
    :player/torn-id 2
    :player/battle-stats 10.0}
   {:db/id #db/id[:db.part/user -3]
    :player/torn-id 3
    :player/battle-stats 20.0}
   {:db/id #db/id[:db.part/user -4]
    :player/torn-id 4}
   {:db/id #db/id[:db.part/user -5]
    :player/torn-id 5}
   {:db/id #db/id[:db.part/user -6]
    :player/torn-id 6}
   {:db/id #db/id[:db.part/user -7]
    :player/torn-id 7}
   {:db/id #db/id[:db.part/user -8]
    :attack/torn-id 1
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -4]
    :attack/result :attack.result/hospitalize}
   {:db/id #db/id[:db.part/user -9]
    :attack/torn-id 2
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -5]
    :attack/result :attack.result/lose}
   {:db/id #db/id[:db.part/user -11]
    :attack/torn-id 4
    :attack/attacker #db/id[:db.part/user -1]
    :attack/defender #db/id[:db.part/user -6]
    :attack/result :attack.result/mug}
   {:db/id #db/id[:db.part/user -12]
    :attack/torn-id 5
    :attack/attacker #db/id[:db.part/user -3]
    :attack/defender #db/id[:db.part/user -6]
    :attack/result :attack.result/stalemate}
   {:db/id #db/id[:db.part/user -13]
    :attack/torn-id 6
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -7]
    :attack/result :attack.result/hospitalize}
   {:db/id #db/id[:db.part/user -14]
    :attack/torn-id 7
    :attack/attacker #db/id[:db.part/user -2]
    :attack/defender #db/id[:db.part/user -7]
    :attack/result :attack.result/lose}
   ])

(def test-player {:player/torn-id 8
                  :player/api-key "foo"
                  :player/battle-stats 100.0})

(def test-attack {:attack/torn-id 15
                  :attack/attacker [:player/torn-id 1]
                  :attack/defender [:player/torn-id 2]
                  :attack/result :attack.result/hospitalize})

(def duplicate-test-attack {:attack/torn-id 1
                            :attack/attacker [:player/torn-id 2]
                            :attack/defender [:player/torn-id 4]
                            :attack/result :attack.result/hospitalize})

(defn speculate [db t]
  (:db-after
   (d/with db t)))

(defn connect-apply-schema-create-dbs [f]
  (let [db (component/start (db/new-database test-uri))
        conn (:conn db)]
    (def test-db (speculate (d/db conn) test-data))
    (f)
    (component/stop db)))

(use-fixtures :once connect-apply-schema-create-dbs)

;; What should the difficulty api look like? The main use case for queries is
;; we're given a torn ID for the would-be attacker and a list of torn IDs for
;; would-be defenders. We need to return a difficulty rating of unknown, easy,
;; medium, or impossible. If a target has been beaten by someone with equal or
;; lower stats, they're given an easy rating. If a target has successfully
;; defended against someone with equal or better stats, they're given a
;; impossible rating. If someone has been given an easy rating and a impossible
;; rating, they combine to make a medium rating. If a target has been given no
;; ratings, their difficulty is unknown.

;; The main function for this will be
;; (difficulty attacker-id [target-ids])
;; which will return a map with keys equal to the target-ids and values either
;; :unknown, :easy, :medium, or :impossible.

(deftest difficulties-test
  (is (= {4 :easy 5 :unknown 6 :medium 7 :easy}
         (db/difficulties test-db 3 [4 5 6 7])))
  (is (= {4 :easy 5 :impossible 6 :medium 7 :medium}
         (db/difficulties test-db 2 [4 5 6 7])))
  (is (= {4 :unknown 5 :impossible 6 :medium 7 :impossible}
         (db/difficulties test-db 1 [4 5 6 7])))
  (is (= {0 :unknown}
         (db/difficulties test-db 1 [0])))
  (is (= {} (db/difficulties test-db 1 [])))
  (is (= {:error :nonexistent-attacker}
         (db/difficulties test-db 0 []))))

(deftest player-by-torn-id-test
  (is (= {:player/torn-id 1 :player/battle-stats 5.0}
         (db/player-by-torn-id test-db 1)))
  (is (= {:player/torn-id 4} (db/player-by-torn-id test-db 4)))
  (is (nil? (db/player-by-torn-id test-db 0))))

;; There should be two main ways to insert data into the database.
;; First, if we're adding a new player, we'll need to insert them into the database and spawn a call to download their attack log.
;; Second, we'll need to be able to add a list of attacks to the database.

(deftest add-player-test
  (is (= test-player
         (db/player-by-torn-id (speculate test-db (db/add-player-tx test-player)) (:player/torn-id test-player))))
  (let [existing-player (assoc test-player :player/torn-id 1)]
    (is (= existing-player
           (db/player-by-torn-id (speculate test-db (db/add-player-tx existing-player)) 1))))
  (let [new-api-player (assoc test-player :player/api-key "foo")]
    (is (= new-api-player
           (db/player-by-torn-id (speculate test-db (db/add-player-tx new-api-player)) (:player/torn-id new-api-player))))))

(deftest attack-by-torn-id-test
  (is (= {:attack/torn-id 1
          :attack/attacker [:player/torn-id 2]
          :attack/defender [:player/torn-id 4]
          :attack/result :attack.result/hospitalize}
         (db/attack-by-torn-id test-db 1)))
  (is (= nil (db/attack-by-torn-id test-db 0))))

(deftest add-attack-test
  (is (= test-attack
         (db/attack-by-torn-id (speculate test-db (db/add-attacks-tx test-db [test-attack]))
                               (:attack/torn-id test-attack))))
  (let [get-attacks '[:find ?attack :where [?attack :attack/torn-id]]]
    (is (= (count (d/q get-attacks test-db))
           (count (d/q get-attacks (speculate test-db (db/add-attacks-tx test-db [duplicate-test-attack])))))))
  (is (= duplicate-test-attack
         (db/attack-by-torn-id (speculate test-db (db/add-attacks-tx test-db [duplicate-test-attack]))
                               (:attack/torn-id duplicate-test-attack)))))
