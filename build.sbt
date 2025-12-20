name := "scaleway-kms-driver"

version := "0.1.0"

scalaVersion := "2.13.12"

organization := "com.saxonxyz"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      // HTTP client - sttp with circe backend
      "com.softwaremill.sttp.client3" %% "core" % "3.9.1",
      "com.softwaremill.sttp.client3" %% "circe" % "3.9.1",
      
      // JSON handling
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      
      // Configuration
      "com.github.pureconfig" %% "pureconfig" % "0.17.4",
      
      // Logging
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "ch.qos.logback" % "logback-classic" % "1.4.11",
      
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test
    ),
    
    // Assembly settings
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case "application.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    },
    
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar"
  )
