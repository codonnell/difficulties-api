(ns difficulty-api.dispatch
  (require [difficulty-api.db :as db]
           [difficulty-api.torn-api :as api]
           [clojure.core.async :refer [go]]))

(defn add-api-key [http-client db api-key]
  (if-let [torn-id (:player_id (api/valid-api-key? http-client api-key))]
    (do (go (db/add-player db {:player/torn-id torn-id
                               :player/api-key api-key
                               :player/battle-stats (api/total-battle-stats
                                                     (api/battle-stats http-client api-key))}))
        true)
    (throw (ex-info "Invalid API key" {:api-key api-key
                                       :type :invalid-api-key}))))

(defn difficulties [http-client db api-key torn-ids]
  (if-let [attacker-id (:player/torn-id (db/player-by-api-key db api-key))]
    (db/difficulties db attacker-id torn-ids)
    (throw (ex-info "Unknown API key" {:api-key api-key
                                        :type :unknown-api-key}))))

