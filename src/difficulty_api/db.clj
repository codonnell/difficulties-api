(ns difficulty-api.db
  (:require [com.stuartsierra.component :as component]
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
  "Returns a list of entries [{:stats stats :result :win/:lose}]"
  [db torn-id]
  (map (fn [[{:keys [player/battle-stats]} result]]
         {:stats battle-stats :result (get result-map result)})
       (d/q '[:find (pull ?attacker [:player/battle-stats]) ?result
              :in $ ?defender-id
              :where
              [?defender :player/torn-id ?defender-id]
              [?attack :attack/defender ?defender]
              [?attack :attack/attacker ?attacker]
              [?attack :attack/result ?result-entity]
              [?result-entity :db/ident ?result]]
            db torn-id)))

(defn attack-difficulty
  "Returns :easy if attacker stats are higher than stats and result is :win.
  Returns :impossible if attacker stats are lower than stats and result is :lose.
  Otherwise returns nil."
  [attacker-stats {:keys [stats result]}]
  (cond
    (and (>= attacker-stats stats) (= :win result)) :easy
    (and (<= attacker-stats stats) (= :lose result)) :impossible
    :default nil))

(defn difficulty
  "Returns :unknown, :easy, :medium, or :hard"
  [db attacker-id defender-id]
  (let [attacker-stats (:player/battle-stats
                        (d/q '[:find (pull ?attacker [:player/battle-stats]) .
                                                    :in $ ?attacker-id
                                                    :where [?attacker :player/torn-id ?attacker-id]]
                                                  db attacker-id))]
    (->> (attacks-on db defender-id)
         (keep (partial attack-difficulty attacker-stats))
         (reduce (fn [player-difficulty attack-difficulty]
                   (condp = [player-difficulty attack-difficulty]
                     [:unknown :easy] :easy
                     [:unknown :impossible] :impossible
                     [:easy :easy] :easy
                     [:easy :impossible] (reduced :medium)
                     [:impossible :easy] (reduced :medium)
                     [:impossible :impossible] :impossible))
                 :unknown))))

(defn difficulties
  [db attacker-id defender-ids]
  (if-not (d/q '[:find ?stats .
                 :in $ ?torn-id
                 :where [?p :player/torn-id ?torn-id] [?p :player/battle-stats ?stats]]
               db attacker-id)
    {:error :nonexistent-attacker}
    (into {} (map
              (fn [defender-id] [defender-id (difficulty db attacker-id defender-id)])
              defender-ids))))

