val cantonVersion = "3.4.11"

name := "scaleway-kms-driver"
organization := "com.saxonxyz"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.13.12"

resolvers += "Digital Asset Artifactory" at "https://digitalasset.jfrog.io/artifactory/canton-kms-driver-api/"

credentials += Credentials(
  "Artifactory Realm",
  "digitalasset.jfrog.io",
  sys.env.getOrElse("DA_JFROG_USER", ""),
  sys.env.getOrElse("DA_JFROG_TOKEN", "")
)

libraryDependencies ++= Seq(
  // OpenTelemetry (required by kms-driver-api; also in local stubs compilation classpath)
  "io.opentelemetry" % "opentelemetry-api" % "1.32.0",

  // HTTP client
  "com.softwaremill.sttp.client3" %% "core" % "3.9.1",
  "com.softwaremill.sttp.client3" %% "circe" % "3.9.1",

  // JSON
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",

  // Configuration
  "com.github.pureconfig" %% "pureconfig" % "0.17.4",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

// Official Canton KMS Driver API dependency.
// When DA_JFROG_TOKEN is set, the official artifact is downloaded from Digital Asset Artifactory
// and used for compilation (in Provided scope, so it is not bundled in the assembly JAR).
// Without credentials, the local stub JAR in lib/ provides the same API for compilation.
libraryDependencies ++= {
  if (sys.env.contains("DA_JFROG_TOKEN"))
    Seq("com.digitalasset.canton" % "kms-driver-api" % cantonVersion % Provided)
  else
    Seq.empty
}

// Assembly configuration
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.endsWith(".SF")) => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.endsWith(".DSA")) => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.endsWith(".RSA")) => MergeStrategy.discard
  case PathList("reference.conf") => MergeStrategy.concat
  case PathList("application.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}

// Exclude the local canton API stubs JAR from assembly.
// The Canton runtime provides the real kms-driver-api on its classpath at load time.
assembly / assemblyExcludedJars := {
  val cp = (assembly / fullClasspath).value
  cp.filter(_.data.getName.startsWith("canton-kms-driver-api-stubs"))
}

assembly / assemblyJarName := s"${name.value}-${version.value}.jar"

// Ensure tests are run before assembly
assembly := (assembly dependsOn Test / test).value

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:higherKinds",
  "-language:implicitConversions"
)
