package com.saxonxyz.canton.kms.scaleway

import scala.concurrent.duration._

case class ScalewayKmsConfig(
  baseUrl: String = "https://api.scaleway.com/key-manager/v1alpha1",
  region: String,
  projectId: String,
  secretKey: String,
  requestTimeout: FiniteDuration = 30.seconds,
  connectionTimeout: FiniteDuration = 10.seconds
)

object ScalewayKmsConfig {
  def fromEnvironment(): ScalewayKmsConfig = {
    val region = sys.env.getOrElse("SCW_REGION", "fr-par")
    val projectId = sys.env.getOrElse("SCW_PROJECT_ID", 
      throw new IllegalArgumentException("SCW_PROJECT_ID environment variable is required"))
    val secretKey = sys.env.getOrElse("SCW_SECRET_KEY", 
      throw new IllegalArgumentException("SCW_SECRET_KEY environment variable is required"))
    
    ScalewayKmsConfig(
      region = region,
      projectId = projectId,
      secretKey = secretKey
    )
  }
}
