package ru.kyamshanov.comminusm.plugin

import org.junit.jupiter.api.Test

/**
 * Test to verify that lint violations are fixed.
 * This test uses reflection to check:
 * 1. wireFlagListeners has max 6 parameters
 * 2. wireMenus has max 6 parameters
 * 3. OrderService has max 11 public methods
 */
class LintViolationTest {
    @Test
    fun `wireFlagListeners parameter count is within threshold`() {
        // Get the wireFlagListeners method from ComminusmPlugin
        val clazz = ComminusmPlugin::class.java
        val methods = clazz.declaredMethods.filter { it.name == "wireFlagListeners" }

        // Should find exactly one method
        assert(methods.size == 1) { "Expected 1 wireFlagListeners method, found ${methods.size}" }

        val method = methods[0]
        val paramCount = method.parameterCount

        // Detekt threshold is 6, so parameter count must be <= 6
        assert(paramCount <= 6) { "wireFlagListeners has $paramCount parameters, max allowed is 6" }
    }

    @Test
    fun `wireMenus parameter count is within threshold`() {
        // Get the wireMenus method from ComminusmPlugin
        val clazz = ComminusmPlugin::class.java
        val methods = clazz.declaredMethods.filter { it.name == "wireMenus" }

        // Should find exactly one method
        assert(methods.size == 1) { "Expected 1 wireMenus method, found ${methods.size}" }

        val method = methods[0]
        val paramCount = method.parameterCount

        // Detekt threshold is 6, so parameter count must be <= 6
        assert(paramCount <= 6) { "wireMenus has $paramCount parameters, max allowed is 6" }
    }

    @Test
    fun `OrderService function count is within threshold`() {
        // Get all public functions from OrderService
        val clazz = ru.kyamshanov.comminusm.service.OrderService::class.java

        // Count public methods (excluding inherited ones from Any)
        val publicMethods =
            clazz.declaredMethods
                .filter {
                    java.lang.reflect.Modifier
                        .isPublic(it.modifiers)
                }.filterNot { it.declaringClass == Any::class.java }
                .filter {
                    !it.name.startsWith("get") || it.parameterCount == 0
                } // count getters as part of the threshold

        // Detekt threshold is 11, so max function count is 11
        assert(publicMethods.size <= 11) { "OrderService has ${publicMethods.size} public methods, max allowed is 11" }
    }
}
