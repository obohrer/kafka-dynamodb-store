(ns kafka-dynamodb-store.scram.core
  (:require [kafka-dynamodb-store.scram.scram-utils :as scram]
            [amazonica.aws.dynamodbv2 :as dynamodb]
            [safely.core :refer [safely]]
            [clojure.edn :as edn]
            [clojure.set :as set]))


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


(defn lookup-credentials
  "Lookup credentials for a username, nil if not found"
  [{:keys [table-name endpoint]} username]
  (->> (dynamodb/get-item {:endpoint endpoint}
                        :table-name table-name
                        :key        {:username username})
     :item
     :scram-credentials
     edn/read-string
     (map (juxt first (comp scram/m->ScramCredential second)))
     (into {})))


(defn set-user-credentials!
  "Set a new or existing user credentials"
  [{:keys [table-name endpoint]} username password]
  (let [scram-credentials (scram/scram-with-all-mechanisms password)]
    (safely
        (dynamodb/put-item {:endpoint endpoint}
                           :table-name table-name
                           :item       {:username          username
                                        :scram-credentials (prn-str scram-credentials)})
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
  [{:keys [table-name endpoint]} usernames-and-passwords]
  (let [db-cfg {:endpoint endpoint :table-name table-name}
        usernames (list-usernames db-cfg)
        deleted-users (set/difference usernames (set (map :username usernames-and-passwords)))]
    (doseq [to-delete deleted-users]
      (delete-user! db-cfg to-delete))
    (doseq [{:keys [username password]} usernames-and-passwords]
      (set-user-credentials! db-cfg username password))))



(comment
  (set-user-credentials! {:table-name "Test" :endpoint "eu-west-1"}
                         "Foo" "FOOBAR")

  (list-usernames {:table-name "Test" :endpoint "eu-west-1"})
  (lookup-credentials {:table-name "Test" :endpoint "eu-west-1"}
                      "Foo")
  (delete-user {:table-name "Test" :endpoint "eu-west-1"}
               "Foo"))

