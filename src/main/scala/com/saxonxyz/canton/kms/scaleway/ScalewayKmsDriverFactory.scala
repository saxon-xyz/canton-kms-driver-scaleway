package com.saxonxyz.canton.kms.scaleway

import com.daml.kms.driver.{KmsDriver, KmsDriverFactory}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pureconfig._
import pureconfig.generic.auto._

class ScalewayKmsDriverFactory extends KmsDriverFactory with LazyLogging {

  override def name: String = "scaleway"

  override def create(config: Config): Either[String, KmsDriver] = {
    logger.info("Creating Scaleway KMS driver from config")

    val configSource = ConfigSource.fromConfig(config)
    
    configSource.load[ScalewayKmsConfig] match {
      case Right(scwConfig) =>
        logger.info(s"Successfully loaded Scaleway KMS config for region ${scwConfig.region}")
        Right(ScalewayKmsDriver(scwConfig))
      case Left(failures) =>
        val errorMsg = s"Failed to load Scaleway KMS config: ${failures.prettyPrint()}"
        logger.error(errorMsg)
        Left(errorMsg)
    }
  }

  override def create(configMap: Map[String, String]): Either[String, KmsDriver] = {
    logger.info("Creating Scaleway KMS driver from map")

    try {
      val config = ScalewayKmsConfig(
        secretKey = configMap.getOrElse("secretKey", 
          return Left("Missing required config: secretKey")),
        projectId = configMap.getOrElse("projectId",
          return Left("Missing required config: projectId")),
        region = configMap.getOrElse("region",
          return Left("Missing required config: region")),
        baseUrl = configMap.getOrElse("baseUrl", "https://api.scaleway.com"),
        apiVersion = configMap.getOrElse("apiVersion", "v1alpha1")
      )
      
      logger.info(s"Successfully created Scaleway KMS config for region ${config.region}")
      Right(ScalewayKmsDriver(config))
    } catch {
      case e: Exception =>
        val errorMsg = s"Failed to create Scaleway KMS driver: ${e.getMessage}"
        logger.error(errorMsg, e)
        Left(errorMsg)
    }
  }
}
