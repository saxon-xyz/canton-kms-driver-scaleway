package com.saxonxyz.canton.kms.scaleway

import com.digitalasset.canton.crypto.kms.driver.api.v1.{KmsDriver, KmsDriverFactory}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.Logger
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext

class ScalewayKmsDriverFactory extends KmsDriverFactory with LazyLogging {

  override def name: String = "scaleway"

  override def buildInfo: Option[String] = None

  override type ConfigType = ScalewayKmsConfig

  override type Driver = ScalewayKmsDriver

  override def configReader: ConfigReader[ScalewayKmsConfig] =
    implicitly[ConfigReader[ScalewayKmsConfig]]

  override def configWriter(confidential: Boolean): ConfigWriter[ScalewayKmsConfig] = {
    val base = implicitly[ConfigWriter[ScalewayKmsConfig]]
    if (confidential)
      base.contramap[ScalewayKmsConfig](_.copy(secretKey = "***"))
    else
      base
  }

  override def create(
      config: ScalewayKmsConfig,
      loggerFactory: Class[?] => Logger,
      executionContext: ExecutionContext,
  ): ScalewayKmsDriver = {
    logger.info(s"Creating Scaleway KMS driver for region ${config.region}")
    ScalewayKmsDriver(config, executionContext)
  }
}
