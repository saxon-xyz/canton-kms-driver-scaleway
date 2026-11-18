package com.saxonxyz.canton.kms.scaleway

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode

class HealthCheckSpec extends AnyFlatSpec with Matchers {

  private val config = ScalewayKmsConfig(secretKey = "test", projectId = "11111111-2222-3333-4444-555555555555", region = "fr-par")

  // Scaleway refuses an unscoped key list (403 "action list resource key") to
  // credentials whose policy covers only one project, so the check must name it.
  private val backend = SttpBackendStub.synchronous
    .whenRequestMatches(r =>
      r.uri.path.lastOption.contains("keys") && r.uri.paramsMap.get("project_id").contains(config.projectId))
    .thenRespond("""{"keys":[],"total_count":0}""")
    .whenAnyRequest
    .thenRespond("""{"message":"insufficient permissions"}""", StatusCode.Forbidden)

  "healthCheck" should "scope the key list to the configured project" in {
    new ScalewayKmsClient(config, backend).healthCheck() shouldBe Right(true)
  }

  it should "report a refused check as a failure" in {
    val refusing = SttpBackendStub.synchronous.whenAnyRequest
      .thenRespond("""{"message":"insufficient permissions"}""", StatusCode.Forbidden)
    new ScalewayKmsClient(config, refusing).healthCheck().isLeft shouldBe true
  }
}
