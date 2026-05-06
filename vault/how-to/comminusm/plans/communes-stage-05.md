---
genre: how-to
module: comminusm
title: Stage 05 — Chat Service
topic: communes
date: 2026-05-06
author: "@Main"
related:
  - vault/reference/comminusm/spec/communes.md
  - vault/concepts/comminusm/plans/communes-plan.md
---

# Stage 05 — Chat Service

**Objective:** Route `/cc` messages, enforce rules, manage per-player toggle state.  
**Duration:** ~0.5 days  
**Dependencies:** Stage 01 & 02

---

## Components to Implement

### CommuneChatService

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/service/CommuneChatService.kt`

**Responsibility:** Route `/cc` messages, manage per-player toggle state, plain-text enforcement.

**Public API:**

```kotlin
interface CommuneChatService {
    // Toggle mode (persistent per session, reset on commune dissolution)
    fun setToggleMode(playerUUID: UUID, enabled: Boolean)
    fun getToggleMode(playerUUID: UUID): Boolean
    
    // Broadcast message to all online commune members
    suspend fun broadcastToCommune(
        communeId: UUID,
        sender: Player,
        text: String
    )
    
    // Plain-text wrapping (CC-09)
    fun wrapPlainText(text: String): String
    
    // Reset toggles when player's commune dissolved (§6.7)
    fun resetToggleMode(playerUUID: UUID)
    
    // Check if player is in a commune (convenience method)
    fun isInCommune(playerUUID: UUID): Boolean
}
```

**Implementation:**

```kotlin
class CommuneChatServiceImpl(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
) : CommuneChatService {
    
    private val toggleModeMap = ConcurrentHashMap<UUID, Boolean>()
    
    override fun setToggleMode(playerUUID: UUID, enabled: Boolean) {
        if (enabled) {
            toggleModeMap[playerUUID] = true
        } else {
            toggleModeMap.remove(playerUUID)
        }
    }
    
    override fun getToggleMode(playerUUID: UUID): Boolean {
        return toggleModeMap[playerUUID] ?: false
    }
    
    override suspend fun broadcastToCommune(
        communeId: UUID,
        sender: Player,
        text: String
    ) {
        val commune = communeService.getCommuneById(communeId) ?: return
        val orders = communeService.getOrdersInCommune(communeId) ?: emptySet()
        
        // Find all online players in commune
        val communeMembers = Bukkit.getOnlinePlayers()
            .filter { player ->
                val playerOrders = orderMembershipService.listNativeOrders(player.uniqueId)
                playerOrders.any { it in orders }
            }
        
        // Format message
        val wrappedText = wrapPlainText(text)
        val component = Component.text()
            .append(Component.text("§8[§aКоммуна§8] "))
            .append(Component.text(sender.name).color(NamedTextColor.YELLOW))
            .append(Component.text(": ").color(NamedTextColor.GRAY))
            .append(Component.text(wrappedText).color(NamedTextColor.WHITE))
            .build()
        
        // Send to all commune members
        communeMembers.forEach { it.sendMessage(component) }
    }
    
    override fun wrapPlainText(text: String): String {
        // CC-09: prevent code injection (e.g., § codes to change color mid-message)
        return text.replace(Regex("[§&][0-9a-f]"), "")  // strip legacy/minimessage codes
    }
    
    override fun resetToggleMode(playerUUID: UUID) {
        toggleModeMap.remove(playerUUID)
    }
    
    override fun isInCommune(playerUUID: UUID): Boolean {
        val orders = orderMembershipService.listNativeOrders(playerUUID)
        return orders.any { communeService.getCommuneByOrder(it) != null }
    }
}
```

### AsyncChatEventListener (optional, if handling `/cc` via async PlayerAsyncChatEvent)

```kotlin
class AsyncChatEventListener(
    private val communeChatService: CommuneChatService,
) : Listener {
    
    @EventHandler
    fun onAsyncChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        
        if (!communeChatService.getToggleMode(player.uniqueId)) {
            return  // Not in commune chat mode
        }
        
        val orders = orderMembershipService.listNativeOrders(player.uniqueId)
        val commune = orders
            .asSequence()
            .mapNotNull { communeService.getCommuneByOrder(it) }
            .firstOrNull()
        
        if (commune == null) {
            event.isCancelled = true
            player.sendMessage("§cВы больше не в коммуне")
            communeChatService.resetToggleMode(player.uniqueId)
            return
        }
        
        // Suppress global chat, route to commune
        event.isCancelled = true
        Bukkit.getScheduler().runTask(plugin) {
            communeChatService.broadcastToCommune(commune.id, player, event.message)
        }
    }
}
```

---

## Tests for Stage 05

### Unit Tests

- Toggle mode: on/off
- Wrap plain text: strip color codes
- Broadcast: correct recipients, correct format
- Is in commune: true/false based on membership

### Integration Tests

- Full chat flow: toggle mode → broadcast → all commune members receive
- Player leaves commune: toggle reset
- Commune dissolved: all players' toggles reset

---

## DoD Checklist

- [ ] CommuneChatService compiles
- [ ] `./gradlew compileKotlin` passes
- [ ] Unit tests pass
- [ ] Broadcast delivers to correct players
- [ ] Plain-text wrapping removes color codes
- [ ] Lint passes

---
