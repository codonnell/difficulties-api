(ns difficulty-api.db
  (:require [com.stuartsierra.component :as component]
            [medley.core :refer [map-vals]]
            [datomic.api :as d]))

(defrecord Database [uri]
  component/Lifecycle

  (start [component]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn (read-string (slurp "schema.edn")))
      (assoc component :conn conn)))

  (stop [component]
    (d/release (:conn component))
    (assoc component :conn nil)))

(defn new-database [uri]
  (map->Database {:uri uri}))

(def result-map
  {:attack.result/stalemate :lose
   :attack.result/hospitalize :win
   :attack.result/leave :win
   :attack.result/mug :win
   :attack.result/lose :lose})

(defn attacks-on
  "Returns a list of entries [{:attacker-stats stats :result :win/:lose}]"
  [db defender-ids]
  (->> (d/q '[:find
              (pull ?attack [{:attack/attacker [:player/battle-stats]}
                             {:attack/defender [:player/torn-id]}])
              ?result
              :in $ [?defender-id ...]
              :where
              [?defender :player/torn-id ?defender-id]
              [?attack :attack/defender ?defender]
              [?attack :attack/attacker ?attacker]
              [?attack :attack/result ?result-entity]
              [?result-entity :db/ident ?result]]
            db defender-ids)
       (map (fn [[attack result]]
              {:attacker-stats (get-in attack [:attack/attacker :player/battle-stats])
               :defender-id (get-in attack [:attack/defender :player/torn-id])
               :result (get result-map result)}))
       (group-by :defender-id)))

(defn attack-difficulty
  "Returns :easy if caller stats are higher than attacker stats and result is :win.
  Returns :impossible if caller stats are lower than attacker stats and result is :lose.
  Otherwise returns nil."
  [caller-stats {:keys [attacker-stats result]}]
  (cond
    (and (>= caller-stats attacker-stats) (= :win result)) :easy
    (and (<= caller-stats attacker-stats) (= :lose result)) :impossible
    :default nil))

(defn difficulty
  "Returns :unknown, :easy, :medium, or :hard"
  [db caller-stats attacks]
  (->> attacks
   (keep (partial attack-difficulty caller-stats))
   (reduce (fn [player-difficulty attack-difficulty]
             (condp = [player-difficulty attack-difficulty]
               [:unknown :easy] :easy
               [:unknown :impossible] :impossible
               [:easy :easy] :easy
               [:easy :impossible] (reduced :medium)
               [:impossible :easy] (reduced :medium)
               [:impossible :impossible] :impossible))
           :unknown)))

(defn difficulties
  [db attacker-id defender-ids]
  (if-let [caller-stats
           (d/q '[:find ?stats .
                  :in $ ?torn-id
                  :where [?p :player/torn-id ?torn-id] [?p :player/battle-stats ?stats]]
                db attacker-id)]
    (map-vals
     (fn [attacks] (difficulty db caller-stats attacks))
     (merge (zipmap defender-ids (repeat [])) (attacks-on db defender-ids)))
    {:error :nonexistent-attacker}))

(def player-pull [:player/torn-id :player/api-key :player/battle-stats])

(defn player-by-torn-id [db torn-id]
  (d/q '[:find (pull ?player player-pull) .
         :in $ ?torn-id player-pull
         :where [?player :player/torn-id ?torn-id]]
       db torn-id player-pull))

(defn add-player-tx [player]
  [(merge {:db/id #db/id[:db.part/user -1]} player)])

(defn add-player [conn player]
  (d/transact conn (add-player-tx player)))

(def attack-pull
  [:attack/torn-id :attack/start-time :attack/end-time {:attack/attacker [:player/torn-id]}
   {:attack/defender [:player/torn-id]}])

(defn attack-by-torn-id [db torn-id]
  (let [[[attack result]] (d/q '[:find (pull ?attack attack-pull) ?result
                               :in $ ?attack-id attack-pull
                               :where
                               [?attack :attack/torn-id ?attack-id]
                               [?attack :attack/result ?result-id]
                               [?result-id :db/ident ?result]]
                             db torn-id attack-pull)]
    (when attack (assoc attack
                        :attack/attacker (first (vec (get attack :attack/attacker)))
                        :attack/defender (first (vec (get attack :attack/defender)))
                        :attack/result result))))

(defn add-attacks-tx [db attacks]
  (let [new-attacks (take-while (fn [attack] (not (attack-by-torn-id db (:attack/torn-id attack)))) attacks)]
    (mapv (fn [attack] (assoc attack
                              :db/id (d/tempid :db.part/user)
                              :attack/attacker (:attack/attacker attack)
                              :attack/defender (:attack/defender attack)
                              :attack/result [:db/ident (:attack/result attack)]))
          new-attacks)))

(defn add-attack-tx [db attack]
  (add-attacks-tx db [attack]))
