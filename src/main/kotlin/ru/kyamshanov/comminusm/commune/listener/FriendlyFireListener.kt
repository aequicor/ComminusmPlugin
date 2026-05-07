@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import ru.kyamshanov.comminusm.application.usecases.commune.CheckCommuneFriendlyFireUseCase

/**
 * Listener that prevents friendly-fire damage between commune members.
 *
 * Checks if both damager and damagee are players, and if they both belong to orders
 * that are members of the same commune. If so, cancels the damage event.
 *
 * Implements AC-20 & AC-22: "Friendly-fire disabled for commune members"
 */
class FriendlyFireListener(
    private val checkCommuneFriendlyFireUseCase: CheckCommuneFriendlyFireUseCase,
) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damagee = event.entity
        val damager = event.damager

        if (damagee !is Player || damager !is Player) {
            return
        }

        if (checkCommuneFriendlyFireUseCase(damagee.uniqueId, damager.uniqueId)) {
            event.isCancelled = true
            damager.sendMessage("Это ваш союзник!")
        }
    }
}
