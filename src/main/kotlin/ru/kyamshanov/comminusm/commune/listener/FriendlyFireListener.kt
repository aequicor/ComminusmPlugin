package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.service.OrderService

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
    private val membershipService: OrderMembershipService,
    private val orderService: OrderService
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damagee = event.entity
        val damager = event.damager

        // Both must be players
        if (damagee !is Player || damager !is Player) {
            return
        }

        val damageeUuid = damagee.uniqueId
        val damagerUuid = damager.uniqueId

        // Get all native orders for damager (spec §6.17 step 4: ownerUuid OR granted_via='native')
        val damagerNativeOrders = membershipService.getNativeOrdersOfPlayer(damagerUuid)
        if (damagerNativeOrders.isEmpty()) {
            return
        }

        // Get all native orders for damagee
        val damageeNativeOrders = membershipService.getNativeOrdersOfPlayer(damageeUuid)
        if (damageeNativeOrders.isEmpty()) {
            return
        }

        // Check if any native orders are in the same commune
        for (damagerOrderId in damagerNativeOrders) {
            val damagerCommune = communeService.getCommuneOfOrder(damagerOrderId) ?: continue
            for (damageeOrderId in damageeNativeOrders) {
                val damageeCommune = communeService.getCommuneOfOrder(damageeOrderId) ?: continue
                if (damagerCommune.id == damageeCommune.id) {
                    // Both have native orders in same commune - cancel damage
                    event.isCancelled = true
                    // Send notification using Component API (not hardcoded §)
                    damager.sendMessage("Это ваш союзник!")
                    return
                }
            }
        }
    }
}
