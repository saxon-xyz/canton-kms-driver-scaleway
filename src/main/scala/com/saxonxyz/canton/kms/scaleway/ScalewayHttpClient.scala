package com.saxonxyz.canton.kms.scaleway

import com.typesafe.scalalogging.LazyLogging
import io.circe.parser._
import io.circe.syntax._
import sttp.client3._
import sttp.model.StatusCode

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64
import scala.util.{Failure, Success, Try}

class ScalewayHttpClient(config: ScalewayKmsConfig) extends LazyLogging {
  
  private val backend = HttpClientSyncBackend()
  
  private def authHeader: (String, String) = "X-Auth-Token" -> config.secretKey
  
  private def buildUrl(path: String): String = {
    s"${config.baseUrl}/regions/${config.region}$path"
  }
  
  def healthCheck(): Either[String, Boolean] = {
    Try {
      val response = basicRequest
        .get(uri"${buildUrl("/keys")}")
        .header(authHeader._1, authHeader._2)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.Ok => Right(true)
        case StatusCode.Unauthorized => Left("Unauthorized: Check your SCW_SECRET_KEY")
        case other => Left(s"Health check failed with status: ${other.code}")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Health check failed: ${ex.getMessage}")
    }
  }
  
  def createKey(name: String, usage: KeyUsage, algorithm: String): Either[String, Key] = {
    val request = CreateKeyRequest(
      project_id = config.projectId,
      name = name,
      usage = usage,
      algorithm = algorithm
    )
    
    Try {
      val response = basicRequest
        .post(uri"${buildUrl("/keys")}")
        .header(authHeader._1, authHeader._2)
        .header("Content-Type", "application/json")
        .body(request.asJson.noSpaces)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.Ok | StatusCode.Created =>
          decode[Key](response.body.getOrElse("{}")) match {
            case Right(key) => Right(key)
            case Left(err) => Left(s"Failed to decode key response: ${err.getMessage}")
          }
        case other =>
          val errorMsg = response.body.fold(identity, identity)
          Left(s"Failed to create key (${other.code}): $errorMsg")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Failed to create key: ${ex.getMessage}")
    }
  }
  
  def sign(keyId: String, data: Array[Byte], algorithm: String): Either[String, Array[Byte]] = {
    // Hash the data client-side
    val hash = algorithm match {
      case algo if algo.contains("SHA256") || algo.contains("SHA_256") =>
        MessageDigest.getInstance("SHA-256").digest(data)
      case algo if algo.contains("SHA384") || algo.contains("SHA_384") =>
        MessageDigest.getInstance("SHA-384").digest(data)
      case algo if algo.contains("SHA512") || algo.contains("SHA_512") =>
        MessageDigest.getInstance("SHA-512").digest(data)
      case _ =>
        MessageDigest.getInstance("SHA-256").digest(data) // Default to SHA-256
    }
    
    val signRequest = SignRequest(
      key_id = keyId,
      message = Base64.getEncoder.encodeToString(hash),
      algorithm = algorithm
    )
    
    Try {
      val response = basicRequest
        .post(uri"${buildUrl("/keys/sign")}")
        .header(authHeader._1, authHeader._2)
        .header("Content-Type", "application/json")
        .body(signRequest.asJson.noSpaces)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.Ok =>
          decode[SignResponse](response.body.getOrElse("{}")) match {
            case Right(signResponse) =>
              Try(Base64.getDecoder.decode(signResponse.signature)) match {
                case Success(sig) => Right(sig)
                case Failure(ex) => Left(s"Failed to decode signature: ${ex.getMessage}")
              }
            case Left(err) => Left(s"Failed to decode sign response: ${err.getMessage}")
          }
        case other =>
          val errorMsg = response.body.fold(identity, identity)
          Left(s"Failed to sign (${other.code}): $errorMsg")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Failed to sign: ${ex.getMessage}")
    }
  }
  
  def decryptRsaOaep(keyId: String, ciphertext: Array[Byte], algorithm: String): Either[String, Array[Byte]] = {
    val decryptRequest = DecryptRequest(
      key_id = keyId,
      ciphertext = Base64.getEncoder.encodeToString(ciphertext),
      algorithm = algorithm
    )
    
    Try {
      val response = basicRequest
        .post(uri"${buildUrl("/keys/decrypt")}")
        .header(authHeader._1, authHeader._2)
        .header("Content-Type", "application/json")
        .body(decryptRequest.asJson.noSpaces)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.Ok =>
          decode[DecryptResponse](response.body.getOrElse("{}")) match {
            case Right(decryptResponse) =>
              Try(Base64.getDecoder.decode(decryptResponse.plaintext)) match {
                case Success(plain) => Right(plain)
                case Failure(ex) => Left(s"Failed to decode plaintext: ${ex.getMessage}")
              }
            case Left(err) => Left(s"Failed to decode decrypt response: ${err.getMessage}")
          }
        case other =>
          val errorMsg = response.body.fold(identity, identity)
          Left(s"Failed to decrypt (${other.code}): $errorMsg")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Failed to decrypt: ${ex.getMessage}")
    }
  }
  
  def encryptAesGcm(keyId: String, plaintext: Array[Byte], algorithm: String): Either[String, Array[Byte]] = {
    val encryptRequest = EncryptRequest(
      key_id = keyId,
      plaintext = Base64.getEncoder.encodeToString(plaintext),
      algorithm = algorithm
    )
    
    Try {
      val response = basicRequest
        .post(uri"${buildUrl("/keys/encrypt")}")
        .header(authHeader._1, authHeader._2)
        .header("Content-Type", "application/json")
        .body(encryptRequest.asJson.noSpaces)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.Ok =>
          decode[EncryptResponse](response.body.getOrElse("{}")) match {
            case Right(encryptResponse) =>
              Try(Base64.getDecoder.decode(encryptResponse.ciphertext)) match {
                case Success(cipher) => Right(cipher)
                case Failure(ex) => Left(s"Failed to decode ciphertext: ${ex.getMessage}")
              }
            case Left(err) => Left(s"Failed to decode encrypt response: ${err.getMessage}")
          }
        case other =>
          val errorMsg = response.body.fold(identity, identity)
          Left(s"Failed to encrypt (${other.code}): $errorMsg")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Failed to encrypt: ${ex.getMessage}")
    }
  }
  
  def decryptAesGcm(keyId: String, ciphertext: Array[Byte], algorithm: String): Either[String, Array[Byte]] = {
    decryptRsaOaep(keyId, ciphertext, algorithm) // Same API endpoint
  }
  
  def getPublicKey(keyId: String): Either[String, Array[Byte]] = {
    Try {
      val response = basicRequest
        .get(uri"${buildUrl(s"/keys/$keyId/public-key")}")
        .header(authHeader._1, authHeader._2)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.Ok =>
          decode[GetPublicKeyResponse](response.body.getOrElse("{}")) match {
            case Right(pubKeyResponse) =>
              // Convert PEM to X.509 bytes
              pemToX509Bytes(pubKeyResponse.public_key) match {
                case Right(bytes) => Right(bytes)
                case Left(err) => Left(s"Failed to convert PEM to X.509: $err")
              }
            case Left(err) => Left(s"Failed to decode public key response: ${err.getMessage}")
          }
        case other =>
          val errorMsg = response.body.fold(identity, identity)
          Left(s"Failed to get public key (${other.code}): $errorMsg")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Failed to get public key: ${ex.getMessage}")
    }
  }
  
  def getKeyStatus(keyId: String): Either[String, KeyState] = {
    Try {
      val response = basicRequest
        .get(uri"${buildUrl(s"/keys/$keyId")}")
        .header(authHeader._1, authHeader._2)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.Ok =>
          decode[Key](response.body.getOrElse("{}")) match {
            case Right(key) => Right(key.state)
            case Left(err) => Left(s"Failed to decode key: ${err.getMessage}")
          }
        case other =>
          val errorMsg = response.body.fold(identity, identity)
          Left(s"Failed to get key status (${other.code}): $errorMsg")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Failed to get key status: ${ex.getMessage}")
    }
  }
  
  def deleteKey(keyId: String): Either[String, Unit] = {
    Try {
      val response = basicRequest
        .delete(uri"${buildUrl(s"/keys/$keyId")}")
        .header(authHeader._1, authHeader._2)
        .readTimeout(config.requestTimeout)
        .send(backend)
      
      response.code match {
        case StatusCode.NoContent | StatusCode.Ok => Right(())
        case other =>
          val errorMsg = response.body.fold(identity, identity)
          Left(s"Failed to delete key (${other.code}): $errorMsg")
      }
    } match {
      case Success(result) => result
      case Failure(ex) => Left(s"Failed to delete key: ${ex.getMessage}")
    }
  }
  
  private def pemToX509Bytes(pem: String): Either[String, Array[Byte]] = {
    Try {
      // Remove PEM headers and decode base64
      val pemContent = pem
        .replaceAll("-----BEGIN PUBLIC KEY-----", "")
        .replaceAll("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s+", "")
      
      Base64.getDecoder.decode(pemContent)
    } match {
      case Success(bytes) => Right(bytes)
      case Failure(ex) => Left(s"Failed to parse PEM: ${ex.getMessage}")
    }
  }
  
  def close(): Unit = {
    backend.close()
  }
}
