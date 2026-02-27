package com.saxonxyz.canton.kms.scaleway

import com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory

import scala.jdk.CollectionConverters._

/**
 * ServiceLoader smoke test utility.
 *
 * Included in the assembly JAR so it can be used to verify SPI discovery and
 * diagnose linkage failures (NoClassDefFoundError, ClassCastException, etc.)
 * without requiring a live Canton environment.
 *
 * Build and run:
 *   sbt clean assembly
 *   java -cp target/scala-2.13/scaleway-kms-driver-0.1.0-SNAPSHOT.jar \
 *     com.saxonxyz.canton.kms.scaleway.ServiceLoaderSmokeTest
 */
object ServiceLoaderSmokeTest {

  def main(args: Array[String]): Unit = {
    println("=== Scaleway KMS Driver ServiceLoader Smoke Test ===")
    println()

    val loader = java.util.ServiceLoader.load(classOf[KmsDriverFactory])

    val factories = try {
      loader.iterator().asScala.toList
    } catch {
      case e: java.util.ServiceConfigurationError =>
        println("ERROR: ServiceConfigurationError while loading KmsDriverFactory providers:")
        e.printStackTrace(System.out)
        printCauseChain(e.getCause)
        sys.exit(1)
    }

    if (factories.isEmpty) {
      println("ERROR: No KmsDriverFactory implementations discovered!")
      sys.exit(1)
    }

    val count = factories.size
    println(s"Discovered $count ${if (count == 1) "factory" else "factories"}:")
    factories.foreach { factory =>
      println(s"  - name='${factory.name}', version=${factory.version}, class=${factory.getClass.getName}")
      factory.buildInfo.foreach(info => println(s"    buildInfo: $info"))
    }

    val scalewayFactory = factories.find(_.name == "scaleway")
    scalewayFactory match {
      case Some(f) =>
        println()
        println(s"SUCCESS: Found 'scaleway' factory: ${f.getClass.getName}")
      case None =>
        println()
        println(s"ERROR: No factory with name 'scaleway' found. Found: ${factories.map(_.name).mkString(", ")}")
        sys.exit(1)
    }
  }

  /** Prints the full cause chain of a Throwable for diagnosing linkage errors. */
  private def printCauseChain(cause: Throwable): Unit = {
    var current = cause
    var depth = 1
    while (current != null) {
      println(s"Caused by [depth=$depth]: ${current.getClass.getName}: ${current.getMessage}")
      current.printStackTrace(System.out)
      current = current.getCause
      depth += 1
    }
  }
}
