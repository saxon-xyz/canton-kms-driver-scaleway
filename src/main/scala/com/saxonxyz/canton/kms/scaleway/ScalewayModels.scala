package com.saxonxyz.canton.kms.scaleway

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

object ScalewayModels {

  /**
   * Get the hash algorithm name for a signing algorithm
   */
  def hashAlgorithmFor(algorithm: KeyAlgorithm): Option[String] = algorithm match {
    case KeyAlgorithm.RsaSignPssSha256 | KeyAlgorithm.RsaSignPkcs1Sha256 | KeyAlgorithm.EcdsaSignP256Sha256 => 
      Some("SHA-256")
    case KeyAlgorithm.RsaSignPssSha384 | KeyAlgorithm.RsaSignPkcs1Sha384 | KeyAlgorithm.EcdsaSignP384Sha384 => 
      Some("SHA-384")
    case KeyAlgorithm.RsaSignPssSha512 | KeyAlgorithm.RsaSignPkcs1Sha512 => 
      Some("SHA-512")
    case _ => 
      None
  }

  // Key usages
  sealed trait KeyUsage
  object KeyUsage {
    case object AsymmetricSigning extends KeyUsage
    case object AsymmetricEncryption extends KeyUsage
    case object SymmetricEncryption extends KeyUsage

    implicit val encoder: Encoder[KeyUsage] = Encoder.instance {
      case AsymmetricSigning => Json.fromString("asymmetric_signing")
      case AsymmetricEncryption => Json.fromString("asymmetric_encryption")
      case SymmetricEncryption => Json.fromString("symmetric_encryption")
    }

    implicit val decoder: Decoder[KeyUsage] = Decoder.decodeString.emap {
      case "asymmetric_signing" => Right(AsymmetricSigning)
      case "asymmetric_encryption" => Right(AsymmetricEncryption)
      case "symmetric_encryption" => Right(SymmetricEncryption)
      case other => Left(s"Unknown key usage: $other")
    }

    implicit val keyEncoder: KeyEncoder[KeyUsage] = {
      case AsymmetricSigning => "asymmetric_signing"
      case AsymmetricEncryption => "asymmetric_encryption"
      case SymmetricEncryption => "symmetric_encryption"
    }

    implicit val keyDecoder: KeyDecoder[KeyUsage] = {
      case "asymmetric_signing" => Some(AsymmetricSigning)
      case "asymmetric_encryption" => Some(AsymmetricEncryption)
      case "symmetric_encryption" => Some(SymmetricEncryption)
      case _ => None
    }
  }

  // Key algorithms
  sealed trait KeyAlgorithm
  object KeyAlgorithm {
    // Asymmetric signing
    case object RsaSignPssSha256 extends KeyAlgorithm
    case object RsaSignPssSha384 extends KeyAlgorithm
    case object RsaSignPssSha512 extends KeyAlgorithm
    case object RsaSignPkcs1Sha256 extends KeyAlgorithm
    case object RsaSignPkcs1Sha384 extends KeyAlgorithm
    case object RsaSignPkcs1Sha512 extends KeyAlgorithm
    case object EcdsaSignP256Sha256 extends KeyAlgorithm
    case object EcdsaSignP384Sha384 extends KeyAlgorithm
    
    // Asymmetric encryption
    case object RsaOaepSha256 extends KeyAlgorithm
    
    // Symmetric encryption
    case object Aes256Gcm extends KeyAlgorithm

    implicit val encoder: Encoder[KeyAlgorithm] = Encoder.instance {
      case RsaSignPssSha256 => Json.fromString("rsa_pss_2048_sha256")
      case RsaSignPssSha384 => Json.fromString("rsa_pss_3072_sha384")
      case RsaSignPssSha512 => Json.fromString("rsa_pss_4096_sha512")
      case RsaSignPkcs1Sha256 => Json.fromString("rsa_pkcs1_2048_sha256")
      case RsaSignPkcs1Sha384 => Json.fromString("rsa_pkcs1_3072_sha384")
      case RsaSignPkcs1Sha512 => Json.fromString("rsa_pkcs1_4096_sha512")
      case EcdsaSignP256Sha256 => Json.fromString("ec_p256_sha256")
      case EcdsaSignP384Sha384 => Json.fromString("ec_p384_sha384")
      case RsaOaepSha256 => Json.fromString("rsa_oaep_2048_sha256")
      case Aes256Gcm => Json.fromString("aes_256_gcm")
    }

    implicit val decoder: Decoder[KeyAlgorithm] = Decoder.decodeString.emap {
      case "rsa_pss_2048_sha256" | "rsa_sign_pss_sha_256" =>
        Right(RsaSignPssSha256)
      case "rsa_pss_3072_sha384" | "rsa_sign_pss_sha_384" =>
        Right(RsaSignPssSha384)
      case "rsa_pss_4096_sha512" | "rsa_sign_pss_sha_512" =>
        Right(RsaSignPssSha512)
      case "rsa_pkcs1_2048_sha256" | "rsa_sign_pkcs1_sha_256" =>
        Right(RsaSignPkcs1Sha256)
      case "rsa_pkcs1_3072_sha384" | "rsa_sign_pkcs1_sha_384" =>
        Right(RsaSignPkcs1Sha384)
      case "rsa_pkcs1_4096_sha512" | "rsa_sign_pkcs1_sha_512" =>
        Right(RsaSignPkcs1Sha512)
      case "ec_p256_sha256" | "ecdsa_sign_p256_sha_256" =>
        Right(EcdsaSignP256Sha256)
      case "ec_p384_sha384" | "ecdsa_sign_p384_sha_384" =>
        Right(EcdsaSignP384Sha384)
      case "rsa_oaep_2048_sha256" | "rsa_oaep_sha_256" =>
        Right(RsaOaepSha256) // accepts legacy alias for backwards compatibility
      case "aes_256_gcm" => Right(Aes256Gcm)
      case other => Left(s"Unknown algorithm: $other")
    }
  }

  // Key status
  sealed trait KeyStatus
  object KeyStatus {
    case object Enabled extends KeyStatus
    case object Disabled extends KeyStatus
    case object Destroyed extends KeyStatus

    implicit val encoder: Encoder[KeyStatus] = Encoder.instance {
      case Enabled => Json.fromString("enabled")
      case Disabled => Json.fromString("disabled")
      case Destroyed => Json.fromString("destroyed")
    }

    implicit val decoder: Decoder[KeyStatus] = Decoder.decodeString.emap {
      case "enabled" => Right(Enabled)
      case "disabled" => Right(Disabled)
      case "destroyed" => Right(Destroyed)
      case other => Left(s"Unknown key status: $other")
    }
  }

  // Create key request
  case class CreateKeyRequest(
    project_id: String,
    name: String,
    usage: Map[KeyUsage, KeyAlgorithm],
    description: Option[String] = None,
    tags: Option[List[String]] = None,
    // Note: protected and unprotected are mutually exclusive per Scaleway's API design
    `protected`: Option[Boolean] = None,
    unprotected: Option[Boolean] = None
  )

  object CreateKeyRequest {
    implicit val encoder: Encoder[CreateKeyRequest] = deriveEncoder[CreateKeyRequest]
    implicit val decoder: Decoder[CreateKeyRequest] = deriveDecoder[CreateKeyRequest]
  }

  // Key response
  case class Key(
    id: String,
    project_id: String,
    name: String,
    usage: KeyUsage,
    algorithm: KeyAlgorithm,
    status: KeyStatus,
    created_at: String,
    updated_at: Option[String] = None,
    description: Option[String] = None,
    tags: Option[List[String]] = None,
    `protected`: Option[Boolean] = None,
    public_key: Option[String] = None,
    region: String
  )

  object Key {
    private def decodeUsageAndAlgorithm(c: HCursor): Decoder.Result[(KeyUsage, KeyAlgorithm)] = {
      c.get[Option[KeyAlgorithm]]("algorithm").flatMap {
        case Some(algorithm) =>
          c.downField("usage").as[KeyUsage].map(usage => usage -> algorithm)
        case None =>
          c.downField("usage").as[Map[KeyUsage, KeyAlgorithm]].flatMap {
            case usageMap if usageMap.size == 1 =>
              Right(usageMap.head)
            case usageMap if usageMap.isEmpty =>
              Left(DecodingFailure("usage map cannot be empty", c.history))
            case _ =>
              Left(DecodingFailure("usage map must contain a single entry", c.history))
          }
      }
    }

    implicit val encoder: Encoder[Key] = Encoder.instance { key =>
      Json.obj(
        "id" -> Json.fromString(key.id),
        "project_id" -> Json.fromString(key.project_id),
        "name" -> Json.fromString(key.name),
        "usage" -> Map(key.usage -> key.algorithm).asJson,
        "status" -> key.status.asJson,
        "created_at" -> Json.fromString(key.created_at),
        "updated_at" -> key.updated_at.asJson,
        "description" -> key.description.asJson,
        "tags" -> key.tags.asJson,
        "protected" -> key.`protected`.asJson,
        "public_key" -> key.public_key.asJson,
        "region" -> Json.fromString(key.region)
      )
    }

    implicit val decoder: Decoder[Key] = (c: HCursor) => {
      val usageAndAlgorithm = decodeUsageAndAlgorithm(c)

      for {
        id <- c.get[String]("id")
        projectId <- c.get[String]("project_id")
        name <- c.get[String]("name")
        ua <- usageAndAlgorithm
        status <- c.get[Option[KeyStatus]]("status").map(_.getOrElse(KeyStatus.Enabled))
        createdAt <- c.get[String]("created_at")
        updatedAt <- c.get[Option[String]]("updated_at")
        description <- c.get[Option[String]]("description")
        tags <- c.get[Option[List[String]]]("tags")
        prot <- c.get[Option[Boolean]]("protected")
        publicKey <- c.get[Option[String]]("public_key")
        region <- c.get[String]("region")
      } yield Key(
        id = id,
        project_id = projectId,
        name = name,
        usage = ua._1,
        algorithm = ua._2,
        status = status,
        created_at = createdAt,
        updated_at = updatedAt,
        description = description,
        tags = tags,
        `protected` = prot,
        public_key = publicKey,
        region = region
      )
    }
  }

  // Sign request
  case class SignRequest(
    key_id: String,
    data: String  // Base64 encoded hash
  )

  object SignRequest {
    implicit val encoder: Encoder[SignRequest] = deriveEncoder[SignRequest]
    implicit val decoder: Decoder[SignRequest] = deriveDecoder[SignRequest]
  }

  // Sign response
  case class SignResponse(
    signature: String  // Base64 encoded
  )

  object SignResponse {
    implicit val encoder: Encoder[SignResponse] = deriveEncoder[SignResponse]
    implicit val decoder: Decoder[SignResponse] = deriveDecoder[SignResponse]
  }

  // Decrypt request
  case class DecryptRequest(
    key_id: String,
    ciphertext: String  // Base64 encoded
  )

  object DecryptRequest {
    implicit val encoder: Encoder[DecryptRequest] = deriveEncoder[DecryptRequest]
    implicit val decoder: Decoder[DecryptRequest] = deriveDecoder[DecryptRequest]
  }

  // Decrypt response
  case class DecryptResponse(
    plaintext: String  // Base64 encoded
  )

  object DecryptResponse {
    implicit val encoder: Encoder[DecryptResponse] = deriveEncoder[DecryptResponse]
    implicit val decoder: Decoder[DecryptResponse] = deriveDecoder[DecryptResponse]
  }

  // Encrypt request
  case class EncryptRequest(
    key_id: String,
    plaintext: String,  // Base64 encoded
    additional_data: Option[String] = None  // Base64 encoded AAD for GCM
  )

  object EncryptRequest {
    implicit val encoder: Encoder[EncryptRequest] = deriveEncoder[EncryptRequest]
    implicit val decoder: Decoder[EncryptRequest] = deriveDecoder[EncryptRequest]
  }

  // Encrypt response
  case class EncryptResponse(
    ciphertext: String,  // Base64 encoded (for AES-GCM: iv + ciphertext + tag)
    additional_data: Option[String] = None
  )

  object EncryptResponse {
    implicit val encoder: Encoder[EncryptResponse] = deriveEncoder[EncryptResponse]
    implicit val decoder: Decoder[EncryptResponse] = deriveDecoder[EncryptResponse]
  }

  // List keys response
  case class ListKeysResponse(
    keys: List[Key],
    total_count: Int
  )

  object ListKeysResponse {
    implicit val encoder: Encoder[ListKeysResponse] = deriveEncoder[ListKeysResponse]
    implicit val decoder: Decoder[ListKeysResponse] = deriveDecoder[ListKeysResponse]
  }

  // Error response
  case class ErrorResponse(
    message: String,
    `type`: Option[String] = None,
    fields: Option[Map[String, List[String]]] = None
  )

  object ErrorResponse {
    implicit val encoder: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
    implicit val decoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
  }
}
