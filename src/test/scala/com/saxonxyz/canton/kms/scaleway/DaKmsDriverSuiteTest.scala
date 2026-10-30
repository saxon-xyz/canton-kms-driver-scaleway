package com.saxonxyz.canton.kms.scaleway

import com.digitalasset.canton.crypto.kms.driver.api.v1.KmsDriver
import com.digitalasset.canton.crypto.kms.driver.testing.v1.{KmsDriverFactoryTest, KmsDriverTest}

import scala.concurrent.ExecutionContext

/** DA's reusable KMS driver test suite (com.daml %% kms-driver-testing), run against real
  * Scaleway Key Manager. Needs SCW_SECRET_KEY / SCW_PROJECT_ID / SCW_REGION, and
  * SCW_KMS_DEFAULT_PROTECTED=false so the keys it generates can be deleted. Without them
  * every test is cancelled rather than failed.
  */
private object DaSuiteEnv {
  val present: Boolean = Seq("SCW_SECRET_KEY", "SCW_PROJECT_ID", "SCW_REGION").forall(sys.env.contains)

  def config(cancel: String => Nothing): ScalewayKmsConfig =
    if (present)
      ScalewayKmsConfig.fromEnvironment()
    else cancel("Scaleway credentials not set (SCW_SECRET_KEY, SCW_PROJECT_ID, SCW_REGION)")
}

class ScalewayDaKmsDriverTest extends KmsDriverTest {

  // The suite's afterAll closes its lazily created driver, which would build one (and fail)
  // when no credentials are set.
  override def afterAll(): Unit = if (DaSuiteEnv.present) super.afterAll()

  override protected def newKmsDriver(): KmsDriver =
    ScalewayKmsDriver(DaSuiteEnv.config(cancel(_)), ExecutionContext.global)

  // kmsDriver() builds the driver while registering tests (to enumerate key specs), so
  // without credentials register a single cancelled test instead of aborting the suite.
  if (DaSuiteEnv.present)
    "Scaleway KMS Driver" must {
      behave like kmsDriver(allowKeyGeneration = true)
    }
  else
    "Scaleway KMS Driver" must {
      "run against Scaleway Key Manager" in cancel("Scaleway credentials not set")
    }
}

class ScalewayDaKmsDriverFactoryTest extends KmsDriverFactoryTest {

  override type Factory = ScalewayKmsDriverFactory

  override protected lazy val factory: ScalewayKmsDriverFactory = new ScalewayKmsDriverFactory

  override protected lazy val config: ScalewayKmsDriverFactory#ConfigType =
    DaSuiteEnv.config(cancel(_))

  "Scaleway KMS Driver Factory" must {
    behave like kmsDriverFactory()
  }
}
