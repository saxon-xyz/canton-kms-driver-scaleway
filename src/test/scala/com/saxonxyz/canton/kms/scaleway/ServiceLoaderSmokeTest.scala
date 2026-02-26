package com.saxonxyz.canton.kms.scaleway

import com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory

import scala.jdk.CollectionConverters._

/**
 * ServiceLoader smoke test utility.
 *
 * Run with the assembled fat JAR on the classpath:
 *   java -cp scaleway-kms-driver-0.1.0-SNAPSHOT.jar \
 *     com.saxonxyz.canton.kms.scaleway.ServiceLoaderSmokeTest
 */
object ServiceLoaderSmokeTest {

  def main(args: Array[String]): Unit = {
    println("=== Scaleway KMS Driver ServiceLoader Smoke Test ===")
    println()

    val loader = java.util.ServiceLoader.load(classOf[KmsDriverFactory])
    val factories = loader.iterator().asScala.toList

    if (factories.isEmpty) {
      println("ERROR: No KmsDriverFactory implementations discovered!")
      sys.exit(1)
    }

    println(s"Discovered ${factories.size} factory/factories:")
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
}
