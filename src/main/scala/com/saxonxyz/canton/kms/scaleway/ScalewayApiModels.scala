package com.saxonxyz.canton.kms.scaleway

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

// Scaleway API models matching v1alpha1 with snake_case fields

sealed trait KeyUsage
object KeyUsage {
  case object AsymmetricSigning extends KeyUsage
  case object AsymmetricEncryption extends KeyUsage
  case object SymmetricEncryption extends KeyUsage
  
  implicit val encoder: Encoder[KeyUsage] = Encoder.instance {
    case AsymmetricSigning => "asymmetric_signing".asJson
    case AsymmetricEncryption => "asymmetric_encryption".asJson
    case SymmetricEncryption => "symmetric_encryption".asJson
  }
  
  implicit val decoder: Decoder[KeyUsage] = Decoder.instance { cursor =>
    cursor.as[String].flatMap {
      case "asymmetric_signing" => Right(AsymmetricSigning)
      case "asymmetric_encryption" => Right(AsymmetricEncryption)
      case "symmetric_encryption" => Right(SymmetricEncryption)
      case other => Left(DecodingFailure(s"Unknown key usage: $other", cursor.history))
    }
  }
}

sealed trait KeyState
object KeyState {
  case object Enabled extends KeyState
  case object Disabled extends KeyState
  case object Deleted extends KeyState
  
  implicit val encoder: Encoder[KeyState] = Encoder.instance {
    case Enabled => "enabled".asJson
    case Disabled => "disabled".asJson
    case Deleted => "deleted".asJson
  }
  
  implicit val decoder: Decoder[KeyState] = Decoder.instance { cursor =>
    cursor.as[String].flatMap {
      case "enabled" => Right(Enabled)
      case "disabled" => Right(Disabled)
      case "deleted" => Right(Deleted)
      case other => Left(DecodingFailure(s"Unknown key state: $other", cursor.history))
    }
  }
}

case class CreateKeyRequest(
  project_id: String,
  name: String,
  usage: KeyUsage,
  algorithm: String,
  description: Option[String] = None
)

object CreateKeyRequest {
  implicit val encoder: Encoder[CreateKeyRequest] = deriveEncoder[CreateKeyRequest]
  implicit val decoder: Decoder[CreateKeyRequest] = deriveDecoder[CreateKeyRequest]
}

case class Key(
  id: String,
  name: String,
  project_id: String,
  usage: KeyUsage,
  algorithm: String,
  state: KeyState,
  created_at: String,
  updated_at: String,
  description: Option[String] = None,
  region: String
)

object Key {
  implicit val encoder: Encoder[Key] = deriveEncoder[Key]
  implicit val decoder: Decoder[Key] = deriveDecoder[Key]
}

case class SignRequest(
  key_id: String,
  message: String, // base64-encoded hash
  algorithm: String
)

object SignRequest {
  implicit val encoder: Encoder[SignRequest] = deriveEncoder[SignRequest]
  implicit val decoder: Decoder[SignRequest] = deriveDecoder[SignRequest]
}

case class SignResponse(
  key_id: String,
  signature: String, // base64-encoded
  algorithm: String
)

object SignResponse {
  implicit val encoder: Encoder[SignResponse] = deriveEncoder[SignResponse]
  implicit val decoder: Decoder[SignResponse] = deriveDecoder[SignResponse]
}

case class DecryptRequest(
  key_id: String,
  ciphertext: String, // base64-encoded
  algorithm: String,
  associated_data: Option[String] = None
)

object DecryptRequest {
  implicit val encoder: Encoder[DecryptRequest] = deriveEncoder[DecryptRequest]
  implicit val decoder: Decoder[DecryptRequest] = deriveDecoder[DecryptRequest]
}

case class DecryptResponse(
  key_id: String,
  plaintext: String, // base64-encoded
  algorithm: String
)

object DecryptResponse {
  implicit val encoder: Encoder[DecryptResponse] = deriveEncoder[DecryptResponse]
  implicit val decoder: Decoder[DecryptResponse] = deriveDecoder[DecryptResponse]
}

case class EncryptRequest(
  key_id: String,
  plaintext: String, // base64-encoded
  algorithm: String,
  associated_data: Option[String] = None
)

object EncryptRequest {
  implicit val encoder: Encoder[EncryptRequest] = deriveEncoder[EncryptRequest]
  implicit val decoder: Decoder[EncryptRequest] = deriveDecoder[EncryptRequest]
}

case class EncryptResponse(
  key_id: String,
  ciphertext: String, // base64-encoded
  algorithm: String
)

object EncryptResponse {
  implicit val encoder: Encoder[EncryptResponse] = deriveEncoder[EncryptResponse]
  implicit val decoder: Decoder[EncryptResponse] = deriveDecoder[EncryptResponse]
}

case class GetPublicKeyResponse(
  key_id: String,
  public_key: String // PEM-encoded
)

object GetPublicKeyResponse {
  implicit val encoder: Encoder[GetPublicKeyResponse] = deriveEncoder[GetPublicKeyResponse]
  implicit val decoder: Decoder[GetPublicKeyResponse] = deriveDecoder[GetPublicKeyResponse]
}

case class ErrorResponse(
  message: String,
  `type`: Option[String] = None
)

object ErrorResponse {
  implicit val encoder: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
  implicit val decoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
}
