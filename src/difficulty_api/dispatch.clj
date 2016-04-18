(ns difficulty-api.dispatch
  (require [difficulty-api.db :as db]
           [difficulty-api.torn-api :as api]
           [clj-time.core :as t]
           [clj-time.coerce :refer [to-date from-date]]
           [clojure.core.async :refer [go]]))

(defn difficulties [db api-key torn-ids]
  (if-let [attacker-id (:player/torn-id (db/player-by-api-key db api-key))]
    (db/difficulties db attacker-id torn-ids)
    (throw (ex-info "Unknown API key" {:api-key api-key
                                       :type :unknown-api-key}))))

(defn update-attacks [http-client db api-key]
  (if-let [torn-id (:player/torn-id (db/player-by-api-key db api-key))]
    (db/update-attacks db torn-id (api/api-attacks->schema-attacks (api/attacks http-client api-key)))
    (throw (ex-info "Unknown attacker" {:player/api-key api-key}))))

(defn update-attacks-if-outdated [http-client db api-key]
  (let [last-update (:player/last-attack-update (db/player-by-api-key db api-key))]
    (when (or (not last-update) (t/after? (t/ago (t/hours 1)) last-update))
      (update-attacks http-client db api-key))))

(defn update-attacks-full [http-client db api-key]
  (if-let [torn-id (:player/torn-id (db/player-by-api-key db api-key))]
    (db/update-attacks db torn-id (api/api-attacks->schema-attacks (api/attacks-full http-client api-key)))
    (throw (ex-info "Unknown attacker" {:player/api-key api-key}))))

(defn add-api-key [http-client db api-key]
  (if-let [torn-id (:player_id (api/valid-api-key? http-client api-key))]
    (do (future (db/add-player db {:player/torn-id torn-id
                                   :player/api-key api-key
                                   :player/battle-stats (api/total-battle-stats
                                                         (api/battle-stats http-client api-key))
                                   :player/last-attack-update (t/epoch)
                                   :player/last-battle-stats-update (t/now)})
                (update-attacks-full http-client db api-key))
        true)
    (throw (ex-info "Invalid API key" {:api-key api-key
                                       :type :invalid-api-key}))))

(defn update-battle-stats [http-client db api-key]
  (if-let [torn-id (:player/torn-id (db/player-by-api-key db api-key))]
    (db/update-battle-stats db torn-id (api/total-battle-stats
                                        (api/battle-stats http-client api-key)))
    (throw (ex-info "Unknown player" {:player/api-key api-key}))))

(defn update-battle-stats-if-outdated [http-client db api-key]
  (let [last-update (:player/last-battle-stats-update (db/player-by-api-key db api-key))]
    (when (or (not last-update) (t/after? (t/ago (t/days 1)) last-update))
      (update-battle-stats http-client db api-key))))
