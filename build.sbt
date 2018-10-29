name := "HotelAPI"

version := "1.0"

scalaVersion := "2.12.7"

libraryDependencies := Seq(
  "com.google.code.gson" % "gson" % "2.8.5",
  "org.scalaj" %% "scalaj-http" % "2.4.1",
  "com.typesafe.akka" %% "akka-actor" % "2.5.17",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.17" % Test,
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
