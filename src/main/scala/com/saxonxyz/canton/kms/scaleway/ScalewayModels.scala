package com.saxonxyz.canton.kms.scaleway

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

object ScalewayModels {

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
      case RsaSignPssSha256 => Json.fromString("rsa_sign_pss_sha_256")
      case RsaSignPssSha384 => Json.fromString("rsa_sign_pss_sha_384")
      case RsaSignPssSha512 => Json.fromString("rsa_sign_pss_sha_512")
      case RsaSignPkcs1Sha256 => Json.fromString("rsa_sign_pkcs1_sha_256")
      case RsaSignPkcs1Sha384 => Json.fromString("rsa_sign_pkcs1_sha_384")
      case RsaSignPkcs1Sha512 => Json.fromString("rsa_sign_pkcs1_sha_512")
      case EcdsaSignP256Sha256 => Json.fromString("ecdsa_sign_p256_sha_256")
      case EcdsaSignP384Sha384 => Json.fromString("ecdsa_sign_p384_sha_384")
      case RsaOaepSha256 => Json.fromString("rsa_oaep_sha_256")
      case Aes256Gcm => Json.fromString("aes_256_gcm")
    }

    implicit val decoder: Decoder[KeyAlgorithm] = Decoder.decodeString.emap {
      case "rsa_sign_pss_sha_256" => Right(RsaSignPssSha256)
      case "rsa_sign_pss_sha_384" => Right(RsaSignPssSha384)
      case "rsa_sign_pss_sha_512" => Right(RsaSignPssSha512)
      case "rsa_sign_pkcs1_sha_256" => Right(RsaSignPkcs1Sha256)
      case "rsa_sign_pkcs1_sha_384" => Right(RsaSignPkcs1Sha384)
      case "rsa_sign_pkcs1_sha_512" => Right(RsaSignPkcs1Sha512)
      case "ecdsa_sign_p256_sha_256" => Right(EcdsaSignP256Sha256)
      case "ecdsa_sign_p384_sha_384" => Right(EcdsaSignP384Sha384)
      case "rsa_oaep_sha_256" => Right(RsaOaepSha256)
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
    usage: KeyUsage,
    algorithm: KeyAlgorithm,
    description: Option[String] = None,
    tags: Option[List[String]] = None,
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
    implicit val encoder: Encoder[Key] = deriveEncoder[Key]
    implicit val decoder: Decoder[Key] = deriveDecoder[Key]
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
