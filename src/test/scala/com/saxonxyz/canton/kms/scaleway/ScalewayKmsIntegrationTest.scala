package com.saxonxyz.canton.kms.scaleway

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class ScalewayKmsIntegrationTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  
  private def hasRequiredEnvVars: Boolean = {
    sys.env.contains("SCW_SECRET_KEY") &&
    sys.env.contains("SCW_PROJECT_ID") &&
    sys.env.contains("SCW_REGION")
  }
  
  private var config: Option[ScalewayKmsConfig] = None
  private var client: Option[ScalewayHttpClient] = None
  private var createdKeyIds: List[String] = List.empty
  
  override def beforeAll(): Unit = {
    if (hasRequiredEnvVars) {
      config = Some(ScalewayKmsConfig.fromEnvironment())
      client = config.map(new ScalewayHttpClient(_))
    }
  }
  
  override def afterAll(): Unit = {
    // Clean up created keys
    client.foreach { c =>
      createdKeyIds.foreach { keyId =>
        Try(c.deleteKey(keyId))
      }
      c.close()
    }
  }
  
  private def withClient[T](test: ScalewayHttpClient => T): Unit = {
    if (!hasRequiredEnvVars) {
      info("Skipping test: SCW_SECRET_KEY, SCW_PROJECT_ID, and SCW_REGION environment variables are required")
      pending
    } else {
      client.foreach(test)
    }
  }
  
  "ScalewayHttpClient" should "pass health check" in withClient { c =>
    val result = c.healthCheck()
    result shouldBe a[Right[_, _]]
    result.getOrElse(false) shouldBe true
  }
  
  it should "create a signing key and retrieve its public key" in withClient { c =>
    val keyName = s"test-signing-key-${System.currentTimeMillis()}"
    val createResult = c.createKey(keyName, KeyUsage.AsymmetricSigning, "ECDSA_P256_SHA_256")
    
    createResult shouldBe a[Right[_, _]]
    val key = createResult.getOrElse(fail("Failed to create key"))
    createdKeyIds = key.id :: createdKeyIds
    
    key.usage shouldBe KeyUsage.AsymmetricSigning
    key.algorithm shouldBe "ECDSA_P256_SHA_256"
    key.state shouldBe KeyState.Enabled
    
    // Get public key
    val pubKeyResult = c.getPublicKey(key.id)
    pubKeyResult shouldBe a[Right[_, _]]
    val pubKeyBytes = pubKeyResult.getOrElse(fail("Failed to get public key"))
    pubKeyBytes should not be empty
  }
  
  it should "create a symmetric key and perform encrypt/decrypt operations" in withClient { c =>
    val keyName = s"test-symmetric-key-${System.currentTimeMillis()}"
    val createResult = c.createKey(keyName, KeyUsage.SymmetricEncryption, "AES_256_GCM")
    
    createResult shouldBe a[Right[_, _]]
    val key = createResult.getOrElse(fail("Failed to create key"))
    createdKeyIds = key.id :: createdKeyIds
    
    key.usage shouldBe KeyUsage.SymmetricEncryption
    key.algorithm shouldBe "AES_256_GCM"
    
    // Encrypt some data
    val plaintext = "Hello, Scaleway KMS!".getBytes("UTF-8")
    val encryptResult = c.encryptAesGcm(key.id, plaintext, "AES_256_GCM")
    
    encryptResult shouldBe a[Right[_, _]]
    val ciphertext = encryptResult.getOrElse(fail("Failed to encrypt"))
    ciphertext should not be empty
    ciphertext should not equal plaintext
    
    // Decrypt the data
    val decryptResult = c.decryptAesGcm(key.id, ciphertext, "AES_256_GCM")
    decryptResult shouldBe a[Right[_, _]]
    val decrypted = decryptResult.getOrElse(fail("Failed to decrypt"))
    
    new String(decrypted, "UTF-8") shouldBe "Hello, Scaleway KMS!"
  }
  
  it should "create an RSA encryption key" in withClient { c =>
    val keyName = s"test-rsa-key-${System.currentTimeMillis()}"
    val createResult = c.createKey(keyName, KeyUsage.AsymmetricEncryption, "RSA_OAEP_2048_SHA_256")
    
    createResult shouldBe a[Right[_, _]]
    val key = createResult.getOrElse(fail("Failed to create key"))
    createdKeyIds = key.id :: createdKeyIds
    
    key.usage shouldBe KeyUsage.AsymmetricEncryption
    key.algorithm shouldBe "RSA_OAEP_2048_SHA_256"
    key.state shouldBe KeyState.Enabled
  }
  
  it should "check key status" in withClient { c =>
    val keyName = s"test-status-key-${System.currentTimeMillis()}"
    val createResult = c.createKey(keyName, KeyUsage.AsymmetricSigning, "ECDSA_P256_SHA_256")
    
    createResult shouldBe a[Right[_, _]]
    val key = createResult.getOrElse(fail("Failed to create key"))
    createdKeyIds = key.id :: createdKeyIds
    
    val statusResult = c.getKeyStatus(key.id)
    statusResult shouldBe a[Right[_, _]]
    statusResult.getOrElse(fail("Failed to get key status")) shouldBe KeyState.Enabled
  }
}
