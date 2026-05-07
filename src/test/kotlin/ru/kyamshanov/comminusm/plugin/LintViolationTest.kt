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
    fun `wireFlagListeners no longer exists (moved to DIContainer)`() {
        // After refactoring, wireFlagListeners has been moved to DIContainer.createListeners()
        val clazz = ComminusmPlugin::class.java
        val methods = clazz.declaredMethods.filter { it.name == "wireFlagListeners" }

        // Should find no such method (logic moved to DIContainer)
        assert(methods.isEmpty()) {
            "wireFlagListeners should not exist in ComminusmPlugin after refactoring to DIContainer"
        }
    }

    @Test
    fun `wireMenus no longer exists (moved to DIContainer)`() {
        // After refactoring, wireMenus has been moved to DIContainer.createMenus()
        val clazz = ComminusmPlugin::class.java
        val methods = clazz.declaredMethods.filter { it.name == "wireMenus" }

        // Should find no such method (logic moved to DIContainer)
        assert(methods.isEmpty()) {
            "wireMenus should not exist in ComminusmPlugin after refactoring to DIContainer"
        }
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
