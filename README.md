# Scaleway-KMS

Scaleway Key Management Service (KMS) driver for Canton, implementing the Canton KMS Driver API.

## Features

-  Full implementation of Canton KMS Driver API
-  Support for Scaleway Key Manager v1alpha1 API
-  Asymmetric signing keys (RSA, ECDSA)
-  Asymmetric encryption keys (RSA-OAEP)
-  Symmetric encryption keys (AES-256-GCM)
-  Client-side hashing for signing operations
-  Automatic IV/tag handling for AES-GCM operations
-  Configurable timeouts and endpoints
-  Integration tests with environment-based configuration

## Building

Build the project:

```bash
sbt compile
```

Run tests (requires Scaleway credentials):

```bash
export SCW_SECRET_KEY="your-secret-key"
export SCW_PROJECT_ID="your-project-id"
export SCW_REGION="fr-par"  # or nl-ams, pl-waw
sbt test
```

Build fat JAR:

```bash
sbt assembly
```

## Configuration

### Environment Variables

-  `SCW_SECRET_KEY` - Scaleway secret key (required)
-  `SCW_PROJECT_ID` - Scaleway project ID (required)
-  `SCW_REGION` - Scaleway region (required)

### Configuration File

```hocon
scaleway {
  kms {
    secretKey = "your-secret-key"
    projectId = "your-project-id"
    region = "fr-par"
    baseUrl = "https://api.scaleway.com"  # optional
    apiVersion = "v1alpha1"  # optional
    requestTimeout = 30s  # optional
    connectionTimeout = 10s  # optional
    defaultProtected = true  # optional, set false in tests to create unprotected keys
  }
}
```

### Programmatic Configuration

```scala
import com.saxonxyz.canton.kms.scaleway._

// From environment variables
val config = ScalewayKmsConfig.fromEnvironment()

// From config file
val config = ScalewayKmsConfig.load().toOption.get

// Manual configuration
val config = ScalewayKmsConfig(
  secretKey = "your-secret-key",
  projectId = "your-project-id",
  region = "fr-par"
)
```

## Usage

### Using the Driver Factory (SPI)

The driver is automatically registered via Java SPI and can be loaded by Canton:

```scala
import com.daml.kms.driver.KmsDriverFactory
import java.util.ServiceLoader

val factories = ServiceLoader.load(classOf[KmsDriverFactory])
val scwFactory = factories.iterator().asScala.find(_.name == "scaleway")
```

### Direct Usage

```scala
import com.saxonxyz.canton.kms.scaleway._

val config = ScalewayKmsConfig.fromEnvironment()
val driver = ScalewayKmsDriver(config)

// Health check
driver.health()

// Generate signing key
val signingKeyPair = driver.generateSigningKeyPair(
  "my-signing-key",
  com.daml.kms.driver.SigningKeySpec.EcDsaP256
)

// Sign data
val signature = driver.sign(
  signingKeyPair.keyId,
  "Hello, World!".getBytes,
  com.daml.kms.driver.SigningKeySpec.EcDsaP256
)

// Generate symmetric key
val symmetricKey = driver.generateSymmetricKey(
  "my-symmetric-key",
  com.daml.kms.driver.SymmetricKeySpec.Aes256Gcm
)

// Encrypt/Decrypt
val ciphertext = driver.encryptSymmetric(
  symmetricKey,
  "Secret message".getBytes,
  None
)

val plaintext = driver.decryptSymmetric(
  symmetricKey,
  ciphertext,
  None
)

driver.close()
```

## Supported Algorithms

### Signing

-  `rsa_sign_pss_sha_256` - RSA PSS with SHA-256
-  `rsa_sign_pss_sha_384` - RSA PSS with SHA-384
-  `rsa_sign_pss_sha_512` - RSA PSS with SHA-512
-  `rsa_sign_pkcs1_sha_256` - RSA PKCS#1 with SHA-256
-  `rsa_sign_pkcs1_sha_384` - RSA PKCS#1 with SHA-384
-  `rsa_sign_pkcs1_sha_512` - RSA PKCS#1 with SHA-512
-  `ecdsa_sign_p256_sha_256` - ECDSA P-256 with SHA-256
-  `ecdsa_sign_p384_sha_384` - ECDSA P-384 with SHA-384

### Encryption

-  `rsa_oaep_sha_256` - RSA-OAEP with SHA-256
-  `aes_256_gcm` - AES-256-GCM

## Integration Tests

Integration tests require valid Scaleway credentials. Tests will be automatically skipped if environment variables are not set.

```bash
# Skip tests (no credentials)
sbt test

# Run tests with credentials
export SCW_SECRET_KEY="your-secret-key"
export SCW_PROJECT_ID="your-project-id"
export SCW_REGION="fr-par"
sbt test
```

## License

See LICENSE file for details.
