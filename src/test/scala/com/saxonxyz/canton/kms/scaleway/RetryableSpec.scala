package com.saxonxyz.canton.kms.scaleway

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RetryableSpec extends AnyFlatSpec with Matchers {

  "isRetryable" should "treat auth and permission failures as permanent" in {
    ScalewayKmsDriver.isRetryable("Failed to create key: 401 - {\"message\":\"authentication is denied\"}") shouldBe false
    ScalewayKmsDriver.isRetryable("Failed to create key: 403 - {\"message\":\"insufficient permissions\"}") shouldBe false
    ScalewayKmsDriver.isRetryable("Failed to get key: 404 - {}") shouldBe false
  }

  it should "retry throttling, server errors and transport failures" in {
    ScalewayKmsDriver.isRetryable("Failed to sign: 429 - {}") shouldBe true
    ScalewayKmsDriver.isRetryable("Failed to sign: 503 - {}") shouldBe true
    ScalewayKmsDriver.isRetryable("Failed to sign: Connection refused") shouldBe true
  }
}
