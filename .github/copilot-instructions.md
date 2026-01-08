# Copilot Instructions for scaleway-kms

These instructions guide AI coding agents working on this Scaleway KMS driver for Canton.

## Project Overview

-  This repo implements the Canton KMS Driver API for Scaleway Key Manager.
-  The public interface for Canton is `com.daml.kms.driver.KmsDriver` plus the SPI factory `com.daml.kms.driver.KmsDriverFactory`.
-  The Scaleway implementation lives under `com.saxonxyz.canton.kms.scaleway` and talks to Scaleway's Key Manager v1alpha1 over HTTP using sttp and circe.

## Key Components

-  `src/main/scala/com/daml/kms/driver/KmsDriver.scala`: Driver trait, key ID/spec types, and error/health ADTs. Treat this as the canonical API contract.
-  `src/main/scala/com/daml/kms/driver/KmsDriverFactory.scala`: SPI factory interface. Implementations are registered via `META-INF/services/com.daml.kms.driver.KmsDriverFactory`.
-  `src/main/scala/com/saxonxyz/canton/kms/scaleway/ScalewayKmsConfig.scala`: PureConfig-backed config (and `fromEnvironment` helper) plus `apiBaseUrl` derivation; mirrors `reference.conf`.
-  `src/main/resources/reference.conf`: Default HOCON config section `scaleway.kms`, wired to environment variables `SCW_SECRET_KEY`, `SCW_PROJECT_ID`, `SCW_REGION`.
-  `src/main/scala/com/saxonxyz/canton/kms/scaleway/ScalewayModels.scala`: Circe models and enums for Scaleway KMS (key usage, algorithms, status, request/response DTOs, error shapes, hashAlgorithmFor, and backward-compatible decoders).
-  `src/main/scala/com/saxonxyz/canton/kms/scaleway/ScalewayKmsClient.scala`: Low-level HTTP client using sttp; returns `Either[String, T]`, handles JSON (circe), Base64, and client-side hashing.
-  `src/main/scala/com/saxonxyz/canton/kms/scaleway/ScalewayKmsDriver.scala`: Adapts `KmsDriver` to `ScalewayKmsClient`, maps Canton specs to `KeyAlgorithm`, converts `Either[String, T]` into `Either[KmsDriverError, T]`, and centralizes logging.
-  `src/main/scala/com/saxonxyz/canton/kms/scaleway/ScalewayKmsDriverFactory.scala`: Concrete SPI factory that builds `ScalewayKmsDriver` from a `Config` or `Map[String,String]`.
-  `src/test/scala/com/saxonxyz/canton/kms/scaleway/ScalewayModelsSpec.scala`: JSON encoding/decoding tests; use this as the pattern when changing/adding models.
-  `src/test/scala/com/saxonxyz/canton/kms/scaleway/ScalewayKmsIntegrationTest.scala`: End-to-end tests against real Scaleway; auto-skip when required env vars are missing.

## Build and Test Workflow

-  Use sbt for all workflows:
   -  `sbt compile` – compile.
   -  `sbt test` – run unit and integration tests; integration tests require `SCW_SECRET_KEY`, `SCW_PROJECT_ID`, `SCW_REGION`.
   -  `sbt assembly` – build a fat JAR; this task is configured to depend on `test`, so keep tests green.
-  Integration tests are written to be safe to run without credentials: if env vars are not set, they `cancel` with a descriptive message. Preserve this behavior for any new integration tests.

## Coding Patterns and Conventions

-  **Error handling:**
   -  `ScalewayKmsClient` methods return `Either[String, T]` with human-readable error messages; they catch exceptions and log details via `LazyLogging`.
   -  `ScalewayKmsDriver` converts `Left(String)` into `KmsDriverError.InternalError` (or `UnsupportedOperation` for unsupported specs). Prefer enriching messages rather than throwing.
-  **Logging:**
   -  Use `com.typesafe.scalalogging.LazyLogging` in service classes. Follow existing log levels (`info` for lifecycle/actions, `debug` for per-call noise, `warn`/`error` for failures).
-  **JSON modeling:**
   -  All Scaleway KMS request/response types are in `ScalewayModels`; add new fields or types there and update the semiauto encoders/decoders.
   -  Maintain compatibility with existing and documented API formats (e.g., `usage` as a map vs. separate `usage` + `algorithm`, multiple string aliases for algorithms). Any change in JSON shape should be covered by/extended in `ScalewayModelsSpec`.
-  **Cryptography mapping:**
   -  Map Canton `SigningKeySpec`/`EncryptionKeySpec`/`SymmetricKeySpec` to `KeyAlgorithm` only in `ScalewayKmsDriver`; keep that mapping exhaustive and use `KmsDriverError.UnsupportedOperation` for unsupported specs.
   -  Keep `hashAlgorithmFor` in `ScalewayModels` in sync with supported signing algorithms used by `ScalewayKmsClient.sign`.
-  **Configuration:**
   -  Prefer `ScalewayKmsConfig.load()` (PureConfig) or `fromEnvironment()` to construct configs; keep env var names consistent with README and `reference.conf`.
   -  When adding config options, update `ScalewayKmsConfig`, `reference.conf`, and driver factory creation (`ScalewayKmsDriverFactory`).
-  **Testing style:**
   -  Use ScalaTest `AnyFlatSpec` + `Matchers` as in existing specs.
   -  For integration tests that rely on real Scaleway, follow the `skipIfNoEnv` pattern so they self-skip when credentials are absent.

## Extending the Driver

-  For new KMS operations or algorithms:
   -  Add/extend models in `ScalewayModels` (including codecs and, if needed, compatibility shims).
   -  Implement the HTTP call in `ScalewayKmsClient` with `Either[String, T]` and logging.
   -  Expose it through `ScalewayKmsDriver`, mapping to/from Canton types and `KmsDriverError`.
   -  Add/extend unit tests (e.g., for JSON) and, where appropriate, integration tests behind env-gated helpers.
-  When making changes that affect Canton integration (factory, SPI), ensure `META-INF/services/com.daml.kms.driver.KmsDriverFactory` still references the correct factory class.
