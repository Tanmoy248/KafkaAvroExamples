package com.barber.kafka.avro


import java.util.{Collections, Properties}

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{Serde, Serdes, StringDeserializer}
import org.apache.kafka.streams.kstream.{KStream, Produced}

import scala.concurrent.TimeoutException
import org.apache.kafka.streams.{KafkaStreams, StreamsBuilder, StreamsConfig}

class KafkaDemoAvroStreams(val inputTopic:String, val outputTopic:String) {


  val builder: StreamsBuilder = new StreamsBuilder()
  var streams: Option[KafkaStreams] = None

  val streamsConfiguration: Properties = {
    val p = new Properties()
    p.put(StreamsConfig.APPLICATION_ID_CONFIG, "avro-stream-demo-topic-streams")
    p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    p.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, classOf[StringDeserializer].getCanonicalName)
    p.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, classOf[SpecificAvroSerde[_ <: SpecificRecord]])
    p.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,  "http://localhost:8081")
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    p
  }


  def start() = {

    try {
      Runtime.getRuntime.addShutdownHook(new Thread(() => close()))

      //https://github.com/confluentinc/kafka-streams-examples/blob/4.1.x/src/test/scala/io/confluent/examples/streams/SpecificAvroScalaIntegrationTest.scala

      // Write the input data as-is to the output topic.
      //
      // Normally, because a) we have already configured the correct default serdes for keys and
      // values and b) the types for keys and values are the same for both the input topic and the
      // output topic, we would only need to define:
      //
      //   builder.stream(inputTopic).to(outputTopic);
      //
      // However, in the code below we intentionally override the default serdes in `to()` to
      // demonstrate how you can construct and configure a specific Avro serde manually.
      val stringSerde: Serde[String] = Serdes.String
      val specificAvroSerde: Serde[User] = new SpecificAvroSerde[User]
      // Note how we must manually call `configure()` on this serde to configure the schema registry
      // url.  This is different from the case of setting default serdes (see `streamsConfiguration`
      // above), which will be auto-configured based on the `StreamsConfiguration` instance.
      val isKeySerde: Boolean = false
      specificAvroSerde.configure(
        Collections.singletonMap(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
        "http://localhost:8081"), isKeySerde)

      val stream: KStream[String, User] = builder.stream(inputTopic)
      stream.to(outputTopic, Produced.`with`(stringSerde, specificAvroSerde))
      streams = Some(new KafkaStreams(builder.build(), streamsConfiguration))
      streams.map(_.start())

    }
    catch {
      case timeOutEx: TimeoutException =>
        println("Timeout ")
        false
      case ex: Exception => ex.printStackTrace()
        println("Got error when reading message ")
        false
    }
  }

  def close(): Unit = streams.map(_.close())

}