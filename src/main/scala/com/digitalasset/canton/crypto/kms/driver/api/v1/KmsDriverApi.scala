package com.digitalasset.canton.crypto.kms.driver.api.v1

import com.digitalasset.canton.logging.NamedLoggerFactory
import com.digitalasset.canton.tracing.TraceContext
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

// KMS Driver Factory trait
trait KmsDriverFactory {
  def name: String
  def version: Int
  def create(config: Config, loggerFactory: NamedLoggerFactory)(implicit ec: ExecutionContext): KmsDriver
}

// KMS Driver trait
trait KmsDriver {
  def name: String
  
  def health(request: HealthRequest)(implicit traceContext: TraceContext): Future[HealthResponse]
  def generateSigningKeyPair(request: GenerateKeyPairRequest)(implicit traceContext: TraceContext): Future[GenerateKeyPairResponse]
  def generateEncryptionKeyPair(request: GenerateKeyPairRequest)(implicit traceContext: TraceContext): Future[GenerateKeyPairResponse]
  def generateSymmetricKey(request: GenerateSymmetricKeyRequest)(implicit traceContext: TraceContext): Future[GenerateSymmetricKeyResponse]
  def sign(request: SignRequest)(implicit traceContext: TraceContext): Future[SignResponse]
  def decrypt(request: DecryptRequest)(implicit traceContext: TraceContext): Future[DecryptResponse]
  def encryptSymmetric(request: EncryptSymmetricRequest)(implicit traceContext: TraceContext): Future[EncryptSymmetricResponse]
  def decryptSymmetric(request: DecryptSymmetricRequest)(implicit traceContext: TraceContext): Future[DecryptSymmetricResponse]
  def getPublicKey(request: GetPublicKeyRequest)(implicit traceContext: TraceContext): Future[GetPublicKeyResponse]
  def deleteKeyPair(request: DeleteKeyPairRequest)(implicit traceContext: TraceContext): Future[DeleteKeyPairResponse]
  def deleteSymmetricKey(request: DeleteSymmetricKeyRequest)(implicit traceContext: TraceContext): Future[DeleteSymmetricKeyResponse]
}

// Request/Response case classes
case class HealthRequest()
case class HealthResponse(isHealthy: Boolean)

case class GenerateKeyPairRequest(keySpec: KeySpec)
case class GenerateKeyPairResponse(keyId: String)

case class GenerateSymmetricKeyRequest(keyLength: Int = 256)
case class GenerateSymmetricKeyResponse(keyId: String)

case class SignRequest(signingKeyId: String, data: Vector[Byte], signatureScheme: SignatureScheme)
case class SignResponse(signature: Vector[Byte])

case class DecryptRequest(encryptionKeyId: String, ciphertext: Vector[Byte])
case class DecryptResponse(plaintext: Vector[Byte])

case class EncryptSymmetricRequest(symmetricKeyId: String, plaintext: Vector[Byte])
case class EncryptSymmetricResponse(ciphertext: Vector[Byte])

case class DecryptSymmetricRequest(symmetricKeyId: String, ciphertext: Vector[Byte])
case class DecryptSymmetricResponse(plaintext: Vector[Byte])

case class GetPublicKeyRequest(keyId: String)
case class GetPublicKeyResponse(publicKey: Vector[Byte])

case class DeleteKeyPairRequest(keyId: String)
case class DeleteKeyPairResponse()

case class DeleteSymmetricKeyRequest(keyId: String)
case class DeleteSymmetricKeyResponse()

// Key specs and algorithms
sealed trait KeySpec

sealed trait SigningKeySpec extends KeySpec
object SigningKeySpec {
  case class EcCurve(format: CryptoKeyFormat, curve: EllipticCurve, algorithm: SigningAlgorithmSpec) extends SigningKeySpec
  case class Rsa(format: CryptoKeyFormat, keySize: Int, algorithm: SigningAlgorithmSpec) extends SigningKeySpec
}

sealed trait EncryptionKeySpec extends KeySpec
object EncryptionKeySpec {
  case class Rsa(format: CryptoKeyFormat, keySize: Int, algorithm: EncryptionAlgorithmSpec) extends EncryptionKeySpec
}

sealed trait CryptoKeyFormat
object CryptoKeyFormat {
  case object DerX509Spki extends CryptoKeyFormat
}

sealed trait EllipticCurve
object EllipticCurve {
  case object P256 extends EllipticCurve
  case object P384 extends EllipticCurve
}

sealed trait SigningAlgorithmSpec
object SigningAlgorithmSpec {
  case object EcDsaSha256 extends SigningAlgorithmSpec
  case object EcDsaSha384 extends SigningAlgorithmSpec
  case object RsaPssSha256 extends SigningAlgorithmSpec
}

sealed trait EncryptionAlgorithmSpec
object EncryptionAlgorithmSpec {
  case object RsaOaepSha256 extends EncryptionAlgorithmSpec
}

sealed trait SignatureScheme
object SignatureScheme {
  case object EcDsaSha256 extends SignatureScheme
  case object EcDsaSha384 extends SignatureScheme
  case object RsaPssSha256 extends SignatureScheme
}
