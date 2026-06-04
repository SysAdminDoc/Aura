package com.freevibe.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateAuraBaselineProfile() {
        baselineProfileRule.collect(
            packageName = AURA_PACKAGE,
            includeInStartupProfile = true,
            profileBlock = {
                exerciseAuraCriticalJourneys()
            },
        )
    }
}
