name := """seed-stream"""

version := "1.0"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.11",
  "com.typesafe.akka" %% "akka-stream" % "2.5.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.11" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
