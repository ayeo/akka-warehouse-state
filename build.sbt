name := "http2"

version := "0.1"

scalaVersion := "2.13.2"

val AkkaVersion = "2.6.9"
val AkkaHttpVersion = "10.2.4"
libraryDependencies ++= Seq(
  "ch.qos.logback"    % "logback-classic" % "1.2.3",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
)