package com.saxonxyz.canton.kms.scaleway

import com.digitalasset.canton.crypto.kms.driver.api.v1._
import com.saxonxyz.canton.kms.scaleway.ScalewayKmsDriver.{isRetryable, uniqueKeyName}
import com.saxonxyz.canton.kms.scaleway.ScalewayModels._
import com.typesafe.scalalogging.LazyLogging
import io.opentelemetry.context.Context

import scala.concurrent.{ExecutionContext, Future}

class ScalewayKmsDriver(
  config: ScalewayKmsConfig,
  client: ScalewayKmsClient,
  ec: ExecutionContext
) extends KmsDriver with LazyLogging {

  private implicit val executionContext: ExecutionContext = ec

  override def supportedSigningKeySpecs: Set[SigningKeySpec] =
    Set(SigningKeySpec.EcP256, SigningKeySpec.EcP384)

  override def supportedSigningAlgoSpecs: Set[SigningAlgoSpec] =
    Set(SigningAlgoSpec.EcDsaSha256, SigningAlgoSpec.EcDsaSha384)

  override def supportedEncryptionKeySpecs: Set[EncryptionKeySpec] =
    Set(EncryptionKeySpec.Rsa2048)

  override def supportedEncryptionAlgoSpecs: Set[EncryptionAlgoSpec] =
    Set(EncryptionAlgoSpec.RsaEsOaepSha256)

  override def health: Future[KmsDriverHealth] = Future {
    client.healthCheck() match {
      case Right(_) => KmsDriverHealth.Ok
      case Left(err) =>
        logger.warn(s"Health check failed: $err")
        KmsDriverHealth.Failed(err)
    }
  }

  override def generateSigningKeyPair(
    signingKeySpec: SigningKeySpec,
    keyName: Option[String]
  )(traceContext: Context): Future[String] = Future {
    logger.info(s"Generating signing key pair with spec $signingKeySpec, name=$keyName")

    val algorithm = signingKeySpec match {
      case SigningKeySpec.EcP256 => KeyAlgorithm.EcdsaSignP256Sha256
      case SigningKeySpec.EcP384 => KeyAlgorithm.EcdsaSignP384Sha384
      case unsupported =>
        throw KmsDriverException(
          new IllegalArgumentException(s"Unsupported signing key spec: $unsupported"),
          retryable = false
        )
    }

    val specName = signingKeySpec match {
      case SigningKeySpec.EcP256 => "ec-p256"
      case SigningKeySpec.EcP384 => "ec-p384"
      case other => other.toString.toLowerCase
    }
    val name = uniqueKeyName(keyName, s"scaleway-kms-signing-$specName")
    client.createKey(name, KeyUsage.AsymmetricSigning, algorithm) match {
      case Right(key) =>
        logger.info(s"Successfully generated signing key pair: ${key.id}")
        key.id
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to create signing key: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def generateEncryptionKeyPair(
    encryptionKeySpec: EncryptionKeySpec,
    keyName: Option[String]
  )(traceContext: Context): Future[String] = Future {
    logger.info(s"Generating encryption key pair with spec $encryptionKeySpec, name=$keyName")

    val algorithm = encryptionKeySpec match {
      case EncryptionKeySpec.Rsa2048 => KeyAlgorithm.RsaOaepSha256
      case unsupported =>
        throw KmsDriverException(
          new IllegalArgumentException(s"Unsupported encryption key spec: $unsupported"),
          retryable = false
        )
    }

    val specName = encryptionKeySpec match {
      case EncryptionKeySpec.Rsa2048 => "rsa-2048"
      case other => other.toString.toLowerCase
    }
    val name = uniqueKeyName(keyName, s"scaleway-kms-encryption-$specName")
    client.createKey(name, KeyUsage.AsymmetricEncryption, algorithm) match {
      case Right(key) =>
        logger.info(s"Successfully generated encryption key pair: ${key.id}")
        key.id
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to create encryption key: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def generateSymmetricKey(
    keyName: Option[String]
  )(traceContext: Context): Future[String] = Future {
    logger.info(s"Generating symmetric key, name=$keyName")

    val name = uniqueKeyName(keyName, "scaleway-kms-symmetric")
    client.createKey(name, KeyUsage.SymmetricEncryption, KeyAlgorithm.Aes256Gcm) match {
      case Right(key) =>
        logger.info(s"Successfully generated symmetric key: ${key.id}")
        key.id
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to create symmetric key: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def sign(
    data: Array[Byte],
    keyId: String,
    algoSpec: SigningAlgoSpec
  )(traceContext: Context): Future[Array[Byte]] = Future {
    logger.debug(s"Signing with key: $keyId, algoSpec: $algoSpec")

    val algorithm = algoSpec match {
      case SigningAlgoSpec.EcDsaSha256 => KeyAlgorithm.EcdsaSignP256Sha256
      case SigningAlgoSpec.EcDsaSha384 => KeyAlgorithm.EcdsaSignP384Sha384
      case unsupported =>
        throw KmsDriverException(
          new IllegalArgumentException(s"Unsupported signing algorithm spec: $unsupported"),
          retryable = false
        )
    }

    client.sign(keyId, data, algorithm) match {
      case Right(signature) => signature
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to sign: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def decryptAsymmetric(
    ciphertext: Array[Byte],
    keyId: String,
    algoSpec: EncryptionAlgoSpec
  )(traceContext: Context): Future[Array[Byte]] = Future {
    logger.debug(s"Decrypting asymmetrically with key: $keyId, algoSpec: $algoSpec")

    algoSpec match {
      case EncryptionAlgoSpec.RsaEsOaepSha256 => // supported
      case unsupported =>
        throw KmsDriverException(
          new IllegalArgumentException(s"Unsupported encryption algorithm spec: $unsupported"),
          retryable = false
        )
    }

    client.decrypt(keyId, ciphertext) match {
      case Right(plaintext) => plaintext
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to decrypt: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def encryptSymmetric(
    data: Array[Byte],
    keyId: String
  )(traceContext: Context): Future[Array[Byte]] = Future {
    logger.debug(s"Encrypting symmetrically with key: $keyId")

    // Canton v1 KMS Driver API does not expose associated/“additional” data for symmetric encryption,
    // so we always call the client with additionalData = None here.
    client.encrypt(keyId, data, additionalData = None) match {
      case Right(ciphertext) => ciphertext
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to encrypt: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def decryptSymmetric(
    ciphertext: Array[Byte],
    keyId: String
  )(traceContext: Context): Future[Array[Byte]] = Future {
    logger.debug(s"Decrypting symmetrically with key: $keyId")

    client.decrypt(keyId, ciphertext) match {
      case Right(plaintext) => plaintext
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to decrypt: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def getPublicKey(keyId: String)(traceContext: Context): Future[PublicKey] = Future {
    logger.debug(s"Getting public key for: $keyId")

    client.getKey(keyId) match {
      case Right(key) =>
        val keySpec: KeySpec = key.usage match {
          case KeyUsage.AsymmetricSigning =>
            key.algorithm match {
              case KeyAlgorithm.EcdsaSignP256Sha256 => SigningKeySpec.EcP256
              case KeyAlgorithm.EcdsaSignP384Sha384 => SigningKeySpec.EcP384
              case other =>
                throw KmsDriverException(
                  new RuntimeException(s"Unexpected signing algorithm: $other"),
                  retryable = false
                )
            }
          case KeyUsage.AsymmetricEncryption =>
            key.algorithm match {
              case KeyAlgorithm.RsaOaepSha256 => EncryptionKeySpec.Rsa2048
              case other =>
                throw KmsDriverException(
                  new RuntimeException(s"Unexpected encryption algorithm: $other"),
                  retryable = false
                )
            }
          case other =>
            throw KmsDriverException(
              new RuntimeException(s"getPublicKey called on non-asymmetric key with usage: $other"),
              retryable = false
            )
        }
        client.getPublicKey(keyId) match {
          case Right(publicKeyBytes) => PublicKey(publicKeyBytes, keySpec)
          case Left(err) =>
            throw KmsDriverException(
              new RuntimeException(s"Failed to get public key: $err"),
              retryable = isRetryable(err)
            )
        }
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to get key info: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def keyExistsAndIsActive(keyId: String)(traceContext: Context): Future[Unit] = Future {
    logger.debug(s"Checking if key exists and is active: $keyId")

    client.getKeyStatus(keyId) match {
      case Right(KeyStatus.Enabled) => ()
      case Right(status) =>
        throw KmsDriverException(
          new RuntimeException(s"Key $keyId is not active, status: $status"),
          retryable = false
        )
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to get key status: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def deleteKey(keyId: String)(traceContext: Context): Future[Unit] = Future {
    logger.info(s"Deleting key: $keyId")

    client.deleteKey(keyId) match {
      case Right(_) => ()
      case Left(err) =>
        throw KmsDriverException(
          new RuntimeException(s"Failed to delete key: $err"),
          retryable = isRetryable(err)
        )
    }
  }

  override def close(): Unit = {
    logger.info("Closing Scaleway KMS driver")
    // Nothing to clean up as we're using synchronous backend
  }
}

object ScalewayKmsDriver {
  def apply(config: ScalewayKmsConfig, ec: ExecutionContext): ScalewayKmsDriver = {
    val client = ScalewayKmsClient(config)
    new ScalewayKmsDriver(config, client, ec)
  }

  /** Scaleway key names must be unique per project, and Canton may ask for several keys of
    * the same kind (no name, or the same name across rotations). A fixed name made every
    * second request fail with 409 "key name must be unique", so always add a short suffix.
    */
  def uniqueKeyName(requested: Option[String], default: String): String =
    s"${requested.getOrElse(default)}-${java.util.UUID.randomUUID().toString.take(8)}"

  private val HttpStatus = """: (\d{3}) - """.r

  /** Client errors carry the HTTP status as "...: <code> - <body>". A 4xx other than 429 is
    * permanent (bad credentials, missing IAM permission, unknown key), so Canton must not
    * retry it forever; 429, 5xx and transport errors (no status) stay retryable.
    */
  def isRetryable(err: String): Boolean =
    HttpStatus.findFirstMatchIn(err).map(_.group(1).toInt) match {
      case Some(code) if code >= 400 && code < 500 => code == 429
      case _ => true
    }
}
