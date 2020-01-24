(ns kafka-dynamodb-store.scram.core
  (:require [kafka-dynamodb-store.scram.scram-utils :as scram]
            [kafka-dynamodb-store.kms-utils :as kms]
            [amazonica.aws.dynamodbv2 :as dynamodb]
            [safely.core :refer [safely]]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.core.memoize :as memo]))


(defn create-table!
  "Create the scram store table"
  [{:keys [table-name endpoint]}]
  (dynamodb/create-table {:endpoint endpoint}
                         :table-name table-name
                         :key-schema
                         [{:attribute-name "username"  :key-type "HASH"}]
                         :attribute-definitions
                         [{:attribute-name "username"  :attribute-type "S"}]
                         :provisioned-throughput
                         {:read-capacity-units 1
                          :write-capacity-units 1}))


(defn- parse-credentials
  [region kms-key-id scram-credentials]
  (let [parsed (edn/read-string scram-credentials)]
    (if kms-key-id
      (if-let [decrypted (some->> parsed
                                :result
                                (kms/decrypt region)
                                :result
                                edn/read-string)]
        decrypted
        (throw (ex-info "Cannot decrypt credentials, record tempered with?"
                        {:kms-key-id kms-key-id :encrypted-payload parsed})))
      parsed)))

(defn- encode-credentials
  [region kms-key-id scram-credentials]
  (let [encoded (prn-str scram-credentials)]
    (if kms-key-id
      (prn-str (kms/encrypt region kms-key-id encoded))
      encoded)))


(defn lookup-credentials*
  "Lookup credentials for a username, nil if not found"
  [{:keys [table-name endpoint kms-key-id]} username]
  (some->> (dynamodb/get-item {:endpoint endpoint}
                              :table-name table-name
                              :key        {:username username})
           :item
           :scram-credentials
           (parse-credentials endpoint kms-key-id)
           (map (juxt first (comp scram/m->ScramCredential second)))
           (into {})))

;; Cache the credentials for 30 seconds to avoid hitting
;; dynamodb too hard in case of aggressive retries from clients
(def lookup-credentials
  (memo/ttl lookup-credentials* {} :ttl/threshold 30000))


(defn set-user-credentials!
  "Set a new or existing user credentials"
  [{:keys [table-name endpoint kms-key-id]} username password]
  (let [scram-credentials (scram/scram-with-all-mechanisms password)]
    (safely
        (dynamodb/put-item {:endpoint endpoint}
                           :table-name table-name
                           :item
                           {:username          username
                            :scram-credentials (encode-credentials endpoint kms-key-id scram-credentials)})
      :on-error
      :max-retries 10
      :retry-delay [:random-exp-backoff :base 3000 :+/- 0.35 :max 25000])
    nil))


(defn list-usernames
  [{:keys [table-name endpoint]}]
  (loop [last-evaluated-key nil
         results []]
    (let [step (safely
                   (dynamodb/scan {:endpoint endpoint}
                                  :table-name table-name
                                  :projection-expression "username")
                 :on-error
                 :max-retries 10
                 :retry-delay [:random-exp-backoff :base 3000 :+/- 0.35 :max 25000])
          all-results (set (concat results (->> step
                                              :items
                                              (map :username))))]
      (if (:last-evaluated-key step)
        (recur (:last-evaluated-key step)
               all-results)
        all-results))))


(defn delete-user!
  "Delete a user by username"
  [{:keys [table-name endpoint]} username]
  (safely
      (dynamodb/delete-item {:endpoint endpoint}
                            :table-name table-name
                            :key        {:username username})
    :on-error
    :max-retries 10
    :retry-delay [:random-exp-backoff :base 3000 :+/- 0.35 :max 25000])
  nil)


(defn reset-users!
  "Reset the users list with a new one (col of maps).
  This operation is not safe when ran concurrently"
  [{:keys [table-name endpoint] :as db-cfg} usernames-and-passwords]
  (let [usernames (list-usernames db-cfg)
        deleted-users (set/difference usernames (set (map :username usernames-and-passwords)))]
    (doseq [to-delete deleted-users]
      (delete-user! db-cfg to-delete))
    (doseq [{:keys [username password]} usernames-and-passwords]
      (set-user-credentials! db-cfg username password))))
