# Scaleway KMS Driver Implementation Summary

## Project Structure

```
Scaleway-KMS/
├── build.sbt                           # sbt build configuration with dependencies
├── project/
│   ├── build.properties                # sbt version
│   └── plugins.sbt                     # sbt-assembly plugin
├── src/
│   ├── main/
│   │   ├── scala/
│   │   │   ├── com/digitalasset/canton/  # Canton API stubs
│   │   │   │   ├── crypto/kms/driver/api/v1/
│   │   │   │   │   └── KmsDriverApi.scala
│   │   │   │   ├── logging/
│   │   │   │   │   └── NamedLoggerFactory.scala
│   │   │   │   └── tracing/
│   │   │   │       └── TraceContext.scala
│   │   │   └── com/saxonxyz/canton/kms/scaleway/  # Implementation
│   │   │       ├── ScalewayApiModels.scala
│   │   │       ├── ScalewayHttpClient.scala
│   │   │       ├── ScalewayKmsConfig.scala
│   │   │       ├── ScalewayKmsDriver.scala
│   │   │       └── ScalewayKmsDriverFactory.scala
│   │   └── resources/
│   │       └── META-INF/services/      # SPI service registration
│   │           ├── com.digitalasset.canton.crypto.kms.driver.api.KmsDriverFactory
│   │           └── com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory
│   └── test/
│       └── scala/
│           └── com/saxonxyz/canton/kms/scaleway/
│               └── ScalewayKmsIntegrationTest.scala
├── example-config.conf                 # Example Canton configuration
└── README.md                           # Documentation
```

## Implementation Details

### 1. Build Configuration (build.sbt)

- **Dependencies**:
  - sttp 3.9.1 for HTTP client
  - Circe 0.14.6 for JSON handling
  - PureConfig 0.17.4 for configuration
  - ScalaTest 3.2.17 for testing
  - Scala-logging 3.9.5 and Logback 1.4.11 for logging

- **Assembly Plugin**:
  - sbt-assembly 2.1.5 for creating fat jars
  - Merge strategy configured to handle META-INF/services concatenation
  - Produces: `scaleway-kms-driver-0.1.0.jar` (~27MB)

### 2. Canton KMS Driver API Stubs

Since the Canton KMS Driver API is not published to Maven Central, we created stub implementations:

- **KmsDriverApi.scala**: Defines KmsDriver and KmsDriverFactory traits with all required methods
- **Request/Response models**: All Canton API request/response case classes
- **Key specs**: SigningKeySpec, EncryptionKeySpec with algorithm specifications
- **TraceContext.scala**: Stub for Canton's tracing context
- **NamedLoggerFactory.scala**: Stub for Canton's logger factory

### 3. Scaleway API Models (ScalewayApiModels.scala)

JSON models matching Scaleway's v1alpha1 API with snake_case fields:

- **KeyUsage**: asymmetric_signing, asymmetric_encryption, symmetric_encryption
- **KeyState**: enabled, disabled, deleted
- **Request/Response models**: CreateKeyRequest, SignRequest, EncryptRequest, DecryptRequest, etc.
- All models use Circe for JSON encoding/decoding

### 4. HTTP Client (ScalewayHttpClient.scala)

Implements all required operations using sttp:

- **healthCheck()**: Verifies API connectivity
- **createKey()**: Creates keys for signing/encryption/symmetric operations
- **sign()**: Signs data with client-side hashing (SHA-256/384/512)
- **decryptRsaOaep()**: Decrypts RSA-OAEP ciphertext
- **encryptAesGcm()**: Encrypts data with AES-256-GCM
- **decryptAesGcm()**: Decrypts AES-256-GCM ciphertext
- **getPublicKey()**: Fetches public key and converts PEM to X.509 bytes
- **getKeyStatus()**: Checks key state
- **deleteKey()**: Deletes a key

All methods use X-Auth-Token header for authentication and support configurable timeouts.

### 5. Configuration (ScalewayKmsConfig.scala)

Supports configuration via:
- Environment variables: SCW_SECRET_KEY, SCW_PROJECT_ID, SCW_REGION
- Config file parameters: base-url, region, project-id, secret-key
- Default base URL: https://api.scaleway.com/key-manager/v1alpha1
- Configurable request and connection timeouts

### 6. KMS Driver (ScalewayKmsDriver.scala)

Implements all Canton KmsDriver interface methods:

- **health()**: Returns health status
- **generateSigningKeyPair()**: Creates asymmetric signing keys (ECDSA, RSA-PSS)
- **generateEncryptionKeyPair()**: Creates asymmetric encryption keys (RSA-OAEP)
- **generateSymmetricKey()**: Creates symmetric encryption keys (AES-256-GCM)
- **sign()**: Signs data with specified algorithm
- **decrypt()**: Decrypts RSA-OAEP ciphertext
- **encryptSymmetric()**: Encrypts with AES-256-GCM
- **decryptSymmetric()**: Decrypts AES-256-GCM ciphertext
- **getPublicKey()**: Retrieves public key in X.509 format
- **deleteKeyPair()**: Deletes asymmetric key
- **deleteSymmetricKey()**: Deletes symmetric key

Algorithm mappings:
- ECDSA_P256_SHA_256, ECDSA_P384_SHA_384
- RSA_PSS_2048_SHA_256, RSA_PSS_4096_SHA_256
- RSA_OAEP_2048_SHA_256, RSA_OAEP_4096_SHA_256
- AES_256_GCM

### 7. Driver Factory (ScalewayKmsDriverFactory.scala)

- Name: "scaleway"
- Version: 1
- Creates driver instances from Canton config
- Falls back to environment variables if config params not provided

### 8. SPI Service Registration

Two META-INF/services files for backward compatibility:
- com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory (v1 API)
- com.digitalasset.canton.crypto.kms.driver.api.KmsDriverFactory (legacy API)

Both point to: com.saxonxyz.canton.kms.scaleway.ScalewayKmsDriverFactory

### 9. Integration Tests (ScalewayKmsIntegrationTest.scala)

ScalaTest-based integration tests with environment variable guards:

Test cases:
1. Health check
2. Create signing key + retrieve public key
3. Create symmetric key + encrypt/decrypt operations
4. Create RSA encryption key
5. Check key status

All tests skip when SCW_SECRET_KEY, SCW_PROJECT_ID, or SCW_REGION are not set.
Tests clean up created keys in afterAll().

## Build and Test Results

✅ Compilation successful (no errors, no warnings)
✅ Tests pass (skip without env vars)
✅ Assembly builds successfully (27MB jar)
✅ SPI service files included in jar
✅ All source files properly organized

## Acceptance Criteria Met

- ✅ sbt build with all required dependencies
- ✅ sbt-assembly plugin with merge strategy for fat jar
- ✅ Scaleway KMS driver classes in com.saxonxyz.canton.kms.scaleway
- ✅ JSON models matching Scaleway's snake_case v1alpha1 API
- ✅ HTTP client covering all required operations
- ✅ Algorithm mappings for signing/encryption/symmetric operations
- ✅ X-Auth-Token authentication
- ✅ Configurable baseUrl/region/projectId/timeouts
- ✅ SPI service registration (v1 and legacy)
- ✅ Integration test scaffold with env var guards
- ✅ Tests for health, signing keys, symmetric encryption, RSA operations
- ✅ sbt test passes (skips without env vars)
- ✅ sbt assembly succeeds
- ✅ Code compiles without env vars (tests skip)
