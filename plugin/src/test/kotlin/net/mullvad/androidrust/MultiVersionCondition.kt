package net.mullvad.androidrust

import io.kotest.core.annotation.Condition
import io.kotest.core.spec.Spec
import kotlin.reflect.KClass

class MultiVersionCondition : Condition {
    override fun evaluate(kclass: KClass<out Spec>): Boolean =
        !System.getProperty("org.gradle.android.testVersion").isNullOrEmpty()
}
