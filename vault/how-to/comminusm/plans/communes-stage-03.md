---
genre: how-to
module: comminusm
title: Stage 03 — Commands
topic: communes
date: 2026-05-06
author: "@Main"
related:
  - vault/reference/comminusm/spec/communes.md
  - vault/concepts/comminusm/plans/communes-plan.md
---

# Stage 03 — Commands

**Objective:** Implement chat and info commands for communes.  
**Duration:** ~0.5 days  
**Dependencies:** Stage 01 & 02

---

## Components to Implement

### 1. CommuneCommand

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/command/CommuneCommand.kt`

**Responsibility:** Handle `/cc [text]` command — toggle mode, single-message dispatch, mute-check.

**Command registration:**
```kotlin
// In ComminusmPlugin.onEnable():
getCommand("cc")?.setExecutor(CommuneCommand(...))
getCommand("cc")?.tabCompleter = object : TabCompleter { ... }
```

**Usage:**
- `/cc <text>` — send one message to all online commune members
- `/cc` (no args) — toggle "always in commune chat" mode

**Implementation:**

```kotlin
class CommuneCommand(
    private val communeService: CommuneService,
    private val orderMembershipService: OrderMembershipService,
    private val communeChatService: CommuneChatService,
    private val muteService: MuteService?, // optional, if exists
) : CommandExecutor {
    
    override fun onCommand(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cЭта команда доступна только игрокам")
            return true
        }
        
        val player = sender
        val nativeOrders = orderMembershipService.listNativeOrders(player.uniqueId)
        
        // Check if player is in a commune
        val playerCommune = nativeOrders
            .asSequence()
            .mapNotNull { communeService.getCommuneByOrder(it) }
            .firstOrNull()
        
        if (playerCommune == null) {
            player.sendMessage("§cВы не состоите ни в одной коммуне")
            return true
        }
        
        // Check mute status
        if (muteService?.isMuted(player.uniqueId) == true) {
            player.sendMessage("§cВы в muteе и не можете писать в коммуне")
            return true
        }
        
        return when {
            args.isEmpty() -> {
                // Toggle mode
                val currentMode = communeChatService.getToggleMode(player.uniqueId)
                communeChatService.setToggleMode(player.uniqueId, !currentMode)
                if (!currentMode) {
                    player.sendMessage("§aВы в режиме чата коммуны. Введите /cc для выхода")
                } else {
                    player.sendMessage("§aВы вышли из режима чата коммуны")
                }
                true
            }
            else -> {
                // Send single message
                val messageText = args.joinToString(" ")
                communeChatService.broadcastToCommune(
                    playerCommune.id,
                    player,
                    messageText
                )
                true
            }
        }
    }
}
```

**CommuneChatService implementation (spec §5 and §6.8):**

```kotlin
interface CommuneChatService {
    // Toggle mode (persistent per session, reset on leave commune)
    fun setToggleMode(playerUUID: UUID, enabled: Boolean)
    fun getToggleMode(playerUUID: UUID): Boolean
    
    // Broadcast message to all online commune members
    suspend fun broadcastToCommune(communeId: UUID, sender: Player, text: String)
    
    // Plain-text wrapping (CC-09)
    fun wrapPlainText(text: String): String
    
    // Reset toggles when player's commune dissolved (§6.7)
    fun resetToggleMode(playerUUID: UUID)
}
```

**Broadcast implementation:**

```kotlin
suspend fun broadcastToCommune(communeId: UUID, sender: Player, text: String) {
    val commune = communeService.getCommuneById(communeId) ?: return
    val orders = communeService.getOrdersInCommune(communeId) ?: emptySet()
    
    val communeMembers = Bukkit.getOnlinePlayers()
        .filter { player ->
            val playerOrders = orderMembershipService.listNativeOrders(player.uniqueId)
            playerOrders.any { it in orders }
        }
    
    val wrappedText = wrapPlainText(text)
    val message = Component.text()
        .append(Component.text("§8[§aКоммуна§8] "))
        .append(Component.text(sender.name).color(NamedTextColor.YELLOW))
        .append(Component.text(": "))
        .append(Component.text(wrappedText))
        .build()
    
    communeMembers.forEach { it.sendMessage(message) }
}
```

**Tests:**
- Command parsing: `/cc text`, `/cc` (no args)
- Toggle mode: on → off → on
- Message broadcasting: correct recipients, correct format
- Mute check (if muteService exists)
- Not in commune: error message

---

### 2. OrderCommuneInfoCommand

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/command/OrderCommuneInfoCommand.kt`

**Responsibility:** Handle `/order commune <orderName>` — read-only info display (spec AC-23).

**Usage:**
- `/order commune <orderName>` — display commune info for order

**Implementation:**

```kotlin
class OrderCommuneInfoCommand(
    private val orderService: OrderService,
    private val communeService: CommuneService,
) : CommandExecutor {
    
    override fun onCommand(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§cИспользование: /order commune <имя ордера>")
            return true
        }
        
        val orderName = args.joinToString(" ")
        val order = orderService.getOrderByName(orderName)
        
        if (order == null) {
            sender.sendMessage("§cОрдер не найден: $orderName")
            return true
        }
        
        val commune = communeService.getCommuneByOrder(order.id)
        
        return if (commune == null) {
            sender.sendMessage("§7Ордер «${order.name}» не состоит ни в одной коммуне")
            true
        } else {
            // Display commune info
            val communeOrders = communeService.getOrdersInCommune(commune.id) ?: emptySet()
            val orderNames = communeOrders.mapNotNull { orderId ->
                orderService.getOrderById(orderId)?.name
            }
            
            sender.sendMessage("§8═════════════════════")
            sender.sendMessage("§eОрдер: §7${order.name}")
            sender.sendMessage("§eВладелец: §7${Bukkit.getOfflinePlayer(order.ownerUuid).name}")
            sender.sendMessage("§eВ коммуне: §aДА")
            sender.sendMessage("§eСоюзники: §7${orderNames.joinToString(", ")}")
            sender.sendMessage("§8═════════════════════")
            true
        }
    }
}
```

**Startup guard (spec AC-23):**
- If `CommuneStartupTask.completed() == false` → "Система коммун инициализируется, попробуйте снова через несколько секунд"

**Tests:**
- Order not found
- Order not in commune
- Order in commune: display all allies
- Startup guard check

---

## DoD Checklist

- [ ] `/cc` command works: send message, toggle mode
- [ ] `/order commune` command works: display commune info
- [ ] Plain-text wrapping implemented (CC-09)
- [ ] Mute check integrated
- [ ] Startup guard implemented
- [ ] All tests pass
- [ ] Lint passes

---
