package com.saxonxyz.canton.kms.scaleway

import pureconfig._
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._

import scala.concurrent.duration._

case class ScalewayKmsConfig(
  secretKey: String,
  projectId: String,
  region: String,
  baseUrl: String = "https://api.scaleway.com",
  apiVersion: String = "v1alpha1",
  requestTimeout: FiniteDuration = 30.seconds,
  connectionTimeout: FiniteDuration = 10.seconds
) {
  def apiBaseUrl: String = s"$baseUrl/key-manager/$apiVersion/regions/$region"
}

object ScalewayKmsConfig {
  def load(): Either[ConfigReaderFailures, ScalewayKmsConfig] = {
    ConfigSource.default.at("scaleway.kms").load[ScalewayKmsConfig]
  }

  def fromEnvironment(): ScalewayKmsConfig = {
    val secretKey = sys.env.getOrElse("SCW_SECRET_KEY", 
      throw new IllegalArgumentException("SCW_SECRET_KEY environment variable is required"))
    val projectId = sys.env.getOrElse("SCW_PROJECT_ID",
      throw new IllegalArgumentException("SCW_PROJECT_ID environment variable is required"))
    val region = sys.env.getOrElse("SCW_REGION",
      throw new IllegalArgumentException("SCW_REGION environment variable is required"))
    
    ScalewayKmsConfig(
      secretKey = secretKey,
      projectId = projectId,
      region = region
    )
  }
}
