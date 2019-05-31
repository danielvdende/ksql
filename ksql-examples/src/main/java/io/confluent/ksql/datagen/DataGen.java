/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.datagen;

import com.google.common.collect.ImmutableMap;
import io.confluent.avro.random.generator.Generator;
import io.confluent.ksql.util.KsqlConfig;
import org.apache.commons.cli.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.function.BiConsumer;

public final class DataGen {
  private static Options options = new Options()
    .addOption(Option.builder().type(String.class).longOpt("").hasArg().build())
    .addOption("quickstart", "")
    .addOption(Option.builder().type(String.class).longOpt("schema").hasArg().build())
    .addOption(Option.builder().type(String.class).longOpt("format").build())
    .addOption(Option.builder().type(String.class).longOpt("topic").build())
    .addOption(Option.builder().type(String.class).longOpt("key").build())
    .addOption(Option.builder().type(Integer.class).longOpt("iterations").build())
    .addOption(Option.builder().type(Integer.class).longOpt("maxInterval").build())
    .addOption(Option.builder().type(String.class).longOpt("propertiesFile").build());

//        "usage: DataGen " + newLine
//        + "[help] " + newLine
//        + "[bootstrap-server=<kafka bootstrap server(s)> (defaults to localhost:9092)] " + newLine
//        + "[quickstart=<quickstart preset> (case-insensitive; one of 'orders', 'users', or "
//        + "'pageviews')] " + newLine
//        + "schema=<avro schema file> " + newLine
//        + "[schemaRegistryUrl=<url for Confluent Schema Registry> "
//        + "(defaults to http://localhost:8081)] " + newLine
//        + "format=<message format> (case-insensitive; one of 'avro', 'json', or "
//        + "'delimited') " + newLine
//        + "topic=<kafka topic name> " + newLine
//        + "key=<name of key column> " + newLine
//        + "[iterations=<number of rows> (defaults to 1,000,000)] " + newLine
//        + "[maxInterval=<Max time in ms between rows> (defaults to 500)] " + newLine
//        + "[propertiesFile=<file specifying Kafka client properties>]" + newLine

  private DataGen() {
  }

  public static void main(final String[] args) {

    try {
      run(args);
    } catch (final Arguments.ArgumentParseException exception) {
      System.err.println(exception.getMessage());
      usage();
      System.exit(1);
    } catch (final Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  static void run(final String... args) throws IOException {
    CommandLineParser parser = new DefaultParser();
    try{
      CommandLine cmd = parser.parse(DataGen.options, args);

      if(cmd.hasOption("help")){
        usage();
        return;
      }

      final Generator generator = new Generator(cmd.getOptionValue("schemaFile"), new Random());
      final Properties props = getProperties(cmd);
      // TODO: getProducer needs to get an enum value, not a string
      final DataGenProducer dataProducer = new ProducerFactory().getProducer(cmd.getOptionValue("format"), props);

      dataProducer.populateTopic(
              props,
              generator,
              cmd.getOptionValue("topicName"),
              cmd.getOptionValue("keyName"),
              Integer.parseInt(cmd.getOptionValue("iterations")),
              Integer.parseInt(cmd.getOptionValue("maxInterval"))
      );
    } catch(ParseException e){
      System.err.println("OHNOES");
    }


//    final Arguments arguments = new Arguments.Builder()
//        .parseArgs(args)
//        .build();
//
//    if (arguments.help) {
//      usage();
//      return;
//    }
//
//    final Generator generator = new Generator(arguments.schemaFile, new Random());
//    final Properties props = getProperties(arguments);
//    final DataGenProducer dataProducer = new ProducerFactory().getProducer(arguments.format, props);
//
//    dataProducer.populateTopic(
//        props,
//        generator,
//        arguments.topicName,
//        arguments.keyName,
//        arguments.iterations,
//        arguments.maxInterval
//    );
  }

  static Properties getProperties(final CommandLine cmd) throws IOException {
    final Properties props = new Properties();
    props.put("bootstrap.servers", cmd.getOptionValue("bootstrapServer"));
    props.put("client.id", "KSQLDataGenProducer");
    props.put(KsqlConfig.SCHEMA_REGISTRY_URL_PROPERTY, cmd.getOptionValue("schemaRegistryUrl"));

    if(cmd.hasOption("propertiesFile")){
      try {
        props.load(new FileInputStream(cmd.getOptionValue("propertiesFile")));
      } catch (final Exception e) {
        throw new IllegalArgumentException("File not found: " + cmd.getOptionValue("propertiesFile"), e);
      }
    }
    return props;
  }

  private static void usage() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("DataGen", DataGen.options);
  }

  static class Arguments {

    public enum Format { AVRO, JSON, DELIMITED }

    private final boolean help;
    private final String bootstrapServer;
    private final InputStream schemaFile;
    private final Format format;
    private final String topicName;
    private final String keyName;
    private final int iterations;
    private final long maxInterval;
    private final String schemaRegistryUrl;
    private final InputStream propertiesFile;

    Arguments(
        final boolean help,
        final String bootstrapServer,
        final InputStream schemaFile,
        final Format format,
        final String topicName,
        final String keyName,
        final int iterations,
        final long maxInterval,
        final String schemaRegistryUrl,
        final InputStream propertiesFile
    ) {
      this.help = help;
      this.bootstrapServer = bootstrapServer;
      this.schemaFile = schemaFile;
      this.format = format;
      this.topicName = topicName;
      this.keyName = keyName;
      this.iterations = iterations;
      this.maxInterval = maxInterval;
      this.schemaRegistryUrl = schemaRegistryUrl;
      this.propertiesFile = propertiesFile;
    }

    static class ArgumentParseException extends RuntimeException {

      ArgumentParseException(final String message) {
        super(message);
      }
    }

    private static final class Builder {

      private static final Map<String, BiConsumer<Builder, String>> ARG_HANDLERS =
          ImmutableMap.<String, BiConsumer<Builder, String>>builder()
              .put("quickstart", (builder, argVal) -> builder.quickstart = parseQuickStart(argVal))
              .put("bootstrap-server", (builder, argVal) -> builder.bootstrapServer = argVal)
              .put("schema", (builder, argVal) -> builder.schemaFile = toFileInputStream(argVal))
              .put("format", (builder, argVal) -> builder.format = parseFormat(argVal))
              .put("topic", (builder, argVal) -> builder.topicName = argVal)
              .put("key", (builder, argVal) -> builder.keyName = argVal)
              .put("iterations", (builder, argVal) -> builder.iterations = parseIterations(argVal))
              .put("maxInterval",
                  (builder, argVal) -> builder.maxInterval = parseIterations(argVal))
              .put("schemaRegistryUrl", (builder, argVal) -> builder.schemaRegistryUrl = argVal)
              .put("propertiesFile",
                  (builder, argVal) -> builder.propertiesFile = toFileInputStream(argVal))
              .build();

      private Quickstart quickstart;

      private boolean help;
      private String bootstrapServer;
      private InputStream schemaFile;
      private Format format;
      private String topicName;
      private String keyName;
      private int iterations;
      private long maxInterval;
      private String schemaRegistryUrl;
      private InputStream propertiesFile;

      private Builder() {
        quickstart = null;
        help = false;
        bootstrapServer = "localhost:9092";
        schemaFile = null;
        format = null;
        topicName = null;
        keyName = null;
        iterations = 1000000;
        maxInterval = -1;
        schemaRegistryUrl = "http://localhost:8081";
        propertiesFile = null;
      }

      private enum Quickstart {
        CLICKSTREAM_CODES("clickstream_codes_schema.avro", "clickstream", "code"),
        CLICKSTREAM("clickstream_schema.avro", "clickstream", "ip"),
        CLICKSTREAM_USERS("clickstream_users_schema.avro", "webusers", "user_id"),
        ORDERS("orders_schema.avro", "orders", "orderid"),
        RATINGS("ratings_schema.avro", "ratings", "rating_id"),
        USERS("users_schema.avro", "users", "userid"),
        USERS_("users_array_map_schema.avro", "users", "userid"),
        PAGEVIEWS("pageviews_schema.avro", "pageviews", "viewtime");

        private final String schemaFileName;
        private final String rootTopicName;
        private final String keyName;

        Quickstart(final String schemaFileName, final String rootTopicName, final String keyName) {
          this.schemaFileName = schemaFileName;
          this.rootTopicName = rootTopicName;
          this.keyName = keyName;
        }

        public InputStream getSchemaFile() {
          return getClass().getClassLoader().getResourceAsStream(schemaFileName);
        }

        public String getTopicName(final Format format) {
          return String.format("%s_kafka_topic_%s", rootTopicName, format.name().toLowerCase());
        }

        public String getKeyName() {
          return keyName;
        }

        public Format getFormat() {
          return Format.JSON;
        }

      }

      Arguments build() {
        if (quickstart != null) {
          schemaFile = Optional.ofNullable(schemaFile).orElse(quickstart.getSchemaFile());
          format = Optional.ofNullable(format).orElse(quickstart.getFormat());
          topicName = Optional.ofNullable(topicName).orElse(quickstart.getTopicName(format));
          keyName = Optional.ofNullable(keyName).orElse(quickstart.getKeyName());
        }

        try {
          Objects.requireNonNull(schemaFile, "Schema file not provided");
          Objects.requireNonNull(format, "Message format not provided");
          Objects.requireNonNull(topicName, "Kafka topic name not provided");
          Objects.requireNonNull(keyName, "Name of key column not provided");
        } catch (final NullPointerException exception) {
          throw new ArgumentParseException(exception.getMessage());
        }
        return new Arguments(
            help,
            bootstrapServer,
            schemaFile,
            format,
            topicName,
            keyName,
            iterations,
            maxInterval,
            schemaRegistryUrl,
            propertiesFile
        );
      }

      Builder parseArgs(final String[] args) throws IOException {
        for (final String arg : args) {
          parseArg(arg);
        }
        return this;
      }

      // CHECKSTYLE_RULES.OFF: CyclomaticComplexity
      private Builder parseArg(final String arg) throws IOException {
        // CHECKSTYLE_RULES.ON: CyclomaticComplexity
        if ("help".equals(arg) || "--help".equals(arg)) {
          help = true;
          return this;
        }

        final String[] splitOnEquals = arg.split("=");
        if (splitOnEquals.length != 2) {
          throw new ArgumentParseException(String.format(
              "Invalid argument format in '%s'; expected <name>=<value>",
              arg
          ));
        }

        final String argName = splitOnEquals[0].trim();
        final String argValue = splitOnEquals[1].trim();

        if (argName.isEmpty()) {
          throw new ArgumentParseException(String.format(
              "Empty argument name in %s",
              arg
          ));
        }

        if (argValue.isEmpty()) {
          throw new ArgumentParseException(String.format(
              "Empty argument value in '%s'",
              arg
          ));
        }

        setArg(argName, argValue);
        return this;
      }

      private void setArg(final String argName, final String argVal) {
        final BiConsumer<Builder, String> handler = ARG_HANDLERS.get(argName);
        if (handler == null) {
          throw new ArgumentParseException(String.format(
              "Unknown argument name in '%s'",
              argName
          ));
        }

        handler.accept(this, argVal);
      }

      // DANIEL: these will be useful. Perhaps need minor refactoring/reformatting
      private static FileInputStream toFileInputStream(final String argVal) {
        try {
          return new FileInputStream(argVal);
        } catch (final Exception e) {
          throw new IllegalArgumentException("File not found: " + argVal, e);
        }
      }

      private static Quickstart parseQuickStart(final String argValue) {
        try {
          return Quickstart.valueOf(argValue.toUpperCase());
        } catch (final IllegalArgumentException iae) {
          throw new ArgumentParseException(String.format(
              "Invalid quickstart in '%s'; was expecting one of "
              + Arrays.toString(Quickstart.values())
              + " (case-insensitive)",
              argValue
          ));
        }
      }

      private static Format parseFormat(final String formatString) {
        try {
          return Format.valueOf(formatString.toUpperCase());
        } catch (final IllegalArgumentException exception) {
          throw new ArgumentParseException(String.format(
              "Invalid format in '%s'; was expecting one of AVRO, JSON, or DELIMITED "
              + "(case-insensitive)",
              formatString
          ));
        }
      }

      private static int parseIterations(final String iterationsString) {
        try {
          final int result = Integer.valueOf(iterationsString, 10);
          if (result <= 0) {
            throw new ArgumentParseException(String.format(
                "Invalid number of iterations in '%d'; must be a positive number",
                result
            ));
          }
          return Integer.valueOf(iterationsString, 10);
        } catch (final NumberFormatException exception) {
          throw new ArgumentParseException(String.format(
              "Invalid number of iterations in '%s'; must be a valid base 10 integer",
              iterationsString
          ));
        }
      }
    }
  }
}
