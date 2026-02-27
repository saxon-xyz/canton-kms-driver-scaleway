# Scaleway-KMS

Scaleway Key Management Service (KMS) driver for Canton, implementing the official Canton KMS Driver API v1.

## Features

-  Full implementation of Canton KMS Driver API v1 (`com.digitalasset.canton.crypto.kms.driver.api.v1`)
-  Discoverable by Canton 3.4.11 via Java ServiceLoader
-  Support for Scaleway Key Manager v1alpha1 API
-  Asymmetric signing keys (ECDSA P-256, ECDSA P-384)
-  Asymmetric encryption keys (RSA-2048 OAEP)
-  Symmetric encryption keys (AES-256-GCM)
-  Client-side hashing for signing operations
-  Configurable timeouts and endpoints
-  Integration tests with environment-based configuration

## Prerequisites

### Digital Asset Artifactory Credentials

The official Canton KMS Driver API artifact is hosted on the Digital Asset Artifactory. Credentials are **required** to build; the build will fail clearly with a resolution error if they are not set:

```bash
export DA_JFROG_USER="your-jfrog-username"
export DA_JFROG_TOKEN="your-jfrog-api-token"
```

## Building

Build the project (DA_JFROG_USER and DA_JFROG_TOKEN must be set):

```bash
sbt compile
```

Run tests (unit tests run without Scaleway credentials; integration tests require them):

```bash
export SCW_SECRET_KEY="your-secret-key"
export SCW_PROJECT_ID="your-project-id"
export SCW_REGION="fr-par"  # or nl-ams, pl-waw
sbt test
```

Build fat JAR for use with Canton's `EXTRA_CLASSPATH`:

```bash
sbt clean assembly
```

The assembled JAR will be at:
```
target/scala-2.13/scaleway-kms-driver-0.1.0-SNAPSHOT.jar
```

## Verifying the Assembly JAR

After building, verify the SPI entry is present:

```bash
jar tf target/scala-2.13/scaleway-kms-driver-0.1.0-SNAPSHOT.jar | grep 'META-INF/services/com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory'
```

Expected output:
```
META-INF/services/com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory
```

## ServiceLoader Smoke Test

The smoke test is compiled into the assembly JAR (`src/main/scala/...`). It verifies SPI discovery and surfaces linkage errors (e.g. `NoClassDefFoundError`, `ClassCastException`) with full stack traces and cause chains.

Build and run:

```bash
sbt clean assembly
java -cp target/scala-2.13/scaleway-kms-driver-0.1.0-SNAPSHOT.jar \
     com.saxonxyz.canton.kms.scaleway.ServiceLoaderSmokeTest
```

Expected output:
```
=== Scaleway KMS Driver ServiceLoader Smoke Test ===

Discovered 1 factory/factories:
  - name='scaleway', version=1, class=com.saxonxyz.canton.kms.scaleway.ScalewayKmsDriverFactory

SUCCESS: Found 'scaleway' factory: com.saxonxyz.canton.kms.scaleway.ScalewayKmsDriverFactory
```

## Configuration

### Environment Variables

-  `SCW_SECRET_KEY` - Scaleway secret key (required)
-  `SCW_PROJECT_ID` - Scaleway project ID (required)
-  `SCW_REGION` - Scaleway region (required, e.g., `fr-par`, `nl-ams`, `pl-waw`)
-  `SCW_KMS_DEFAULT_PROTECTED` - Whether to create protected keys by default (default: `true`)

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

### Canton Integration

Configure Canton to load the driver by adding the JAR to `EXTRA_CLASSPATH` and referencing the driver by name `scaleway` in Canton's KMS configuration:

```hocon
canton {
  participants {
    myParticipant {
      crypto {
        kms {
          type = driver
          driver-name = scaleway
          config {
            secret-key = ${SCW_SECRET_KEY}
            project-id = ${SCW_PROJECT_ID}
            region = ${SCW_REGION}
          }
        }
      }
    }
  }
}
```

## SPI Registration

The driver is registered for Java ServiceLoader discovery via:

```
src/main/resources/META-INF/services/com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory
```

Containing:
```
com.saxonxyz.canton.kms.scaleway.ScalewayKmsDriverFactory
```

## Supported Algorithms

### Signing Key Specs

-  `EcP256` - ECDSA P-256 (maps to Scaleway `ec_p256_sha256`)
-  `EcP384` - ECDSA P-384 (maps to Scaleway `ec_p384_sha384`)

### Signing Algorithm Specs

-  `EcDsaSha256` - ECDSA with SHA-256
-  `EcDsaSha384` - ECDSA with SHA-384

### Encryption Key Specs

-  `Rsa2048` - RSA 2048-bit (maps to Scaleway `rsa_oaep_2048_sha256`)

### Encryption Algorithm Specs

-  `RsaEsOaepSha256` - RSA-OAEP with SHA-256

### Symmetric (KMS-managed)

-  AES-256-GCM (used internally by Scaleway KMS)

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
