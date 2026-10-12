plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Test tiers by class-name suffix (docs/testing.md):
//   -PtestTier=unit         -> *Test only (excludes *IntegrationTest, *FeatureTest)
//   -PtestTier=integration  -> *IntegrationTest
//   -PtestTier=feature      -> *FeatureTest
//   (unset)                 -> all tiers
subprojects {
    val testTier = providers.gradleProperty("testTier")
    tasks.withType<Test>().configureEach {
        filter.isFailOnNoMatchingTests = false
        when (testTier.orNull) {
            null -> Unit
            "unit" -> {
                filter.excludeTestsMatching("*IntegrationTest")
                filter.excludeTestsMatching("*FeatureTest")
            }
            "integration" -> filter.includeTestsMatching("*IntegrationTest")
            "feature" -> filter.includeTestsMatching("*FeatureTest")
            else -> throw GradleException("Unknown testTier '${testTier.get()}' (unit | integration | feature)")
        }
    }
}
