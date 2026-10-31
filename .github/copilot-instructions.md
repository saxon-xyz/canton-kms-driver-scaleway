# Instructions for AI coding agents

This repo is a Canton KMS driver for Scaleway Key Manager (driver name `scaleway`).

- API: the official `com.daml %% kms-driver-api` from Maven Central (package
  `com.digitalasset.canton.crypto.kms.driver.api.v1`), `Provided`. Do not vendor or stub it.
- The JAR is **thin**. Everything Canton ships (Scala, circe, sttp core, pureconfig, logback) is
  `Provided` and pinned in `build.sbt` to the versions of the target Canton release. Bundling
  them breaks Canton's logging and config reader at load time. Re-pin on a Canton minor bump.
- SPI: register the factory in both `META-INF/services/...api.KmsDriverFactory` and
  `...api.v1.KmsDriverFactory`.
- Code: `ScalewayKmsClient` (HTTP, returns `Either[String, T]`, errors as `"<what>: <status> - <body>"`),
  `ScalewayKmsDriver` (maps Canton specs, throws `KmsDriverException` with `retryable` from
  `isRetryable`: 4xx except 429 is permanent), `ScalewayKmsDriverFactory`, `ScalewayKmsConfig`.
- Tests: `sbt test`. Unit tests need nothing. DA's suite (`DaKmsDriverSuiteTest`) and the
  integration tests need `SCW_SECRET_KEY`, `SCW_PROJECT_ID`, `SCW_REGION` and
  `SCW_KMS_DEFAULT_PROTECTED=false`, and cancel cleanly without them. Keep that behaviour.
- Never commit credentials or project IDs.
