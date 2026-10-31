# Scaleway Key Manager KMS driver for Canton

Scaleway Key Management Service (KMS) driver for Canton, implementing the official Canton KMS Driver API v1.

## Features

-  Full implementation of Canton KMS Driver API v1 (`com.digitalasset.canton.crypto.kms.driver.api.v1`)
-  Discoverable by Canton 3.6 via Java ServiceLoader; passes DA's `kms-driver-testing` suite against real Scaleway
-  Support for Scaleway Key Manager v1alpha1 API
-  Asymmetric signing keys (ECDSA P-256, ECDSA P-384)
-  Asymmetric encryption keys (RSA-2048 OAEP)
-  Symmetric encryption keys (AES-256-GCM)
-  Client-side hashing for signing operations
-  Configurable timeouts and endpoints
-  Integration tests with environment-based configuration

## Prerequisites

None beyond sbt and a JDK. The Canton KMS Driver API is on **Maven Central**
(`com.daml` %% `kms-driver-api`), so no Digital Asset Artifactory credentials are needed.

## Get the driver

Download `canton-kms-driver-scaleway-<version>.jar` and its `.sha256` from
[Releases](../../releases), and check it: `sha256sum -c canton-kms-driver-scaleway-<version>.jar.sha256`.
Use a driver release built for your Canton minor version. Or build it yourself, below.

## Building

Build the project:

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

Build the (thin) driver JAR:

```bash
sbt clean assembly
```

The assembled JAR will be at:
```
target/scala-2.13/canton-kms-driver-scaleway-0.1.0-SNAPSHOT.jar
```

## Verifying the Assembly JAR

After building, verify the SPI entry is present:

```bash
jar tf target/scala-2.13/canton-kms-driver-scaleway-0.1.0-SNAPSHOT.jar | grep 'META-INF/services/com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriverFactory'
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
java -cp target/scala-2.13/canton-kms-driver-scaleway-0.1.0-SNAPSHOT.jar \
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

The JAR is **thin**: it contains only this driver (plus sttp's small circe module). Scala,
logback, pureconfig, circe and sttp core come from Canton at runtime, and `build.sbt` pins
them to the versions Canton 3.6.4 ships. A fat JAR that bundled its own copies broke
Canton's logging at startup and gave `NoSuchMethodError` in the config reader. **Re-pin
the versions in `build.sbt` whenever the fleet moves to a new Canton minor.**

Put the JAR on Canton's class path and select the driver by `name` (not `driver-name`):

```hocon
canton.participants.participant1.crypto.provider = kms
canton.participants.participant1.crypto.kms {
  type = driver
  name = "scaleway"
  config {
    secret-key = ${SCW_SECRET_KEY}
    project-id = ${SCW_PROJECT_ID}
    region = ${SCW_REGION}
  }
}
# Strongly recommended, see "Latency" below.
canton.participants.participant1.crypto.session-signing-keys.enabled = true
```

With the Splice `splice-participant` Helm chart, set it the same way the Canton Foundation's
GCP and AWS KMS examples do:

```yaml
additionalEnvVars:
  - name: ADDITIONAL_CONFIG_SESSION_SIGNING_KEYS
    value: canton.participants.participant.crypto.session-signing-keys.enabled = true
```

### Splice / Kubernetes

No custom image is needed. The Splice participant image's `bin/canton` appends
`EXTRA_CLASSPATH` to Canton's class path, so mount the JAR (about 250 KB, fits in a
ConfigMap) and point that variable at it:

```bash
kubectl -n <namespace> create configmap canton-kms-driver \
  --from-file=driver.jar=canton-kms-driver-scaleway-<version>.jar
kubectl -n <namespace> create secret generic scaleway-kms --from-literal=secretKey=<secret-key>
```

`splice-participant` Helm values. `.Values.kms` is written into Canton's `crypto.kms`
unquoted: quote plain strings inside the YAML string, and leave `${SCW_SECRET_KEY}` unquoted
so HOCON substitutes it from the environment (the secret never enters the values file):

```yaml
kms:
  type: driver
  name: '"scaleway"'
  config:
    secretKey: ${SCW_SECRET_KEY}
    projectId: '"<project-id>"'
    region: '"fr-par"'
additionalEnvVars:
  - name: SCW_SECRET_KEY
    valueFrom:
      secretKeyRef:
        name: scaleway-kms
        key: secretKey
  - name: EXTRA_CLASSPATH
    value: /opt/canton-kms/driver.jar
  - name: ADDITIONAL_CONFIG_SESSION_SIGNING_KEYS
    value: canton.participants.participant.crypto.session-signing-keys.enabled = true
extraVolumes:
  - name: kms-driver
    configMap:
      name: canton-kms-driver
extraVolumeMounts:
  - name: kms-driver
    mountPath: /opt/canton-kms
```

`EXTRA_CLASSPATH` loading was checked with the official Splice 0.9.4 participant image, and
`helm template` of `splice-participant` 0.9.4 renders these values to the intended HOCON. Not
yet installed through the chart in a cluster. A KMS participant must be a fresh participant:
Canton cannot move an existing participant's keys into a KMS.

### Latency

Without session signing keys, every protocol message is signed by a remote call to Scaleway
Key Manager. With them, most messages are signed by a short-lived in-memory key and the KMS
is only called to authorise that key. Measured on a local Canton 3.6.4 network (BFT
sequencer, Scaleway `fr-par`, one warm-up ping then 10 pings, median and range in ms):

| participant1 keys | self-ping | ping to a 2nd participant |
|---|---|---|
| Scaleway KMS, no session signing keys | 2989 (2835–3349) | 2397 (2176–2872) |
| Scaleway KMS + session signing keys | **865 (805–998)** | **843 (820–1053)** |
| Software keys, no KMS | 939 (829–972) | 944 (828–981) |

With session signing keys the KMS participant is as fast as one with software keys. The
trade-off, as the Canton docs note, is that someone with a memory snapshot of the participant
could use a session key for its lifetime. Throughput under sustained load is not measured yet.

Local check against an open-source Canton release:

```bash
java -cp target/scala-2.13/canton-kms-driver-scaleway-0.1.0-SNAPSHOT.jar:canton-open-source-3.6.4/lib/canton-open-source-3.6.4.jar \
  com.digitalasset.canton.CantonCommunityApp daemon -c canton.conf
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

Apache-2.0, see [LICENSE](LICENSE).
