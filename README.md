# kafka-dynamodb-store
Kafka storage handlers to bypass zookeeper.


The current implementation allows you to store scram credentials in AWS DynamoDB instead of zookeeper. It is still in the early stages but should allow in the future to not rely on zookeeper for most config/acls/credentials.

To enable it if you are using `sasl_ssl` add the following lines to your `kafka.properties` file:

```
#if you want to use SCRAM-SHA-512

sasl.enabled.mechanisms=SCRAM-SHA-512
listener.name.sasl_ssl.scram-sha-512.sasl.server.callback.handler.class=kafka_dynamodb_store.scram.CallbackHandler


#If you want to support multiple mechanisms:
sasl.enabled.mechanisms=SCRAM-SHA-512,SCRAM-SHA-256
listener.name.sasl_ssl.scram-sha-512.sasl.server.callback.handler.class=kafka_dynamodb_store.scram.CallbackHandler
listener.name.sasl_ssl.scram-sha-256.sasl.server.callback.handler.class=kafka_dynamodb_store.scram.CallbackHandler

```

And in the kafka.jaas specify the `dynamodb_scram_store_table_name` and `dynamodb_scram_store_region=` entries, eg:
```
KafkaServer {
  org.apache.kafka.common.security.scram.ScramLoginModule optional
  dynamodb_scram_store_table_name="YourDynamoDBTableName"
  dynamodb_scram_store_region="AWS_REGION"
  // Optionally set a kms key id to encrypt entries using KMS
  dynamodb_scram_store_kms_key_id="9b6af687-dc28-442e-9028-000000000000"
  ;
};
```

The `kafka-dynamodb-store` jar should then be available in the classpath of kafka, for example by adding it to `$KAFKA_HOME/libs`.

The DynamoDB table can be created either running the create-table! function from kafka-dynamodb-store.scram.core or simply with terraform:

```
resource "aws_dynamodb_table" "scram_store" {
  name           = var.dynamodb_table_name
  billing_mode   = "PROVISIONED"
  read_capacity  = 10
  write_capacity = 1
  hash_key       = "username"

  attribute {
    name = "username"
    type = "S"
  }
}
```
