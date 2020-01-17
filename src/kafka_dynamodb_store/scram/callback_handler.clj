(ns kafka-dynamodb-store.scram.callback-handler
  "Callback handler which can be used using kafka.properties, eg:
  `listener.name.sasl_ssl.scram-sha-256.sasl.server.callback.handler.class=kafka_dynamodb_store.scram.CallbackHandler`."
  (:require [kafka-dynamodb-store.scram.core :as core]
            [clojure.tools.logging :as log])
  (:import [javax.security.auth.callback Callback]
           [javax.security.auth.login AppConfigurationEntry]
           [javax.security.auth.callback NameCallback PasswordCallback]
           [org.apache.kafka.common.security.scram
            ScramCredentialCallback
            ScramCredential])
  (:gen-class
   :name kafka_dynamodb_store.scram.CallbackHandler
   :state state
   :init init
   :implements [org.apache.kafka.common.security.auth.AuthenticateCallbackHandler]
   :methods [[configure [java.util.Map
                         String
                         java.util.List] Void]
             [close [] Void]
             [handle ["[Ljavax.security.auth.callback.Callback;"] Void]]))


(defn -init []
  [[] (atom {})])


(defn -configure
  [this config mechanism app-config-entries]
  (let [m (->> (into [] app-config-entries)
             (map (memfn getOptions))
             (into {}))
        new-config {:table-name (get m "dynamodb_scram_store_table_name")
                    :endpoint   (get m "dynamodb_scram_store_region")
                    :mechanism  (keyword mechanism)}]
    (reset! (.state this)
            new-config)
    (log/info "SCRAM dynamodb handler started with config:" new-config)))


(defn- find-first
  [pred col]
  (->> col
     (filter pred)
     first))


(defn -handle
  "Lookup credentials and attach them to the credentials callback"
  [this callbacks]
  (try
    (let [^NameCallback name-callback (find-first (partial instance? NameCallback) callbacks)
          ^ScramCredentialCallback password-cb (find-first (partial instance? ScramCredentialCallback) callbacks)

          username          (.getDefaultName name-callback)
          state             @(.state this)
          scram-credentials (core/lookup-credentials state username)]

      (log/info "loading scram creds for:" (.getDefaultName name-callback))
      (when-let [scram-sha (get scram-credentials (:mechanism state))]
        (.setName name-callback (.getDefaultName name-callback))
        (.scramCredential password-cb scram-sha)))
    (catch Exception e
      (log/error e "Error while handling credential lookup"))))


(defn -close
  [this])
