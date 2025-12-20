package com.saxonxyz.canton.kms.scaleway

import com.saxonxyz.canton.kms.scaleway.ScalewayModels._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.util.Try

class ScalewayKmsIntegrationTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // Minimum expected size for X.509 encoded public keys (typical RSA keys are 200-400 bytes)
  private val MinX509PublicKeySize = 100

  // Check if environment variables are set
  private val secretKey = sys.env.get("SCW_SECRET_KEY")
  private val projectId = sys.env.get("SCW_PROJECT_ID")
  private val region = sys.env.get("SCW_REGION")
  
  private val envVarsPresent = secretKey.isDefined && projectId.isDefined && region.isDefined

  private var config: Option[ScalewayKmsConfig] = None
  private var client: Option[ScalewayKmsClient] = None
  private val createdKeyIds = scala.collection.mutable.ListBuffer[String]()

  override def beforeAll(): Unit = {
    if (envVarsPresent) {
      config = Some(ScalewayKmsConfig.fromEnvironment())
      client = config.map(ScalewayKmsClient(_))
    }
  }

  override def afterAll(): Unit = {
    // Clean up created keys
    if (envVarsPresent) {
      client.foreach { c =>
        createdKeyIds.foreach { keyId =>
          Try(c.deleteKey(keyId))
        }
      }
    }
  }

  def skipIfNoEnv(testName: String)(testFun: => Unit): Unit = {
    if (envVarsPresent) {
      testFun
    } else {
      info(s"Skipping test '$testName' - required environment variables not set (SCW_SECRET_KEY, SCW_PROJECT_ID, SCW_REGION)")
      cancel(s"Required environment variables not set")
    }
  }

  "ScalewayKmsClient" should "perform health check successfully" in skipIfNoEnv("health check") {
    val result = client.get.healthCheck()
    result shouldBe a[Right[_, _]]
    result.map(_ shouldBe true)
  }

  it should "create a signing key and retrieve its public key" in skipIfNoEnv("create signing key") {
    val keyName = s"test-signing-key-${UUID.randomUUID()}"
    
    val createResult = client.get.createKey(
      name = keyName,
      usage = KeyUsage.AsymmetricSigning,
      algorithm = KeyAlgorithm.RsaSignPkcs1Sha256,
      description = Some("Integration test signing key")
    )
    
    createResult shouldBe a[Right[_, _]]
    createResult.foreach { key =>
      createdKeyIds += key.id
      key.name shouldBe keyName
      key.usage shouldBe KeyUsage.AsymmetricSigning
      key.algorithm shouldBe KeyAlgorithm.RsaSignPkcs1Sha256
      key.status shouldBe KeyStatus.Enabled
      
      // Get public key
      val publicKeyResult = client.get.getPublicKey(key.id)
      publicKeyResult shouldBe a[Right[_, _]]
      publicKeyResult.foreach { publicKeyBytes =>
        publicKeyBytes should not be empty
        publicKeyBytes.length should be > MinX509PublicKeySize
      }
    }
  }

  it should "create a symmetric key and perform encryption/decryption" in skipIfNoEnv("symmetric encryption") {
    val keyName = s"test-symmetric-key-${UUID.randomUUID()}"
    
    val createResult = client.get.createKey(
      name = keyName,
      usage = KeyUsage.SymmetricEncryption,
      algorithm = KeyAlgorithm.Aes256Gcm,
      description = Some("Integration test symmetric key")
    )
    
    createResult shouldBe a[Right[_, _]]
    createResult.foreach { key =>
      createdKeyIds += key.id
      key.name shouldBe keyName
      key.usage shouldBe KeyUsage.SymmetricEncryption
      key.algorithm shouldBe KeyAlgorithm.Aes256Gcm
      
      // Encrypt some data
      val plaintext = "Hello, Scaleway KMS!".getBytes("UTF-8")
      val encryptResult = client.get.encrypt(key.id, plaintext)
      
      encryptResult shouldBe a[Right[_, _]]
      encryptResult.foreach { ciphertext =>
        ciphertext should not be empty
        ciphertext should not equal plaintext
        
        // Decrypt the data
        val decryptResult = client.get.decrypt(key.id, ciphertext)
        decryptResult shouldBe a[Right[_, _]]
        decryptResult.foreach { decryptedPlaintext =>
          new String(decryptedPlaintext, "UTF-8") shouldBe "Hello, Scaleway KMS!"
        }
      }
    }
  }

  it should "create an RSA encryption key" in skipIfNoEnv("create RSA encryption key") {
    val keyName = s"test-rsa-key-${UUID.randomUUID()}"
    
    val createResult = client.get.createKey(
      name = keyName,
      usage = KeyUsage.AsymmetricEncryption,
      algorithm = KeyAlgorithm.RsaOaepSha256,
      description = Some("Integration test RSA encryption key")
    )
    
    createResult shouldBe a[Right[_, _]]
    createResult.foreach { key =>
      createdKeyIds += key.id
      key.name shouldBe keyName
      key.usage shouldBe KeyUsage.AsymmetricEncryption
      key.algorithm shouldBe KeyAlgorithm.RsaOaepSha256
      
      // Verify we can get the key details
      val getResult = client.get.getKey(key.id)
      getResult shouldBe a[Right[_, _]]
      getResult.foreach { retrievedKey =>
        retrievedKey.id shouldBe key.id
        retrievedKey.name shouldBe keyName
      }
    }
  }

  it should "retrieve key status" in skipIfNoEnv("get key status") {
    val keyName = s"test-status-key-${UUID.randomUUID()}"
    
    val createResult = client.get.createKey(
      name = keyName,
      usage = KeyUsage.AsymmetricSigning,
      algorithm = KeyAlgorithm.EcdsaSignP256Sha256
    )
    
    createResult shouldBe a[Right[_, _]]
    createResult.foreach { key =>
      createdKeyIds += key.id
      
      val statusResult = client.get.getKeyStatus(key.id)
      statusResult shouldBe a[Right[_, _]]
      statusResult.foreach { status =>
        status shouldBe KeyStatus.Enabled
      }
    }
  }

  "ScalewayKmsDriver" should "perform health check" in skipIfNoEnv("driver health check") {
    val driver = ScalewayKmsDriver(config.get)
    val health = driver.health()
    health shouldBe com.daml.kms.driver.DriverHealth.Healthy
    driver.close()
  }

  it should "generate a signing key pair" in skipIfNoEnv("driver generate signing key") {
    val driver = ScalewayKmsDriver(config.get)
    val keyName = s"test-driver-signing-${UUID.randomUUID()}"
    
    val result = driver.generateSigningKeyPair(
      keyName,
      com.daml.kms.driver.SigningKeySpec.EcDsaP256
    )
    
    result shouldBe a[Right[_, _]]
    result.foreach { keyPair =>
      createdKeyIds += keyPair.keyId.id
      keyPair.publicKey should not be empty
    }
    
    driver.close()
  }

  it should "generate a symmetric key and encrypt/decrypt" in skipIfNoEnv("driver symmetric operations") {
    val driver = ScalewayKmsDriver(config.get)
    val keyName = s"test-driver-symmetric-${UUID.randomUUID()}"
    
    val createResult = driver.generateSymmetricKey(
      keyName,
      com.daml.kms.driver.SymmetricKeySpec.Aes256Gcm
    )
    
    createResult shouldBe a[Right[_, _]]
    createResult.foreach { keyId =>
      createdKeyIds += keyId.id
      
      val plaintext = "Test encryption with driver".getBytes("UTF-8")
      val encryptResult = driver.encryptSymmetric(keyId, plaintext, None)
      
      encryptResult shouldBe a[Right[_, _]]
      encryptResult.foreach { ciphertext =>
        val decryptResult = driver.decryptSymmetric(keyId, ciphertext, None)
        
        decryptResult shouldBe a[Right[_, _]]
        decryptResult.foreach { decrypted =>
          new String(decrypted, "UTF-8") shouldBe "Test encryption with driver"
        }
      }
    }
    
    driver.close()
  }

  "ScalewayKmsDriverFactory" should "create a driver from config map" in skipIfNoEnv("factory create from map") {
    val factory = new ScalewayKmsDriverFactory()
    
    val configMap = Map(
      "secretKey" -> secretKey.get,
      "projectId" -> projectId.get,
      "region" -> region.get
    )
    
    val result = factory.create(configMap)
    result shouldBe a[Right[_, _]]
    result.foreach { driver =>
      driver.name shouldBe "scaleway"
      driver.health() shouldBe com.daml.kms.driver.DriverHealth.Healthy
      driver.close()
    }
  }
}
