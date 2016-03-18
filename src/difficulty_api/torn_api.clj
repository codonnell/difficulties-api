(ns difficulty-api.torn-api
  (:require [clj-http.client :as http]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [clojure.string :refer [join]]
            [cheshire.core :as json]
            [schema.core :as s]))

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
   (cond (empty? api-key) (throw (ex-info "api-key cannot be empty." {}))
         (empty? selections) (throw (ex-info "selections cannot be empty." {}))
         :default (format "http://api.torn.com/user/%s?selections=%s&key=%s"
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
                              :attacker_faction s/Int
                              :defender_name s/Str
                              :attacker_name (s/maybe s/Str)
                              :defender_id s/Int
                              :attacker_id (s/maybe s/Int)
                              :result s/Str
                              :respect_gain s/Num
                              :timestamp_started s/Inst
                              :timestamp_ended s/Inst}}}
    :selections ["attacks"]}})

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

(def attacks (partial user-api-call :attacks))
