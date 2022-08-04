ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val akkaVersion = "2.6.19"
lazy val postgresVersion = "42.3.6"
val akkaHttpVersion = "10.2.9"
val scalaTestVersion = "3.2.12"
lazy val leveldbVersion = "0.12"
lazy val leveldbjniVersion = "1.8"
lazy val protobufVersion = "3.21.1"

lazy val root = (project in file("."))
  .settings(
    name := "my-land-backend"
  )

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test,

  "com.typesafe.akka" %% "akka-slf4j" % "2.6.19",
  "ch.qos.logback" % "logback-classic" % "1.2.11",

  "org.postgresql" % "postgresql" % postgresVersion,
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.3",
  // akka streams
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  // akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.github.jwt-scala" %% "jwt-spray-json" % "9.0.2",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  // testing
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion,
  "org.scalacheck" %% "scalacheck" % "1.16.0",
  "com.chuusai" %% "shapeless" % "2.3.9",

  "org.mindrot" % "jbcrypt" % "0.4",

  // Google Protocol Buffers
  "com.google.protobuf" % "protobuf-java"  % protobufVersion,

  // for development

  // local levelDB stores
  "org.iq80.leveldb"            % "leveldb"          % leveldbVersion,
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % leveldbjniVersion,
)
