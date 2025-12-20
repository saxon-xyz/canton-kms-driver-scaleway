package com.saxonxyz.canton.kms.scaleway

import com.digitalasset.canton.crypto.kms.driver.api.v1._
import com.digitalasset.canton.tracing.TraceContext
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

class ScalewayKmsDriver(
  config: ScalewayKmsConfig,
  override val name: String = "scaleway-kms"
)(implicit ec: ExecutionContext) extends KmsDriver with LazyLogging {
  
  private val client = new ScalewayHttpClient(config)
  
  override def health(request: HealthRequest)(implicit traceContext: TraceContext): Future[HealthResponse] = {
    Future {
      client.healthCheck() match {
        case Right(_) =>
          HealthResponse(isHealthy = true)
        case Left(error) =>
          logger.warn(s"Health check failed: $error")
          HealthResponse(isHealthy = false)
      }
    }
  }
  
  override def generateSigningKeyPair(
    request: GenerateKeyPairRequest
  )(implicit traceContext: TraceContext): Future[GenerateKeyPairResponse] = {
    Future {
      val (algorithm, scwAlgorithm) = request.keySpec match {
        case spec: SigningKeySpec => mapSigningAlgorithm(spec)
        case _ => throw new IllegalArgumentException("Invalid key spec for signing key pair")
      }
      
      val keyName = s"signing-key-${System.currentTimeMillis()}"
      
      client.createKey(keyName, KeyUsage.AsymmetricSigning, scwAlgorithm) match {
        case Right(key) =>
          GenerateKeyPairResponse(keyId = key.id)
        case Left(error) =>
          throw new RuntimeException(s"Failed to generate signing key pair: $error")
      }
    }
  }
  
  override def generateEncryptionKeyPair(
    request: GenerateKeyPairRequest
  )(implicit traceContext: TraceContext): Future[GenerateKeyPairResponse] = {
    Future {
      val (algorithm, scwAlgorithm) = request.keySpec match {
        case spec: EncryptionKeySpec => mapEncryptionAlgorithm(spec)
        case _ => throw new IllegalArgumentException("Invalid key spec for encryption key pair")
      }
      
      val keyName = s"encryption-key-${System.currentTimeMillis()}"
      
      client.createKey(keyName, KeyUsage.AsymmetricEncryption, scwAlgorithm) match {
        case Right(key) =>
          GenerateKeyPairResponse(keyId = key.id)
        case Left(error) =>
          throw new RuntimeException(s"Failed to generate encryption key pair: $error")
      }
    }
  }
  
  override def generateSymmetricKey(
    request: GenerateSymmetricKeyRequest
  )(implicit traceContext: TraceContext): Future[GenerateSymmetricKeyResponse] = {
    Future {
      val keyName = s"symmetric-key-${System.currentTimeMillis()}"
      val scwAlgorithm = "AES_256_GCM"
      
      client.createKey(keyName, KeyUsage.SymmetricEncryption, scwAlgorithm) match {
        case Right(key) =>
          GenerateSymmetricKeyResponse(keyId = key.id)
        case Left(error) =>
          throw new RuntimeException(s"Failed to generate symmetric key: $error")
      }
    }
  }
  
  override def sign(
    request: SignRequest
  )(implicit traceContext: TraceContext): Future[SignResponse] = {
    Future {
      val scwAlgorithm = mapSigningAlgorithmForSigning(request.signingKeyId, request.signatureScheme)
      
      client.sign(request.signingKeyId, request.data.toArray, scwAlgorithm) match {
        case Right(signature) =>
          SignResponse(signature = signature.toVector)
        case Left(error) =>
          throw new RuntimeException(s"Failed to sign: $error")
      }
    }
  }
  
  override def decrypt(
    request: DecryptRequest
  )(implicit traceContext: TraceContext): Future[DecryptResponse] = {
    Future {
      val scwAlgorithm = "RSA_OAEP"
      
      client.decryptRsaOaep(request.encryptionKeyId, request.ciphertext.toArray, scwAlgorithm) match {
        case Right(plaintext) =>
          DecryptResponse(plaintext = plaintext.toVector)
        case Left(error) =>
          throw new RuntimeException(s"Failed to decrypt: $error")
      }
    }
  }
  
  override def encryptSymmetric(
    request: EncryptSymmetricRequest
  )(implicit traceContext: TraceContext): Future[EncryptSymmetricResponse] = {
    Future {
      val scwAlgorithm = "AES_256_GCM"
      
      client.encryptAesGcm(request.symmetricKeyId, request.plaintext.toArray, scwAlgorithm) match {
        case Right(ciphertext) =>
          EncryptSymmetricResponse(ciphertext = ciphertext.toVector)
        case Left(error) =>
          throw new RuntimeException(s"Failed to encrypt: $error")
      }
    }
  }
  
  override def decryptSymmetric(
    request: DecryptSymmetricRequest
  )(implicit traceContext: TraceContext): Future[DecryptSymmetricResponse] = {
    Future {
      val scwAlgorithm = "AES_256_GCM"
      
      client.decryptAesGcm(request.symmetricKeyId, request.ciphertext.toArray, scwAlgorithm) match {
        case Right(plaintext) =>
          DecryptSymmetricResponse(plaintext = plaintext.toVector)
        case Left(error) =>
          throw new RuntimeException(s"Failed to decrypt: $error")
      }
    }
  }
  
  override def getPublicKey(
    request: GetPublicKeyRequest
  )(implicit traceContext: TraceContext): Future[GetPublicKeyResponse] = {
    Future {
      client.getPublicKey(request.keyId) match {
        case Right(publicKeyBytes) =>
          GetPublicKeyResponse(publicKey = publicKeyBytes.toVector)
        case Left(error) =>
          throw new RuntimeException(s"Failed to get public key: $error")
      }
    }
  }
  
  override def deleteKeyPair(
    request: DeleteKeyPairRequest
  )(implicit traceContext: TraceContext): Future[DeleteKeyPairResponse] = {
    Future {
      client.deleteKey(request.keyId) match {
        case Right(_) =>
          DeleteKeyPairResponse()
        case Left(error) =>
          throw new RuntimeException(s"Failed to delete key pair: $error")
      }
    }
  }
  
  override def deleteSymmetricKey(
    request: DeleteSymmetricKeyRequest
  )(implicit traceContext: TraceContext): Future[DeleteSymmetricKeyResponse] = {
    Future {
      client.deleteKey(request.keyId) match {
        case Right(_) =>
          DeleteSymmetricKeyResponse()
        case Left(error) =>
          throw new RuntimeException(s"Failed to delete symmetric key: $error")
      }
    }
  }
  
  private def mapSigningAlgorithm(keySpec: SigningKeySpec): (String, String) = {
    keySpec match {
      case SigningKeySpec.EcCurve(CryptoKeyFormat.DerX509Spki, EllipticCurve.P256, SigningAlgorithmSpec.EcDsaSha256) =>
        ("EC-P256", "ECDSA_P256_SHA_256")
      case SigningKeySpec.EcCurve(CryptoKeyFormat.DerX509Spki, EllipticCurve.P384, SigningAlgorithmSpec.EcDsaSha384) =>
        ("EC-P384", "ECDSA_P384_SHA_384")
      case SigningKeySpec.Rsa(CryptoKeyFormat.DerX509Spki, 2048, SigningAlgorithmSpec.RsaPssSha256) =>
        ("RSA-2048", "RSA_PSS_2048_SHA_256")
      case SigningKeySpec.Rsa(CryptoKeyFormat.DerX509Spki, 4096, SigningAlgorithmSpec.RsaPssSha256) =>
        ("RSA-4096", "RSA_PSS_4096_SHA_256")
      case _ =>
        // Default to EC P256
        ("EC-P256", "ECDSA_P256_SHA_256")
    }
  }
  
  private def mapEncryptionAlgorithm(keySpec: EncryptionKeySpec): (String, String) = {
    keySpec match {
      case EncryptionKeySpec.Rsa(CryptoKeyFormat.DerX509Spki, 2048, EncryptionAlgorithmSpec.RsaOaepSha256) =>
        ("RSA-2048", "RSA_OAEP_2048_SHA_256")
      case EncryptionKeySpec.Rsa(CryptoKeyFormat.DerX509Spki, 4096, EncryptionAlgorithmSpec.RsaOaepSha256) =>
        ("RSA-4096", "RSA_OAEP_4096_SHA_256")
      case _ =>
        // Default to RSA 2048
        ("RSA-2048", "RSA_OAEP_2048_SHA_256")
    }
  }
  
  private def mapSigningAlgorithmForSigning(keyId: String, scheme: SignatureScheme): String = {
    scheme match {
      case SignatureScheme.EcDsaSha256 => "ECDSA_P256_SHA_256"
      case SignatureScheme.EcDsaSha384 => "ECDSA_P384_SHA_384"
      case SignatureScheme.RsaPssSha256 => "RSA_PSS_2048_SHA_256"
      case _ => "ECDSA_P256_SHA_256" // Default
    }
  }
  
  def close(): Unit = {
    client.close()
  }
}
