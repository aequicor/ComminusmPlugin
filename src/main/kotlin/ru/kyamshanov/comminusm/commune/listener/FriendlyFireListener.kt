package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService

/**
 * Listener that prevents friendly-fire damage between commune members.
 *
 * Checks if both damager and damagee are players, and if they both belong to orders
 * that are members of the same commune. If so, cancels the damage event.
 *
 * Implements AC-20 & AC-22: "Friendly-fire disabled for commune members"
 */
class FriendlyFireListener(
    private val communeService: CommuneService,
    private val membershipService: OrderMembershipService
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (shouldCancelFriendlyFire(event.entity, event.damager)) {
            event.isCancelled = true
            if (event.damager is Player) {
                (event.damager as Player).sendMessage("Это ваш союзник!")
            }
        }
    }

    private fun shouldCancelFriendlyFire(damagee: Any, damager: Any): Boolean {
        // Both must be players
        if (damagee !is Player || damager !is Player) {
            return false
        }

        val damageeUuid = damagee.uniqueId
        val damagerUuid = damager.uniqueId

        // Get all native orders for damager (spec §6.17 step 4: ownerUuid OR granted_via='native')
        val damagerNativeOrders = membershipService.getNativeOrdersOfPlayer(damagerUuid)
        if (damagerNativeOrders.isEmpty()) {
            return false
        }

        // Get all native orders for damagee
        val damageeNativeOrders = membershipService.getNativeOrdersOfPlayer(damageeUuid)
        if (damageeNativeOrders.isEmpty()) {
            return false
        }

        // Check if any native orders are in the same commune
        return damagerNativeOrders.any { damagerOrderId ->
            val damagerCommune = communeService.getCommuneOfOrder(damagerOrderId)
            damagerCommune != null && damageeNativeOrders.any { damageeOrderId ->
                val damageeCommune = communeService.getCommuneOfOrder(damageeOrderId)
                damageeCommune != null && damagerCommune.id == damageeCommune.id
            }
        }
    }
}
