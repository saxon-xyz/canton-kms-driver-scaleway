package com.saxonxyz.canton.kms.scaleway

import com.saxonxyz.canton.kms.scaleway.ScalewayModels._
import io.circe.Json
import io.circe.parser.decode
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

  "Key" should "decode usage map responses" in {
    val json =
      """
        |{
        |  "id": "key-1",
        |  "project_id": "project",
        |  "name": "key-name",
        |  "usage": { "asymmetric_signing": "rsa_pkcs1_2048_sha256" },
        |  "status": "enabled",
        |  "created_at": "2024-01-01T00:00:00Z",
        |  "region": "fr-par"
        |}
        |""".stripMargin

    decode[Key](json) shouldBe Right(
      Key(
        id = "key-1",
        project_id = "project",
        name = "key-name",
        usage = KeyUsage.AsymmetricSigning,
        algorithm = KeyAlgorithm.RsaSignPkcs1Sha256,
        status = KeyStatus.Enabled,
        created_at = "2024-01-01T00:00:00Z",
        updated_at = None,
        description = None,
        tags = None,
        `protected` = None,
        public_key = None,
        region = "fr-par"
      )
    )
  }

  it should "decode usage map responses with missing status field (defaulting to Enabled)" in {
    val json =
      """
        |{
        |  "id": "key-2",
        |  "project_id": "project",
        |  "name": "key-name-2",
        |  "usage": { "symmetric_encryption": "aes_256_gcm" },
        |  "created_at": "2024-01-02T00:00:00Z",
        |  "region": "nl-ams"
        |}
        |""".stripMargin

    decode[Key](json) shouldBe Right(
      Key(
        id = "key-2",
        project_id = "project",
        name = "key-name-2",
        usage = KeyUsage.SymmetricEncryption,
        algorithm = KeyAlgorithm.Aes256Gcm,
        status = KeyStatus.Enabled,
        created_at = "2024-01-02T00:00:00Z",
        updated_at = None,
        description = None,
        tags = None,
        `protected` = None,
        public_key = None,
        region = "nl-ams"
      )
    )
  }
}
