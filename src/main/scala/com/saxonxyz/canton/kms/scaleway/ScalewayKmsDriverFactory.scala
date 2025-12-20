package com.saxonxyz.canton.kms.scaleway

import com.digitalasset.canton.crypto.kms.driver.api.v1.{KmsDriver, KmsDriverFactory}
import com.digitalasset.canton.logging.NamedLoggerFactory
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class ScalewayKmsDriverFactory extends KmsDriverFactory with LazyLogging {
  
  override val name: String = "scaleway"
  
  override def version: Int = 1
  
  override def create(
    config: Config,
    loggerFactory: NamedLoggerFactory
  )(implicit ec: ExecutionContext): KmsDriver = {
    
    logger.info(s"Creating Scaleway KMS driver with config: ${config}")
    
    val kmsConfig = parseConfig(config)
    
    new ScalewayKmsDriver(kmsConfig)
  }
  
  private def parseConfig(config: Config): ScalewayKmsConfig = {
    Try {
      val baseUrl = if (config.hasPath("base-url")) {
        config.getString("base-url")
      } else {
        "https://api.scaleway.com/key-manager/v1alpha1"
      }
      
      val region = if (config.hasPath("region")) {
        config.getString("region")
      } else {
        sys.env.getOrElse("SCW_REGION", "fr-par")
      }
      
      val projectId = if (config.hasPath("project-id")) {
        config.getString("project-id")
      } else {
        sys.env.getOrElse("SCW_PROJECT_ID",
          throw new IllegalArgumentException("project-id not found in config or SCW_PROJECT_ID environment variable"))
      }
      
      val secretKey = if (config.hasPath("secret-key")) {
        config.getString("secret-key")
      } else {
        sys.env.getOrElse("SCW_SECRET_KEY",
          throw new IllegalArgumentException("secret-key not found in config or SCW_SECRET_KEY environment variable"))
      }
      
      ScalewayKmsConfig(
        baseUrl = baseUrl,
        region = region,
        projectId = projectId,
        secretKey = secretKey
      )
    } match {
      case Success(cfg) => cfg
      case Failure(ex) =>
        logger.error(s"Failed to parse Scaleway KMS config: ${ex.getMessage}", ex)
        throw ex
    }
  }
}
