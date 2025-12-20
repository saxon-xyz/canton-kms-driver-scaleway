package com.saxonxyz.canton.kms.scaleway

import com.daml.kms.driver._
import com.saxonxyz.canton.kms.scaleway.ScalewayModels._
import com.typesafe.scalalogging.LazyLogging

import java.security.PublicKey
import scala.util.{Failure, Success, Try}

class ScalewayKmsDriver(
  config: ScalewayKmsConfig,
  client: ScalewayKmsClient
) extends KmsDriver with LazyLogging {

  override def name: String = "scaleway"

  override def health(): DriverHealth = {
    client.healthCheck() match {
      case Right(_) => DriverHealth.Healthy
      case Left(err) =>
        logger.warn(s"Health check failed: $err")
        DriverHealth.Unhealthy(err)
    }
  }

  override def generateSigningKeyPair(
    keyName: String,
    spec: SigningKeySpec
  ): Either[KmsDriverError, SigningKeyPair] = {
    logger.info(s"Generating signing key pair: $keyName with spec $spec")

    val algorithm = spec match {
      case SigningKeySpec.EcDsaP256 => KeyAlgorithm.EcdsaSignP256Sha256
      case SigningKeySpec.EcDsaP384 => KeyAlgorithm.EcdsaSignP384Sha384
      case SigningKeySpec.Rsa2048Sha256 => KeyAlgorithm.RsaSignPkcs1Sha256
      case _ => return Left(KmsDriverError.UnsupportedOperation(s"Unsupported signing key spec: $spec"))
    }

    client.createKey(keyName, KeyUsage.AsymmetricSigning, algorithm) match {
      case Right(key) =>
        val keyId = SigningKeyId(key.id)
        client.getPublicKey(key.id) match {
          case Right(publicKeyBytes) =>
            Right(SigningKeyPair(keyId, publicKeyBytes))
          case Left(err) =>
            Left(KmsDriverError.InternalError(s"Failed to get public key: $err"))
        }
      case Left(err) =>
        Left(KmsDriverError.InternalError(s"Failed to create signing key: $err"))
    }
  }

  override def sign(
    keyId: SigningKeyId,
    data: Array[Byte],
    spec: SigningKeySpec
  ): Either[KmsDriverError, Array[Byte]] = {
    logger.debug(s"Signing with key: ${keyId.id}")

    val algorithm = spec match {
      case SigningKeySpec.EcDsaP256 => KeyAlgorithm.EcdsaSignP256Sha256
      case SigningKeySpec.EcDsaP384 => KeyAlgorithm.EcdsaSignP384Sha384
      case SigningKeySpec.Rsa2048Sha256 => KeyAlgorithm.RsaSignPkcs1Sha256
      case _ => return Left(KmsDriverError.UnsupportedOperation(s"Unsupported signing key spec: $spec"))
    }

    client.sign(keyId.id, data, algorithm) match {
      case Right(signature) => Right(signature)
      case Left(err) => Left(KmsDriverError.InternalError(s"Failed to sign: $err"))
    }
  }

  override def getPublicKey(keyId: SigningKeyId): Either[KmsDriverError, Array[Byte]] = {
    logger.debug(s"Getting public key for: ${keyId.id}")
    
    client.getPublicKey(keyId.id) match {
      case Right(publicKey) => Right(publicKey)
      case Left(err) => Left(KmsDriverError.InternalError(s"Failed to get public key: $err"))
    }
  }

  override def generateEncryptionKeyPair(
    keyName: String,
    spec: EncryptionKeySpec
  ): Either[KmsDriverError, EncryptionKeyPair] = {
    logger.info(s"Generating encryption key pair: $keyName with spec $spec")

    val algorithm = spec match {
      case EncryptionKeySpec.Rsa2048 => KeyAlgorithm.RsaOaepSha256
      case _ => return Left(KmsDriverError.UnsupportedOperation(s"Unsupported encryption key spec: $spec"))
    }

    client.createKey(keyName, KeyUsage.AsymmetricEncryption, algorithm) match {
      case Right(key) =>
        val keyId = EncryptionKeyId(key.id)
        client.getPublicKey(key.id) match {
          case Right(publicKeyBytes) =>
            Right(EncryptionKeyPair(keyId, publicKeyBytes))
          case Left(err) =>
            Left(KmsDriverError.InternalError(s"Failed to get public key: $err"))
        }
      case Left(err) =>
        Left(KmsDriverError.InternalError(s"Failed to create encryption key: $err"))
    }
  }

  override def decrypt(
    keyId: EncryptionKeyId,
    ciphertext: Array[Byte]
  ): Either[KmsDriverError, Array[Byte]] = {
    logger.debug(s"Decrypting with key: ${keyId.id}")
    
    client.decrypt(keyId.id, ciphertext) match {
      case Right(plaintext) => Right(plaintext)
      case Left(err) => Left(KmsDriverError.InternalError(s"Failed to decrypt: $err"))
    }
  }

  override def generateSymmetricKey(
    keyName: String,
    spec: SymmetricKeySpec
  ): Either[KmsDriverError, SymmetricKeyId] = {
    logger.info(s"Generating symmetric key: $keyName with spec $spec")

    val algorithm = spec match {
      case SymmetricKeySpec.Aes256Gcm => KeyAlgorithm.Aes256Gcm
      case _ => return Left(KmsDriverError.UnsupportedOperation(s"Unsupported symmetric key spec: $spec"))
    }

    client.createKey(keyName, KeyUsage.SymmetricEncryption, algorithm) match {
      case Right(key) => Right(SymmetricKeyId(key.id))
      case Left(err) => Left(KmsDriverError.InternalError(s"Failed to create symmetric key: $err"))
    }
  }

  override def encryptSymmetric(
    keyId: SymmetricKeyId,
    plaintext: Array[Byte],
    associatedData: Option[Array[Byte]]
  ): Either[KmsDriverError, Array[Byte]] = {
    logger.debug(s"Encrypting with symmetric key: ${keyId.id}")
    
    client.encrypt(keyId.id, plaintext, associatedData) match {
      case Right(ciphertext) => Right(ciphertext)
      case Left(err) => Left(KmsDriverError.InternalError(s"Failed to encrypt: $err"))
    }
  }

  override def decryptSymmetric(
    keyId: SymmetricKeyId,
    ciphertext: Array[Byte],
    associatedData: Option[Array[Byte]]
  ): Either[KmsDriverError, Array[Byte]] = {
    logger.debug(s"Decrypting with symmetric key: ${keyId.id}")
    
    // For AES-GCM, the ciphertext contains iv + encrypted data + tag
    // Scaleway API handles this internally, so we just pass the ciphertext
    client.decrypt(keyId.id, ciphertext) match {
      case Right(plaintext) => Right(plaintext)
      case Left(err) => Left(KmsDriverError.InternalError(s"Failed to decrypt: $err"))
    }
  }

  override def deleteKey(keyId: KeyId): Either[KmsDriverError, Unit] = {
    logger.info(s"Deleting key: ${keyId.id}")
    
    val scwKeyId = keyId match {
      case SigningKeyId(id) => id
      case EncryptionKeyId(id) => id
      case SymmetricKeyId(id) => id
      case _ => return Left(KmsDriverError.UnsupportedOperation(s"Unsupported key type"))
    }

    client.deleteKey(scwKeyId) match {
      case Right(_) => Right(())
      case Left(err) => Left(KmsDriverError.InternalError(s"Failed to delete key: $err"))
    }
  }

  override def close(): Unit = {
    logger.info("Closing Scaleway KMS driver")
    // Nothing to clean up as we're using synchronous backend
  }
}

object ScalewayKmsDriver {
  def apply(config: ScalewayKmsConfig): ScalewayKmsDriver = {
    val client = ScalewayKmsClient(config)
    new ScalewayKmsDriver(config, client)
  }
}
