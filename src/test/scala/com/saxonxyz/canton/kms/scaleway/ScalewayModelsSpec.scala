package com.saxonxyz.canton.kms.scaleway

import com.saxonxyz.canton.kms.scaleway.ScalewayModels._
import io.circe.Json
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScalewayModelsSpec extends AnyFlatSpec with Matchers {

  "CreateKeyRequest" should "encode usage as a usage-to-algorithm map without a top-level algorithm field" in {
    val request = CreateKeyRequest(
      project_id = "project",
      name = "key-name",
      usage = Map(KeyUsage.SymmetricEncryption -> KeyAlgorithm.Aes256Gcm),
      description = Some("desc"),
      tags = None,
      `protected` = Some(false),
      unprotected = Some(true)
    )

    val json = request.asJson.hcursor

    json.get[Json]("usage") shouldBe Right(
      Json.obj("symmetric_encryption" -> Json.fromString("aes_256_gcm"))
    )

    json.downField("algorithm").succeeded shouldBe false
  }
}
