package com.saxonxyz.canton.kms.scaleway

import com.saxonxyz.canton.kms.scaleway.ScalewayModels._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser._
import io.circe.syntax._
import sttp.client3._
import sttp.client3.circe._

import java.nio.charset.StandardCharsets
import java.security.{KeyFactory, MessageDigest}
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import scala.util.{Failure, Success, Try}

class ScalewayKmsClient(config: ScalewayKmsConfig, backend: SttpBackend[Identity, Any]) extends LazyLogging {

  private val baseUri = uri"${config.apiBaseUrl}"

  private def authHeader: Map[String, String] = Map(
    "X-Auth-Token" -> config.secretKey,
    "Content-Type" -> "application/json"
  )

  /**
   * Health check endpoint
   * Note: Scaleway KMS doesn't have a dedicated health endpoint, so we use 
   * a minimal list operation to verify API connectivity and authentication.
   */
  def healthCheck(): Either[String, Boolean] = {
    logger.debug("Performing health check")
    val request = basicRequest
      .get(uri"$baseUri/keys?page_size=1")
      .headers(authHeader)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        Right(true)
      } else {
        Left(s"Health check failed with status ${response.code}: ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error("Health check failed", e)
        Left(s"Health check failed: ${e.getMessage}")
    }
  }

  /**
   * Create a new key
   */
  def createKey(
    name: String,
    usage: KeyUsage,
    algorithm: KeyAlgorithm,
    description: Option[String] = None,
    tags: Option[List[String]] = None
  ): Either[String, Key] = {
    logger.info(s"Creating key: $name with usage $usage and algorithm $algorithm")
    
    val (isProtected, isUnprotected) =
      if (config.defaultProtected) (Some(true), None) else (None, Some(true))

    val createRequest = CreateKeyRequest(
      project_id = config.projectId,
      name = name,
      usage = Map(usage -> algorithm),
      description = description,
      tags = tags,
      `protected` = isProtected,
      unprotected = isUnprotected
    )

    val request = basicRequest
      .post(uri"$baseUri/keys")
      .headers(authHeader)
      .body(createRequest.asJson.noSpaces)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        decode[Key](response.body) match {
          case Right(key) => 
            logger.info(s"Successfully created key ${key.id}")
            Right(key)
          case Left(err) => 
            Left(s"Failed to parse key response: ${err.getMessage}")
        }
      } else {
        Left(s"Failed to create key: ${response.code} - ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error("Failed to create key", e)
        Left(s"Failed to create key: ${e.getMessage}")
    }
  }

  /**
   * Get key by ID
   */
  def getKey(keyId: String): Either[String, Key] = {
    logger.debug(s"Getting key: $keyId")
    
    val request = basicRequest
      .get(uri"$baseUri/keys/$keyId")
      .headers(authHeader)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        decode[Key](response.body) match {
          case Right(key) => Right(key)
          case Left(err) => Left(s"Failed to parse key response: ${err.getMessage}")
        }
      } else {
        Left(s"Failed to get key: ${response.code} - ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to get key $keyId", e)
        Left(s"Failed to get key: ${e.getMessage}")
    }
  }

  /**
   * Get public key in X.509 format using dedicated endpoint
   */
  def getPublicKey(keyId: String): Either[String, Array[Byte]] = {
    logger.debug(s"Getting public key for: $keyId")
    
    val request = basicRequest
      .get(uri"$baseUri/keys/$keyId/public-key")
      .headers(authHeader)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        decode[PublicKeyResponse](response.body) match {
          case Right(pkResp) =>
            Try {
              // Robust PEM parsing: drop any BEGIN/END lines then Base64-decode body
              val pemBody = pkResp.pem
                .linesIterator
                .filterNot(_.startsWith("-----BEGIN"))
                .filterNot(_.startsWith("-----END"))
                .mkString
                .replaceAll("\\s", "")

              Base64.getDecoder.decode(pemBody)
            } match {
              case Success(bytes) => Right(bytes)
              case Failure(ex) => Left(s"Failed to decode public key: ${ex.getMessage}")
            }
          case Left(err) =>
            Left(s"Failed to parse public key response: ${err.getMessage}")
        }
      } else {
        Left(s"Failed to get public key: ${response.code} - ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to get public key for $keyId", e)
        Left(s"Failed to get public key: ${e.getMessage}")
    }
  }

  /**
   * Sign data with client-side hashing
   */
  def sign(keyId: String, data: Array[Byte], algorithm: KeyAlgorithm): Either[String, Array[Byte]] = {
    logger.debug(s"Signing data with key: $keyId")
    
    // Hash the data client-side
    val hashAlgorithm = ScalewayModels.hashAlgorithmFor(algorithm)
      .getOrElse(return Left(s"Algorithm $algorithm is not a signing algorithm"))

    val hash = Try {
      val md = MessageDigest.getInstance(hashAlgorithm)
      md.digest(data)
    } match {
      case Success(h) => h
      case Failure(ex) => return Left(s"Failed to hash data: ${ex.getMessage}")
    }

    val encodedHash = Base64.getEncoder.encodeToString(hash)
    val signRequest = SignRequest(key_id = keyId, data = encodedHash)

    val request = basicRequest
      .post(uri"$baseUri/keys/$keyId/sign")
      .headers(authHeader)
      .body(signRequest.asJson.noSpaces)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        decode[SignResponse](response.body) match {
          case Right(signResp) =>
            Try(Base64.getDecoder.decode(signResp.signature)) match {
              case Success(sig) => Right(sig)
              case Failure(ex) => Left(s"Failed to decode signature: ${ex.getMessage}")
            }
          case Left(err) => Left(s"Failed to parse sign response: ${err.getMessage}")
        }
      } else {
        Left(s"Failed to sign: ${response.code} - ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to sign with key $keyId", e)
        Left(s"Failed to sign: ${e.getMessage}")
    }
  }

  /**
   * Decrypt data using RSA-OAEP
   */
  def decrypt(keyId: String, ciphertext: Array[Byte]): Either[String, Array[Byte]] = {
    logger.debug(s"Decrypting data with key: $keyId")
    
    val encodedCiphertext = Base64.getEncoder.encodeToString(ciphertext)
    val decryptRequest = DecryptRequest(key_id = keyId, ciphertext = encodedCiphertext)

    val request = basicRequest
      .post(uri"$baseUri/keys/$keyId/decrypt")
      .headers(authHeader)
      .body(decryptRequest.asJson.noSpaces)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        decode[DecryptResponse](response.body) match {
          case Right(decryptResp) =>
            Try(Base64.getDecoder.decode(decryptResp.plaintext)) match {
              case Success(plaintext) => Right(plaintext)
              case Failure(ex) => Left(s"Failed to decode plaintext: ${ex.getMessage}")
            }
          case Left(err) => Left(s"Failed to parse decrypt response: ${err.getMessage}")
        }
      } else {
        Left(s"Failed to decrypt: ${response.code} - ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to decrypt with key $keyId", e)
        Left(s"Failed to decrypt: ${e.getMessage}")
    }
  }

  /**
   * Encrypt data using AES-256-GCM
   * Response contains iv + ciphertext + tag all Base64 encoded together
   */
  def encrypt(keyId: String, plaintext: Array[Byte], additionalData: Option[Array[Byte]] = None): Either[String, Array[Byte]] = {
    logger.debug(s"Encrypting data with key: $keyId")
    
    val encodedPlaintext = Base64.getEncoder.encodeToString(plaintext)
    val encodedAad = additionalData.map(ad => Base64.getEncoder.encodeToString(ad))
    
    val encryptRequest = EncryptRequest(
      key_id = keyId,
      plaintext = encodedPlaintext,
      additional_data = encodedAad
    )

    val request = basicRequest
      .post(uri"$baseUri/keys/$keyId/encrypt")
      .headers(authHeader)
      .body(encryptRequest.asJson.noSpaces)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        decode[EncryptResponse](response.body) match {
          case Right(encryptResp) =>
            Try(Base64.getDecoder.decode(encryptResp.ciphertext)) match {
              case Success(ciphertext) => Right(ciphertext)
              case Failure(ex) => Left(s"Failed to decode ciphertext: ${ex.getMessage}")
            }
          case Left(err) => Left(s"Failed to parse encrypt response: ${err.getMessage}")
        }
      } else {
        Left(s"Failed to encrypt: ${response.code} - ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to encrypt with key $keyId", e)
        Left(s"Failed to encrypt: ${e.getMessage}")
    }
  }

  /**
   * Delete a key
   */
  def deleteKey(keyId: String): Either[String, Unit] = {
    logger.info(s"Deleting key: $keyId")
    
    val request = basicRequest
      .delete(uri"$baseUri/keys/$keyId")
      .headers(authHeader)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)

    try {
      val response = request.send(backend)
      if (response.code.isSuccess) {
        Right(())
      } else {
        Left(s"Failed to delete key: ${response.code} - ${response.body}")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to delete key $keyId", e)
        Left(s"Failed to delete key: ${e.getMessage}")
    }
  }

  /**
   * Get key status
   * Note: Defaults to Enabled if status field is not present in the API response,
   * as newly created keys are enabled by default in Scaleway KMS.
   */
  def getKeyStatus(keyId: String): Either[String, KeyStatus] = {
    logger.debug(s"Getting status for key: $keyId")
    getKey(keyId).map { key =>
      // Default to Enabled if status field is not present
      // This is the default state for newly created keys in Scaleway KMS
      key.status.getOrElse(KeyStatus.Enabled)
    }
  }
}

object ScalewayKmsClient {
  def apply(config: ScalewayKmsConfig): ScalewayKmsClient = {
    val backend = HttpClientSyncBackend()
    new ScalewayKmsClient(config, backend)
  }
}
