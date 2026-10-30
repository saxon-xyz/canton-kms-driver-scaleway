// Canton release the driver API is compiled against. Match the fleet's participant
// (Splice 0.9.x ships Canton 3.6). The v1 driver API is stable within a minor line.
val cantonVersion = "3.6.4"

name := "scaleway-kms-driver"
organization := "com.saxonxyz"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.13.18" // Canton 3.6.4's

// Everything Canton already ships is Provided, so the driver JAR carries only our own
// classes. Bundling a second copy of logback/pureconfig/circe/scala-library onto Canton's
// flat classpath is what broke loading: with the old fat JAR, Canton 3.6.4 cannot even
// initialise logging (logback EmptyStackException) before it gets to the KMS driver.
// Versions are PINNED to what Canton 3.6.4 ships (its third-party-licenses.html and the
// kms-driver-api POM): compiling against pureconfig 0.17.4 while Canton runs 0.14.0 gave
// NoSuchMethodError in the derived config reader. Re-pin on every Canton minor bump.
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client3" %% "core" % "3.11.0" % Provided,
  "io.circe" %% "circe-core" % "0.14.2" % Provided,
  "io.circe" %% "circe-generic" % "0.14.2" % Provided,
  "io.circe" %% "circe-parser" % "0.14.2" % Provided,
  "com.github.pureconfig" %% "pureconfig" % "0.14.0" % Provided,
  "ch.qos.logback" % "logback-classic" % "1.6.3" % Provided,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5" % Provided,

  // The one library Canton does not ship: sttp's circe integration. Bundle it without
  // its transitive deps (sttp core and circe come from Canton).
  ("com.softwaremill.sttp.client3" %% "circe" % "3.11.0").intransitive(),

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

// Canton provides the Scala library too.
assembly / assemblyOption ~= (_.withIncludeScala(false))

// Official Canton KMS Driver API, published on Maven Central (no DA Artifactory needed).
// Provided: the Canton runtime supplies it, along with opentelemetry-api, at load time.
// See canton-network/cf-docs global-synchronizer/reference/kms-driver-guide.mdx.
libraryDependencies += "com.daml" %% "kms-driver-api" % cantonVersion % Provided

// DA's reusable driver test suite (KmsDriverTest / KmsDriverFactoryTest).
libraryDependencies += "com.daml" %% "kms-driver-testing" % cantonVersion % Test

// Don't run the test suites as part of `assembly`; the Scaleway-backed ones need credentials.
assembly / test := {}

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
// (Kept as a safety net; stubs are already excluded from unmanaged compile classpath above.)
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
