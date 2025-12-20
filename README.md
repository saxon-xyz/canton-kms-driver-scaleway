# Scaleway-KMS

A Canton KMS (Key Management Service) driver implementation for Scaleway's Key Manager API.

## Overview

This project implements a Canton KMS driver that integrates with Scaleway's Key Manager service, allowing Canton nodes to use Scaleway KMS for cryptographic operations including:

- Asymmetric signing (ECDSA, RSA-PSS)
- Asymmetric encryption (RSA-OAEP)
- Symmetric encryption (AES-256-GCM)
- Key generation and management
- Public key retrieval

## Features

- **Full Canton KMS Driver API compliance** - Implements the Canton KMS Driver v1 API
- **Scaleway API v1alpha1 support** - Uses Scaleway's documented snake_case JSON models
- **Comprehensive cryptographic operations**:
  - Health check endpoint
  - Key creation for signing, encryption, and symmetric operations
  - Digital signatures with client-side hashing
  - RSA-OAEP decryption
  - AES-256-GCM encryption/decryption with IV/ciphertext/tag handling
  - Public key fetching (PEM → X.509 bytes conversion)
  - Key status checking
  - Key deletion
- **Configurable** - Supports configuration via environment variables or config file
- **SPI Service Registration** - Auto-discoverable via Java ServiceLoader
- **Integration tests** - Comprehensive test suite that runs when environment variables are set

## Building

The project uses sbt for building:

```bash
# Compile the code
sbt compile

# Run tests (skips integration tests if env vars not set)
sbt test

# Build fat jar
sbt assembly
```

The assembly produces a fat jar at `target/scala-2.13/scaleway-kms-driver-0.1.0.jar`.

## Configuration

The driver can be configured via:

1. **Environment Variables** (recommended):
   - `SCW_SECRET_KEY` - Scaleway API secret key (required)
   - `SCW_PROJECT_ID` - Scaleway project ID (required)
   - `SCW_REGION` - Scaleway region (default: "fr-par")

2. **Canton Config File**:
```hocon
canton.participants.participant1.crypto.provider = kms
canton.participants.participant1.crypto.kms {
  type = driver
  name = "scaleway"
  config {
    base-url = "https://api.scaleway.com/key-manager/v1alpha1"
    region = "fr-par"
    project-id = "your-project-id"
    secret-key = "your-secret-key"
  }
}
```

## Usage with Canton

1. Build the fat jar: `sbt assembly`
2. Add the jar to Canton's classpath
3. Configure Canton to use the Scaleway KMS driver (see Configuration above)
4. Start Canton with the configuration

## Testing

Integration tests require Scaleway credentials to be set as environment variables. Without these, tests will be skipped:

```bash
export SCW_SECRET_KEY="your-secret-key"
export SCW_PROJECT_ID="your-project-id"
export SCW_REGION="fr-par"

sbt test
```

## Dependencies

- Canton KMS Driver API (stubbed locally)
- sttp for HTTP client
- Circe for JSON handling
- PureConfig for configuration
- ScalaTest for testing
- Logback for logging

## License

See LICENSE file.
