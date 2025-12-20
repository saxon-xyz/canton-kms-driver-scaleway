package com.daml.kms.driver

import com.typesafe.config.Config

/**
 * Factory for creating KMS driver instances.
 * Implementations should be registered via Java SPI in META-INF/services.
 */
trait KmsDriverFactory {
  /**
   * The name of this driver (e.g., "aws", "gcp", "azure", "scaleway")
   */
  def name: String
  
  /**
   * Create a driver from TypeSafe Config
   */
  def create(config: Config): Either[String, KmsDriver]
  
  /**
   * Create a driver from a configuration map
   */
  def create(configMap: Map[String, String]): Either[String, KmsDriver]
}
