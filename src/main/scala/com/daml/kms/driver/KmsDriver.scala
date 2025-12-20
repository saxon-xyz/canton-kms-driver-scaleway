package com.daml.kms.driver

// Key IDs
sealed trait KeyId {
  def id: String
}

case class SigningKeyId(id: String) extends KeyId
case class EncryptionKeyId(id: String) extends KeyId
case class SymmetricKeyId(id: String) extends KeyId

// Key specs
sealed trait SigningKeySpec
object SigningKeySpec {
  case object EcDsaP256 extends SigningKeySpec
  case object EcDsaP384 extends SigningKeySpec
  case object Rsa2048Sha256 extends SigningKeySpec
}

sealed trait EncryptionKeySpec
object EncryptionKeySpec {
  case object Rsa2048 extends EncryptionKeySpec
}

sealed trait SymmetricKeySpec
object SymmetricKeySpec {
  case object Aes256Gcm extends SymmetricKeySpec
}

// Key pairs
case class SigningKeyPair(keyId: SigningKeyId, publicKey: Array[Byte])
case class EncryptionKeyPair(keyId: EncryptionKeyId, publicKey: Array[Byte])

// Driver health
sealed trait DriverHealth
object DriverHealth {
  case object Healthy extends DriverHealth
  case class Unhealthy(reason: String) extends DriverHealth
}

// Errors
sealed trait KmsDriverError
object KmsDriverError {
  case class InternalError(message: String) extends KmsDriverError
  case class UnsupportedOperation(message: String) extends KmsDriverError
  case class InvalidKey(message: String) extends KmsDriverError
}

// Main driver interface
trait KmsDriver extends AutoCloseable {
  def name: String
  def health(): DriverHealth
  
  // Signing operations
  def generateSigningKeyPair(keyName: String, spec: SigningKeySpec): Either[KmsDriverError, SigningKeyPair]
  def sign(keyId: SigningKeyId, data: Array[Byte], spec: SigningKeySpec): Either[KmsDriverError, Array[Byte]]
  def getPublicKey(keyId: SigningKeyId): Either[KmsDriverError, Array[Byte]]
  
  // Asymmetric encryption operations
  def generateEncryptionKeyPair(keyName: String, spec: EncryptionKeySpec): Either[KmsDriverError, EncryptionKeyPair]
  def decrypt(keyId: EncryptionKeyId, ciphertext: Array[Byte]): Either[KmsDriverError, Array[Byte]]
  
  // Symmetric encryption operations
  def generateSymmetricKey(keyName: String, spec: SymmetricKeySpec): Either[KmsDriverError, SymmetricKeyId]
  def encryptSymmetric(keyId: SymmetricKeyId, plaintext: Array[Byte], associatedData: Option[Array[Byte]]): Either[KmsDriverError, Array[Byte]]
  def decryptSymmetric(keyId: SymmetricKeyId, ciphertext: Array[Byte], associatedData: Option[Array[Byte]]): Either[KmsDriverError, Array[Byte]]
  
  // Key management
  def deleteKey(keyId: KeyId): Either[KmsDriverError, Unit]
}
