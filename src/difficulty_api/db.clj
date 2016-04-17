(ns difficulty-api.db
  (:require [com.stuartsierra.component :as component]
            [medley.core :refer [map-vals]]
            [datomic.api :as d]
            [schema.core :as s]
            [clj-time.core :refer [now]]
            [clj-time.coerce :refer [to-date from-date]]
            [difficulty-api.schema :as schema]))

(defrecord Database [uri]
  component/Lifecycle

  (start [component]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn (read-string (slurp "schema.edn")))
      (assoc component :conn conn)))

  (stop [component]
    (if-let [conn (:conn component)]
      (d/release conn))
    (assoc component :conn nil)))

(defn new-database [uri]
  (map->Database {:uri uri}))

(def result-map
  {:attack.result/stalemate :lose
   :attack.result/hospitalize :win
   :attack.result/leave :win
   :attack.result/mug :win
   :attack.result/lose :lose
   :attack.result/run-away :lose
   :attack.result/timeout :lose})

(def ResultEntity
  (s/conditional (fn [[k v]] (and (= :db/ident k)
                                  (set (keys result-map)) v))
                 [s/Any]))

(def DbAttack
  {:attack/torn-id s/Int
   (s/optional-key :attack/attacker) {:player/torn-id s/Int}
   :attack/defender {:player/torn-id s/Int}
   :attack/result ResultEntity
   :attack/timestamp-started s/Inst
   :attack/timestamp-ended s/Inst})

(defn db-attack->schema-attack
  "Converts a pair [attack result] to a map matching schema/Attack."
  [[attack result]]
  (s/validate
   schema/Attack
   (assoc attack
          :attack/attacker (get-in attack [:attack/attacker :player/torn-id])
          :attack/defender (get-in attack [:attack/defender :player/torn-id])
          :attack/result result)))

(defn schema-attack->db-attack
  "Converts a map matching schema/Attack to have ident pairs for datomic. If the
  attack is anonymous (and so :attack/attacker is nil), removes that key."
  [attack]
  (s/validate
   DbAttack
   (as-> attack a
     (assoc a
            :attack/attacker {:player/torn-id (:attack/attacker attack)}
            :attack/defender {:player/torn-id (:attack/defender attack)}
            :attack/result [:db/ident (:attack/result attack)])
     (if (nil? (:attack/attacker attack))
       (dissoc a :attack/attacker)
       a))))

(defn db-player->schema-player
  "Translates a player to the schema representation. That just means changing the :player/last-attack-update from a java Date into a joda DateTime."
  [player]
  (if (get player :player/last-attack-update)
    (update player :player/last-attack-update from-date)
    player))

(defn schema-player->db-player
  "Translates a player to the db representation. That just means changing the :player/last-attack-update from a joda DateTime into a java Date."
  [player]
  (if (get player :player/last-attack-update)
    (update player :player/last-attack-update to-date)
    player))

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
    (nil? attacker-stats) nil
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
               [:unknown nil] :unknown
               [:easy :easy] :easy
               [:easy :impossible] (reduced :medium)
               [:easy nil] :easy
               [:impossible :easy] (reduced :medium)
               [:impossible :impossible] :impossible
               [:impossible nil] :impossible))
           :unknown)))

(defn difficulties*
  [db attacker-id defender-ids]
  (if-let [caller-stats
           (d/q '[:find ?stats .
                  :in $ ?torn-id
                  :where [?p :player/torn-id ?torn-id] [?p :player/battle-stats ?stats]]
                db attacker-id)]
    (map-vals
     (fn [attacks] (difficulty db caller-stats attacks))
     (merge (zipmap defender-ids (repeat [])) (attacks-on db defender-ids)))
    (throw (ex-info "Attacker not in database" {:player/torn-id attacker-id}))))

(defn difficulties [db attacker-id defender-ids]
  (difficulties* (d/db (:conn db)) attacker-id defender-ids))

(def player-pull [:player/torn-id :player/api-key :player/battle-stats :player/last-attack-update])

(defn player-by-torn-id* [db torn-id]
  (db-player->schema-player
   (d/q '[:find (pull ?player player-pull) .
          :in $ ?torn-id player-pull
          :where [?player :player/torn-id ?torn-id]]
        db torn-id player-pull)))

(defn player-by-torn-id [db torn-id]
  (player-by-torn-id* (d/db (:conn db)) torn-id))

(defn player-by-api-key* [db api-key]
  (db-player->schema-player
   (d/q '[:find (pull ?player player-pull) .
          :in $ ?api-key player-pull
          :where [?player :player/api-key ?api-key]]
        db api-key player-pull)))

(defn player-by-api-key [db api-key]
  (player-by-api-key* (d/db (:conn db)) api-key))

(defn add-players-tx [players]
  (mapv (fn [player] (assoc (schema-player->db-player player)
                            :db/id (d/tempid :db.part/user)))
        players))

(defn add-player-tx [player]
  (add-players-tx [player]))

(defn add-players [db players]
  (d/transact (:conn db) (add-players-tx players)))

(defn add-player [db player]
  (add-players db [player]))

(def attack-pull
  [:attack/torn-id :attack/timestamp-started :attack/timestamp-ended {:attack/attacker [:player/torn-id]}
   {:attack/defender [:player/torn-id]}])

(defn attack-by-torn-id* [db torn-id]
  (let [[db-attack] (d/q '[:find (pull ?attack attack-pull) ?result
                           :in $ ?attack-id attack-pull
                           :where
                           [?attack :attack/torn-id ?attack-id]
                           [?attack :attack/result ?result-id]
                           [?result-id :db/ident ?result]]
                         db torn-id attack-pull)]
    (when db-attack
      (db-attack->schema-attack db-attack))))

(defn attack-by-torn-id [db torn-id]
  (attack-by-torn-id* (d/db (:conn db)) torn-id))

(defn add-attacks-tx [attacks]
  (mapv (fn [attack]
          (assoc (schema-attack->db-attack attack) :db/id (d/tempid :db.part/user)))
        attacks))

(defn attacks-by-attacker-id* [db attacker-id]
  (let [attacks (d/q '[:find (pull ?attack attack-pull) ?result
                       :in $ ?attacker-id attack-pull
                       :where
                       [?attack :attack/attacker ?attacker]
                       [?attacker :player/torn-id ?attacker-id]
                       [?attack :attack/result ?result-id]
                       [?result-id :db/ident ?result]]
                     db attacker-id attack-pull)]
    attacks))

(defn attacks-by-attacker-id [db attacker-id]
  (attacks-by-attacker-id* (d/db (:conn db)) attacker-id))

(defn add-attack-tx [attack]
  (add-attacks-tx [attack]))

(defn add-attacks [db attacks]
  (d/transact (:conn db) (add-attacks-tx attacks)))

(defn add-attack [db attack]
  (d/transact (:conn db) (add-attack-tx attack)))

(defn update-attacks [db torn-id attacks]
  (d/transact (:conn db) (conj (add-attacks-tx attacks)
                               {:db/id (d/tempid :db.part/user)
                                :player/torn-id torn-id
                                :player/last-attack-update (to-date (now))})))

(defn update-battle-stats-tx [db torn-id battle-stats]
  (let [ent-id (d/entid db [:player/torn-id torn-id])]
    [[:db.fn/cas ent-id :player/battle-stats (:player/battle-stats (d/entity db ent-id)) battle-stats]]))

(defn update-battle-stats [db torn-id battle-stats]
  (d/transact (:conn db) (update-battle-stats-tx (d/db (:conn db)) torn-id battle-stats)))

(defn fix-attack-timestamps [db]
  (let [attacks (d/q '[:find [(pull ?attack [:attack/torn-id
                                             :attack/timestamp-started
                                             :attack/timestamp-ended]) ...]
                       :where [?attack :attack/torn-id]]
                     (d/db (:conn db)))
        fix-date (fn [date] (java.util.Date. (* 1000 (.getTime date))))
        fixed-attacks (mapv (fn [attack]
                              (-> attack
                                  (update :attack/timestamp-started fix-date)
                                  (update :attack/timestamp-ended fix-date)
                                  (assoc :db/id (d/tempid :db.part/user))))
                            attacks)]
    (d/transact (:conn db) fixed-attacks)))
