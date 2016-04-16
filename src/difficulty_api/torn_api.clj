(ns difficulty-api.torn-api
  (:require [clj-http.client :as http]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [clojure.string :refer [join]]
            [cheshire.core :as json]
            [medley.core :refer [map-keys]]
            [difficulty-api.schema :as schema]))

(defprotocol HttpClient
  "An extremely naive http client which can send GET requests to urls."
  (http-get [this url] "Sends a GET request to given url."))

(def clj-http-client
  (reify
    HttpClient
    (http-get [this url] (http/get url))))

(def error-codes
  {0 "Unknown error: Unhandled error, should not occur."
   1 "Key is empty: Private key is empty in current request."
   2 "Incorrect Key: Private key is wrong/incorrect format."
   3 "Wrong type: Requesting an incorrect basic type."
   4 "Wrong fields: Requesting incorrect selection fields."
   5 "Too many requests: Current private key is banned for a small period of time because of too many requests (max 100 per minute)."
   6 "Incorrect ID: Wrong ID value."
   7 "Incorrect ID-entity relation: A requested selection is private (For example, personal data of another user / faction)."
   8 "IP block: Current IP is banned for a small period of time because of abuse."})

(defn user-query-url
  ([api-key selections]
   (user-query-url api-key selections nil))
  ([api-key selections id]
   (cond (empty? api-key) (throw (ex-info "api-key cannot be empty" {}))
         (empty? selections) (throw (ex-info "selections cannot be empty" {}))
         :default (format "https://api.torn.com/user/%s?selections=%s&key=%s"
                          (if id (str id) "")
                          (join "," selections)
                          api-key))))

(defn sanitize-resp [resp]
  "Decodes the body of the response. If it's an error response, throws an
  ex-info whose map is the response."
  (let [parsed-resp (update resp :body #(json/decode % true))]
    (if-let [error-code (get-in parsed-resp [:body :error :code])]
      (throw (ex-info (get error-codes error-code) parsed-resp))
      parsed-resp)))

(def queries
  {:basic-info
   {:schema {:level s/Int
             :gender s/Str
             :status s/Str
             :player_id s/Int
             :name s/Str}
    :selections ["basic"]}
   :battle-stats
   {:schema {:strength s/Num
             :speed s/Num
             :dexterity s/Num
             :defense s/Num
             :strength_modifier s/Int
             :speed_modifier s/Int
             :dexterity_modifier s/Int
             :defense_modifier s/Int
             :strength_info [s/Str]
             :speed_info [s/Str]
             :dexterity_info [s/Str]
             :defense_info [s/Str]}
    :selections ["battlestats"]}
   :attacks
   {:schema {:attacks {s/Int {:defender_faction s/Int
                              :attacker_faction (s/maybe s/Int)
                              :defender_name s/Str
                              :attacker_name (s/maybe s/Str)
                              :defender_id s/Int
                              :attacker_id (s/maybe s/Int)
                              :result s/Str
                              :respect_gain s/Num
                              :timestamp_started s/Inst
                              :timestamp_ended s/Inst}}}
    :selections ["attacks"]}
   :attacks-full
   {:schema {:attacks {s/Int {:defender_faction s/Int
                              :attacker_faction (s/maybe s/Int)
                              :defender_id s/Int
                              :attacker_id (s/maybe s/Int)
                              :result s/Str
                              :respect_gain s/Num
                              :timestamp_started s/Inst
                              :timestamp_ended s/Inst}}}
    :selections ["attacksfull"]}
   :personal-stats
   {:schema {:personalstats {(s/optional-key :logins) s/Int,
                             (s/optional-key :useractivity) s/Num,
                             (s/optional-key :weaponsbought) s/Int,
                             (s/optional-key :jailed) s/Int,
                             (s/optional-key :dumpsearches) s/Int,
                             (s/optional-key :dumpfinds) s/Int,
                             (s/optional-key :pointssold) s/Num,
                             (s/optional-key :daysbeendonator) s/Int,
                             (s/optional-key :roundsfired) s/Int,
                             (s/optional-key :attackmisses) s/Int,
                             (s/optional-key :hospital) s/Int,
                             (s/optional-key :attackslost) s/Int,
                             (s/optional-key :attackhits) s/Int,
                             (s/optional-key :attackcriticalhits) s/Int,
                             (s/optional-key :shohits) s/Int,
                             (s/optional-key :attackswon) s/Int,
                             (s/optional-key :highestbeaten) s/Int,
                             (s/optional-key :bestkillstreak) s/Int,
                             (s/optional-key :cityfinds) s/Int,
                             (s/optional-key :pishits) s/Int,
                             (s/optional-key :moneymugged) s/Num,
                             (s/optional-key :largestmug) s/Num,
                             (s/optional-key :defendslost) s/Int,
                             (s/optional-key :medicalitemsused) s/Int,
                             (s/optional-key :itemsbought) s/Int,
                             (s/optional-key :drugsused) s/Int,
                             (s/optional-key :xantaken) s/Int,
                             (s/optional-key :attacksstealthed) s/Int,
                             (s/optional-key :piehits) s/Int,
                             (s/optional-key :attacksassisted) s/Int,
                             (s/optional-key :mailssent) s/Int,
                             (s/optional-key :factionmailssent) s/Int,
                             (s/optional-key :defendswon) s/Int,
                             (s/optional-key :trainsreceived) s/Int,
                             (s/optional-key :exttaken) s/Int,
                             (s/optional-key :defendsstalemated) s/Int,
                             (s/optional-key :traveltimes) s/Int,
                             (s/optional-key :mextravel) s/Int,
                             (s/optional-key :itemsboughtabroad) s/Num,
                             (s/optional-key :pointsbought) s/Num,
                             (s/optional-key :lontravel) s/Int,
                             (s/optional-key :itemssent) s/Int,
                             (s/optional-key :switravel) s/Int,
                             (s/optional-key :bazaarcustomers) s/Int,
                             (s/optional-key :bazaarsales) s/Num,
                             (s/optional-key :bazaarprofit) s/Num,
                             (s/optional-key :itemsdumped) s/Int,
                             (s/optional-key :cantravel) s/Int,
                             (s/optional-key :hawtravel) s/Int,
                             (s/optional-key :caytravel) s/Int,
                             (s/optional-key :japtravel) s/Int,
                             (s/optional-key :dubtravel) s/Int,
                             (s/optional-key :bountiesreceived) s/Int,
                             (s/optional-key :pcptaken) s/Int,
                             (s/optional-key :overdosed) s/Int,
                             (s/optional-key :soutravel) s/Int,
                             (s/optional-key :chitravel) s/Int,
                             (s/optional-key :bountiesplaced) s/Int,
                             (s/optional-key :totalbountyspent) s/Num,
                             (s/optional-key :virusescoded) s/Int,
                             (s/optional-key :smghits) s/Int,
                             (s/optional-key :argtravel) s/Int,
                             (s/optional-key :respectforfaction) s/Num,
                             (s/optional-key :bountiescollected) s/Int,
                             (s/optional-key :totalbountyreward) s/Num,
                             (s/optional-key :yourunaway) s/Int,
                             (s/optional-key :revivesreceived) s/Int,
                             (s/optional-key :rifhits) s/Int,
                             (s/optional-key :cantaken) s/Int,
                             (s/optional-key :victaken) s/Int,
                             (s/optional-key :trades) s/Int,
                             (s/optional-key :auctionswon) s/Int,
                             (s/optional-key :auctionsells) s/Int,
                             (s/optional-key :theyrunaway) s/Int,
                             (s/optional-key :machits) s/Int,
                             (s/optional-key :slahits) s/Int,
                             (s/optional-key :friendmailssent) s/Int,
                             (s/optional-key :shrtaken) s/Int,
                             (s/optional-key :bloodwithdrawn) s/Int,
                             (s/optional-key :heahits) s/Int,
                             (s/optional-key :grehits) s/Int,
                             (s/optional-key :axehits) s/Int,
                             (s/optional-key :peoplebusted) s/Int,
                             (s/optional-key :networth) s/Num,
                             (s/optional-key :chahits) s/Int,
                             (s/optional-key :attacksdraw) s/Int,
                             (s/optional-key :companymailssent) s/Int,
                             (s/optional-key :revives) s/Int,
                             (s/optional-key :failedbusts) s/Int,
                             (s/optional-key :peoplebought) s/Int,
                             (s/optional-key :peopleboughtspent) s/Num,
                             (s/optional-key :spousemailssent) s/Int,
                             (s/optional-key :meritsbought) s/Int,
                             (s/optional-key :kettaken) s/Int,
                             (s/optional-key :lsdtaken) s/Int,
                             (s/optional-key :refills) (s/maybe s/Int),
                             (s/optional-key :personalsplaced) s/Int,
                             (s/optional-key :opitaken) s/Int,
                             (s/optional-key :spetaken) s/Int,
                             (s/optional-key :itemswon) s/Int,
                             (s/optional-key :statenhancersused) s/Int,
                             (s/optional-key :classifiedadsplaced) s/Int}}
    :selections ["personalstats"]}})

(defn long->Date [timestamp]
  (java.util.Date. (long timestamp)))

(defn maybe-string->id [id]
  (if (string? id)
    (if (empty? id) nil (Integer/parseInt id))
    id))

(defn ->int [x]
  (if (integer? x) x
      (Integer/parseInt (name x))))

(defn torn-api-matcher [schema]
  (or ({s/Inst long->Date
        (s/maybe s/Int) maybe-string->id
        s/Int ->int} schema)
      (coerce/json-coercion-matcher schema)))

(def resp-parser
  (memoize
   (fn [req-name]
     (coerce/coercer (get-in queries [req-name :schema]) torn-api-matcher))))

(defn user-api-call
  ([req-name http-client api-key]
   (user-api-call req-name http-client api-key nil))
  ([req-name http-client api-key id]
   (let [resp (sanitize-resp (http-get http-client
                                       (user-query-url api-key (get-in queries [req-name :selections]))))]
     ((resp-parser req-name) (:body resp)))))

(def basic-info (partial user-api-call :basic-info))

(defn valid-api-key? [http-client api-key]
  (if (empty? api-key) false
      (try (basic-info http-client api-key)
           (catch RuntimeException e
             (let [resp (ex-data e)]
               (if (#{1 2} (get-in resp [:body :error :code]))
                 false
                 (throw e)))))))

(def battle-stats (partial user-api-call :battle-stats))

(defn total-battle-stats [stats]
  (as-> stats x
    (select-keys x [:strength :speed :dexterity :defense])
    (vals x)
    (reduce + 0 x)))

(def attacks (partial user-api-call :attacks))

(def attacks-full (partial user-api-call :attacks-full))

(def attack-key-conversions
  {:defender_id :attack/defender
   :attacker_id :attack/attacker
   :result :attack/result
   :timestamp_started :attack/timestamp-started
   :timestamp_ended :attack/timestamp-ended})

(def result-conversions
  {"Hospitalize" :attack.result/hospitalize
   "Stalemate" :attack.result/stalemate
   "Leave" :attack.result/leave
   "Mug" :attack.result/mug
   "Lose" :attack.result/lose
   "Run away" :attack.result/run-away
   "Timeout" :attack.result/timeout})

(defn api-attacks->schema-attacks
  "Takes a parsed response to the attacks endpoint and returns a list of attacks matching the Attack schema."
  [attacks]
  (s/validate
   [schema/Attack]
   (map (fn [[torn-id attack]]
          (as-> attack a
            (map-keys attack-key-conversions a)
            (select-keys a (keys schema/Attack))
            (update a :attack/result result-conversions)
            (assoc a :attack/torn-id torn-id)))
        (:attacks attacks))))

(def personal-stats (comp
                     ;; Add in missing keys with value 0
                     (fn [m]
                       (let [stats
                             {:personalstats
                              (merge
                               (zipmap (map :k (keys (get-in queries [:personal-stats :schema :personalstats])))
                                       (repeat 0))
                               (:personalstats m))}]
                         (if-let [refills (get-in stats [:personalstats :refills])]
                           stats
                           (assoc-in stats [:personalstats :refills] 0))))
                     (partial user-api-call :personal-stats)))
