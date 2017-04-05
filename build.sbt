name := """seed-stream"""

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.typesafe.akka" %% "akka-stream" % "2.4.17",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.17" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)
