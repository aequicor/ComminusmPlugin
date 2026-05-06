---
genre: how-to
module: comminusm
title: Stage 06 — Integration & Final Verification
topic: communes
date: 2026-05-06
author: "@Main"
related:
  - vault/reference/comminusm/spec/communes.md
  - vault/concepts/comminusm/plans/communes-plan.md
---

# Stage 06 — Integration & Final Verification

**Objective:** Wire everything together, run full test suite, verify corner cases and spec coverage.  
**Duration:** ~1 day  
**Dependencies:** All previous stages (01-05)

---

## Activities

### 1. Dependency Injection Wiring

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt` (modify `onEnable()`)

```kotlin
override fun onEnable() {
    // ... existing initialization ...
    
    // Stage 01: Repositories & Services
    val databaseManager = DatabaseManager(dataFolder)
    val orderMembersRepository = OrderMembersRepository(databaseManager)
    val orderMembershipService = OrderMembershipService(orderMembersRepository)
    val communeService = CommuneService()
    val communeInvitationService = CommuneInvitationService()
    val crossOrderMembershipService = CrossOrderMembershipService(
        orderMembershipService,
        communeService
    )
    
    // Stage 02: Listeners
    Bukkit.getPluginManager().registerEvents(
        CommuneStartupTask(
            databaseManager, communeService, communeInvitationService,
            orderMembersRepository, orderMembershipService
        ),
        this
    )
    Bukkit.getPluginManager().registerEvents(
        CommuneOrderDestroyListener(
            communeService, crossOrderMembershipService,
            orderMembershipService, communeChatService
        ),
        this
    )
    Bukkit.getPluginManager().registerEvents(
        CommuneMembershipListener(crossOrderMembershipService),
        this
    )
    Bukkit.getPluginManager().registerEvents(
        CommunePlayerListener(communeService, orderMembershipService, communeChatService),
        this
    )
    Bukkit.getPluginManager().registerEvents(
        FriendlyFireListener(communeService, orderMembershipService),
        this
    )
    
    // Stage 03: Commands
    getCommand("cc")?.setExecutor(
        CommuneCommand(communeService, orderMembershipService, communeChatService)
    )
    getCommand("order")?.let { orderCmd ->
        val existingExecutor = orderCmd.executor
        orderCmd.setExecutor(
            DelegatingCommandExecutor(
                existingExecutor,
                OrderCommuneInfoCommand(orderService, communeService)
            )
        )
    }
    
    // Stage 04: Menus (Decorators)
    val partyMenu = CommunePartyMenu(PartyMenu(...), communeService, orderService, orderMembershipService)
    val orderMenu = CommuneOrderMenu(OrderMenu(...), orderService, orderMembershipService)
    Bukkit.getPluginManager().registerEvents(partyMenu, this)
    Bukkit.getPluginManager().registerEvents(orderMenu, this)
    Bukkit.getPluginManager().registerEvents(
        CommuneMenu(communeService, orderService, communeInvitationService, orderMembershipService),
        this
    )
    Bukkit.getPluginManager().registerEvents(
        OrderMembersMenu(orderMembershipService, communeService, orderService),
        this
    )
    
    // Stage 05: Chat Service
    val communeChatService = CommuneChatService(communeService, orderMembershipService)
    Bukkit.getPluginManager().registerEvents(
        AsyncChatEventListener(communeChatService),
        this
    )
    
    // Startup async task
    Bukkit.getScheduler().runTaskAsynchronously(this) {
        CommuneStartupTask(...).startup()
    }
}
```

---

### 2. Database Schema Creation

**File:** `src/main/kotlin/ru/kyamshanov/comminusm/storage/DatabaseManager.kt` (add at startup)

```kotlin
suspend fun initializeSchema() {
    val createOrderMembersTable = """
        CREATE TABLE IF NOT EXISTS order_members (
            order_id BIGINT NOT NULL,
            player_uuid VARCHAR(36) NOT NULL,
            granted_at TIMESTAMP NOT NULL,
            granted_via VARCHAR(16) NOT NULL,
            
            PRIMARY KEY (order_id, player_uuid),
            UNIQUE (order_id, player_uuid),
            FOREIGN KEY (order_id) REFERENCES orders(id),
            CHECK (granted_via IN ('native', 'commune'))
        );
    """.trimIndent()
    
    val createCommunesTable = """
        CREATE TABLE IF NOT EXISTS communes (
            id VARCHAR(36) PRIMARY KEY,
            created_at TIMESTAMP NOT NULL,
            version BIGINT NOT NULL DEFAULT 0
        );
    """.trimIndent()
    
    val createCommuneOrdersTable = """
        CREATE TABLE IF NOT EXISTS commune_orders (
            commune_id VARCHAR(36) NOT NULL,
            order_id BIGINT NOT NULL,
            
            PRIMARY KEY (commune_id, order_id),
            FOREIGN KEY (commune_id) REFERENCES communes(id),
            FOREIGN KEY (order_id) REFERENCES orders(id)
        );
    """.trimIndent()
    
    val createCommuneInvitationsTable = """
        CREATE TABLE IF NOT EXISTS commune_invitations (
            id VARCHAR(36) PRIMARY KEY,
            commune_id VARCHAR(36) NOT NULL,
            from_order_id BIGINT NOT NULL,
            target_order_id BIGINT NOT NULL,
            target_leader_uuid VARCHAR(36) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            
            FOREIGN KEY (commune_id) REFERENCES communes(id),
            FOREIGN KEY (from_order_id) REFERENCES orders(id),
            FOREIGN KEY (target_order_id) REFERENCES orders(id)
        );
    """.trimIndent()
    
    executeUpdate(createOrderMembersTable)
    executeUpdate(createCommunesTable)
    executeUpdate(createCommuneOrdersTable)
    executeUpdate(createCommuneInvitationsTable)
}
```

---

### 3. Integration Tests

**File:** `src/test/kotlin/ru/kyamshanov/comminusm/integration/CommuneSystemIntegrationTest.kt`

**Scenarios:**

```kotlin
class CommuneSystemIntegrationTest {
    
    @Test
    fun fullWorkflow_CreateCommuneInviteAccept() {
        // 1. Player A (leader of Order 1) creates a commune
        val commune = communeService.createCommune(ORDER_1)
        assert(commune.orderIds.contains(ORDER_1))
        
        // 2. Player A invites Order 2
        val invitation = communeInvitationService.createInvitation(
            communeId = commune.id,
            fromOrderId = ORDER_1,
            targetOrderId = ORDER_2,
            targetLeaderUUID = PLAYER_B_UUID
        )
        assert(invitation.expiresAt > Instant.now())
        
        // 3. Player B (leader of Order 2) accepts
        communeService.addOrderToCommune(commune.id, ORDER_2)
        communeInvitationService.cancelInvitation(invitation.id)
        
        // 4. Verify commune now has both orders
        val updatedCommune = communeService.getCommuneById(commune.id)!!
        assert(updatedCommune.orderIds.size == 2)
    }
    
    @Test
    fun cascade_OrderDestroyed() {
        // 1. Create commune with 2 orders
        val commune = createCommuneWith2Orders()
        
        // 2. Add cross-order member: Player C from Order 2 as member of Order 1
        crossOrderMembershipService.grantCrossOrderMember(ORDER_2, ORDER_1, PLAYER_C_UUID)
        
        // 3. Destroy Order 1
        orderService.deleteByOwner(ORDER_1_OWNER)  // publishes FlagDeactivatedEvent
        
        // 4. Verify cascade:
        // - Order 1 removed from commune
        // - All cross-order members of Order 1 revoked
        // - Commune dissolved if empty, or membership update broadcast
        val updatedCommune = communeService.getCommuneById(commune.id)
        assert(ORDER_1 !in (updatedCommune?.orderIds ?: emptySet()))
    }
    
    @Test
    fun friendlyFire_NoopWithinCommune() {
        // 1. Create commune with 2 orders, both members online
        val player1 = createPlayer(ORDER_1)
        val player2 = createPlayer(ORDER_2)
        
        // 2. Player 1 attacks Player 2
        val event = EntityDamageByEntityEvent(player2, player1, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0)
        
        // 3. Verify damage is blocked
        friendlyFireListener.onEntityDamage(event)
        assert(event.isCancelled)
    }
    
    @Test
    fun communeChat_Broadcast() {
        // 1. Create commune with 2 orders
        val player1 = createPlayer(ORDER_1)
        val player2 = createPlayer(ORDER_2)
        val outsider = createPlayer(ORDER_3)  // not in commune
        
        // 2. Player 1 sends /cc message
        communeChatService.broadcastToCommune(COMMUNE_ID, player1, "Привет союзники!")
        
        // 3. Verify only player 2 receives it (not outsider)
        // (verify via mock/spy on sendMessage)
        verify(player1.sendMessage("...")).times(0)  // sender doesn't receive their own message
        verify(player2.sendMessage("...")).times(1)  // commune member receives
        verify(outsider.sendMessage("...")).times(0)  // outsider doesn't receive
    }
    
    @Test
    fun consistencyScan_AC47_Detects_OrphanMembers() {
        // 1. Manually insert orphan record: player with no native orders as cross-order member
        orderMembersRepository.addMember(ORDER_1, ORPHAN_UUID, "commune")
        
        // 2. Run startup task (AC-47 scan)
        val result = communeStartupTask.startup()
        
        // 3. Verify scan detects and logs the issue
        assert(result.issues.any { it.contains("ORPHAN") })
        
        // 4. Verify orphan was cleaned up
        assert(!orderMembershipService.isMember(ORDER_1, ORPHAN_UUID))
    }
    
    @Test
    fun cascade_LeaveCommune() {
        // 1. Create commune with 3 orders: A, B, C
        // 2. Add cross-order members: player from B in A, player from C in A
        // 3. Player A (leader) leaves commune
        // 4. Verify all cross-order grants revoked, others remain in commune
        
        // Implementation: similar to above
    }
}
```

---

### 4. Corner Case Verification

Run all 116 test cases from `vault/reference/comminusm/test-cases/communes-test-cases.md`:

**Critical Corner Cases (CC):**
- CC-01: Order destroyed while in commune → cascade handles it
- CC-06: Leave confirmation screen state
- CC-S01: Cascade ordering (post-delete)
- etc.

**High Corner Cases (all 14):** Verify each has a test

**Manual test checklist:**
- [ ] Create commune (single order)
- [ ] Invite order (expires in 500s)
- [ ] Accept invitation (state validation, version check)
- [ ] Leave commune (confirmation screen, cascade revokes rights)
- [ ] Order destroyed (cascade cascade, test on live server)
- [ ] Commune dissolved (all cross-order rights revoked)
- [ ] Chat toggle mode (on/off, reset on leave)
- [ ] Friendly-fire (same commune = no damage, diff = damage allowed)
- [ ] GUI: all menus render correctly
- [ ] Commands: `/cc` and `/order commune` work

---

### 5. Spec Endpoint Tracing

**Requirement:** Every spec endpoint (from spec §4, §5, §6) has at least one test case.

| Endpoint | Test Case | Status |
|----------|-----------|--------|
| Create Commune (§6.1) | TC-01 (happy), TC-02..TC-04 (AC) | ✅ |
| Invite Order (§6.2) | TC-05..TC-08 | ✅ |
| Accept Invitation (§6.3) | TC-09..TC-12 | ✅ |
| Decline Invitation (§6.4) | TC-13, TC-14 | ✅ |
| Leave Commune (§6.5) | TC-15..TC-20 | ✅ |
| Order Destroyed (§6.6) | TC-21..TC-25, CC-01, CC-S01 | ✅ |
| Dissolve Commune (§6.7) | TC-26..TC-30 | ✅ |
| [Invite native member](§6.8) | TC-31..TC-35 | (US-12, in scope) |
| etc. | ... | ✅ all 116 TCs |

---

## Final Verification Checklist

### Build & Quality

- [ ] `./gradlew clean compileKotlin` — no errors
- [ ] `./gradlew test` — all tests pass (unit + integration)
- [ ] `./gradlew detekt ktlintCheck` — no lint errors
- [ ] No uncommented TODOs (or all in DECISIONS.md)

### Spec Compliance

- [ ] All 16 US implemented and tested
- [ ] All 63 AC verified in tests
- [ ] All 7 Critical CC have tests
- [ ] All 14 High CC have tests
- [ ] Traceability: every spec §6 endpoint → ≥ 1 test case
- [ ] No orphan endpoints or missed requirements

### Corner Cases

- [ ] Cascade order (CC-S01): post-delete cascade executes correctly
- [ ] O(N²) prevention (CC-Q5): batch mode suppresses redundant recalculations
- [ ] Stale-state guard (§5.4): menu version snapshot invalidates old invitations
- [ ] Friendly-fire multi-membership (AC-22): player in multiple native orders handled
- [ ] AC-47 consistency scan: orphan detection + cleanup

### Security

- [ ] No direct SQL concatenation (parameterized queries only)
- [ ] Cross-order rights authorization: only order leader can grant
- [ ] Invitation expiry enforced (500 s timer)
- [ ] Permission checks in all menus (leader-only buttons hidden for non-leaders)
- [ ] `@SecurityReviewer` approval: chat system, permissions, invitations

### Performance

- [ ] In-memory cache populated at startup (O(1) lookups)
- [ ] Async DB writes don't block main thread
- [ ] Cascade batch mode reduces N² to N operations
- [ ] Menu rendering < 500ms per inventory
- [ ] No blocking calls in event handlers

---

## DoD Checklist

- [ ] All stages 01-05 complete and tested
- [ ] DI wiring in `ComminusmPlugin.onEnable()` complete
- [ ] Database schema created at startup
- [ ] All 116 test cases from test-cases.md pass
- [ ] All CC (corner cases) verified
- [ ] Build clean: `./gradlew compileKotlin detekt ktlintCheck test`
- [ ] Code reviewed by @CodeReviewer
- [ ] Security reviewed by @SecurityReviewer
- [ ] Traceability verified by @TraceabilityChecker
- [ ] Ready for PO final approval

---

## Definition of Done (Feature Level)

✅ **Complete when:**
1. All code compiles and tests pass (100% GREEN)
2. All CC (corner cases) verified in production (manual + automated)
3. All spec endpoints traced to test cases
4. Code reviewed (style + security)
5. Zero open CRITICAL/HIGH review findings
6. PO sign-off on final feature

---
