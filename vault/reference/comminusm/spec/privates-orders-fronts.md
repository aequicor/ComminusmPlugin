# Technical Specification — Privates: Orders and Fronts

## Feature Overview

The **Orders and Fronts** feature is the territorial-private system of the ComminusmPlugin. It allows players to:

- **Create an Order** (personal territory) by claiming a WHITE_BANNER flag.
- **Upgrade the Order** up to 5 levels, increasing its protected radius.
- **Create a Work Front** (secondary territory) via a RED_BANNER flag, linked to an existing activated Order.
- Manage both territories through in-game GUI menus (`/party`).
- Protect blocks inside their own Order or Front from modification by other players.
- Earn and spend **Workdays** (in-game currency) to pay for Order upgrades.

**User value:** Players get personal and secondary protected zones, progression via upgrades, and an intuitive GUI-driven management flow without requiring complex command syntax.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Command Layer                            │
│              /party  → PartyMenu                              │
│              /party admin → AdminMenu (comminusm.admin)       │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                      GUI Layer                              │
│  PartyMenu │ OrderMenu │ FrontMenu │ TreasuryMenu │ AdminMenu│
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   Event Listener Layer                        │
│  OrderFlagListener │ FrontFlagListener │ BlockListener        │
│  ExplosionListener │ FlagDeletionConfirmListener              │
│  FlagItemProtectionListener                                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Service Layer                            │
│              OrderService │ WorkFrontService                │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│              Chunk Caching (PDC on Chunk)                   │
│         ChunkCacheManager (order_owner / front_owner)       │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Data Layer                               │
│         OrderRepository │ WorkFrontRepository               │
│         DatabaseManager (SQLite, WAL, FK)                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Domain Models

### `Order`
```kotlin
data class Order(
    val id: Long,                    // auto-increment PK
    val ownerUuid: UUID,             // player UUID
    val level: Int,                  // 1..5
    val centerWorld: String?,        // null until activated
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radius: Int,                 // derived from level
    val createdAt: Long              // epoch millis
)
```
- **Computed properties:**
  - `center` → `Location(world, centerX, centerY, centerZ)` if `centerWorld != null`
  - `size` → `radius * 2 + 1`
  - `isActivated` → `centerWorld != null`

### `WorkFront`
```kotlin
data class WorkFront(
    val ownerUuid: UUID,
    val centerWorld: String,
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radius: Int,                 // fixed (frontRadius from config)
    val createdAt: Long
)
```
- **Computed properties:**
  - `center` → `Location(world, centerX, centerY, centerZ)`
  - `size` → `radius * 2 + 1`

### `WorkdaysBalance`
```kotlin
data class WorkdaysBalance(
    val playerUuid: UUID,
    val balance: Int
)
```

---

## Data Layer

### Database
- **Engine:** SQLite (local file)
- **Settings:** WAL mode enabled, foreign keys enabled.
- **Manager:** `DatabaseManager` handles connection pooling and schema initialization.

### Tables

#### `orders`
| Column       | Type     | Constraints               |
|--------------|----------|---------------------------|
| id           | INTEGER  | PK, AUTOINCREMENT           |
| owner_uuid   | TEXT     | NOT NULL, UNIQUE            |
| level        | INTEGER  | NOT NULL, DEFAULT 1         |
| center_world | TEXT     |                             |
| center_x     | INTEGER  |                             |
| center_y     | INTEGER  |                             |
| center_z     | INTEGER  |                             |
| radius       | INTEGER  | NOT NULL                    |
| created_at   | INTEGER  | NOT NULL (epoch millis)     |

#### `work_fronts`
| Column       | Type     | Constraints               |
|--------------|----------|---------------------------|
| owner_uuid   | TEXT     | PK                          |
| center_world | TEXT     | NOT NULL                    |
| center_x     | INTEGER  | NOT NULL                    |
| center_y     | INTEGER  | NOT NULL                    |
| center_z     | INTEGER  | NOT NULL                    |
| radius       | INTEGER  | NOT NULL                    |
| created_at   | INTEGER  | NOT NULL (epoch millis)     |

#### `workdays`
| Column       | Type     | Constraints               |
|--------------|----------|---------------------------|
| player_uuid  | TEXT     | PK                          |
| balance      | INTEGER  | NOT NULL, DEFAULT 0         |

### Repositories

#### `OrderRepository`
| Method | Signature | Description |
|--------|-----------|-------------|
| insert | `(order: Order) -> Long` | Insert new order, returns generated id |
| findByOwner | `(uuid: UUID) -> Order?` | Get order by owner |
| updateLevel | `(uuid: UUID, newLevel: Int, newRadius: Int) -> Unit` | Upgrade order level/radius |
| activate | `(uuid: UUID, world: String, x: Int, y: Int, z: Int) -> Unit` | Set center_world and coords |
| findAllInWorld | `(world: String) -> List<Order>` | All activated orders in a world |
| deleteByOwner | `(uuid: UUID) -> Unit` | Delete order record |

#### `WorkFrontRepository`
| Method | Signature | Description |
|--------|-----------|-------------|
| upsert | `(front: WorkFront) -> Unit` | Insert or replace front |
| findByOwner | `(uuid: UUID) -> WorkFront?` | Get front by owner |
| deleteByOwner | `(uuid: UUID) -> Unit` | Delete front record |
| findAllInWorld | `(world: String) -> List<WorkFront>` | All fronts in a world |

---

## Service Layer

### `OrderService`
| Method | Signature | Behavior |
|--------|-----------|----------|
| create | `(uuid: UUID) -> Order?` | Creates level-1 order if none exists; returns `null` on duplicate |
| activate | `(uuid: UUID, loc: Location) -> Boolean` | Checks overlap with `minDistanceBetweenCenters`, marks chunk via `ChunkCacheManager`, persists center coordinates. Returns `false` if overlap detected or order not found |
| findByOwner | `(uuid: UUID) -> Order?` | Delegates to repository |
| findAllInWorld | `(world: String) -> List<Order>` | Delegates to repository |
| checkOverlap | `(orders: List<Order>, x, y, z, radius) -> Boolean` | True if any existing order center is within `minDistanceBetweenCenters` of proposed center |
| getRadiusForLevel | `(level: Int) -> Int` | Maps level → radius from config |
| getCostForLevel | `(level: Int) -> Int` | Maps level → workday cost from config |
| getMaxLevel | `() -> Int` | Returns maximum configured level |
| upgrade | `(uuid: UUID) -> Boolean` | Checks balance, deducts cost, increments level and radius. Returns `false` if insufficient funds or max level reached |
| deleteByOwner | `(uuid: UUID) -> Unit` | Clears chunk cache, breaks banner block, drops flag item, deletes DB record |

### `WorkFrontService`
| Method | Signature | Behavior |
|--------|-----------|----------|
| activate | `(uuid: UUID, world: String, x, y, z) -> Boolean` | Deletes old front banner block, upserts new record, marks chunk. Returns `false` if no activated order or inside another player's order |
| getByOwner | `(uuid: UUID) -> WorkFront?` | Delegates to repository |
| deactivate | `(uuid: UUID) -> Unit` | Clears chunk cache, breaks banner block, deletes DB record |
| getAllInWorld | `(world: String) -> List<WorkFront>` | Delegates to repository |

---

## Event Listeners

### `OrderFlagListener`
- **Events:** `BlockPlaceEvent`, `PlayerInteractEvent`
- **BlockPlaceEvent:**
  - Detects `WHITE_BANNER` with display name containing `"Флаг Ордера"`.
  - Verifies player has a **non-activated** order.
  - Calls `orderService.activate(uuid, location)`.
  - Cancels event on any failure (overlap, no order, etc.).
- **PlayerInteractEvent:**
  - Right-click on `WHITE_BANNER` at **exact order center** opens `OrderMenu`.

### `FrontFlagListener`
- **Events:** `BlockPlaceEvent`, `PlayerInteractEvent`
- **BlockPlaceEvent:**
  - Detects `RED_BANNER` with display name containing `"Флаг Трудового Фронта"`.
  - Verifies player has an **activated** order.
  - Verifies placement is **not inside another player's order**.
  - Deletes old front banner block from world (if exists).
  - Calls `workFrontService.activate(uuid, world, x, y, z)`.
- **PlayerInteractEvent:**
  - Right-click on `RED_BANNER` at **exact front center** opens `FrontMenu`.

### `BlockListener`
- **Events:** `BlockBreakEvent`, `BlockPlaceEvent`, `PlayerInteractEvent`
- **onBlockBreak priority order:**
  1. Check if block is a WHITE/RED banner.
     - Own order flag → open deletion confirmation GUI (cancel break).
     - Others order flag → cancel break.
     - Own front flag → deactivate front + drop flag item.
     - Others front flag → cancel break.
  2. Check if block is inside own order → allow.
  3. Check if block is inside others order → deny.
  4. Check if block is inside own front → allow.
  5. No zones → deny.
  6. `isForeignFrontSupportBlock` check for banner support blocks.
- **onBlockPlace:**
  1. Allow if holding WHITE/RED banner (flag placement handled by dedicated listeners).
  2. Own order → allow.
  3. Others order → deny.
  4. Own front → allow.
  5. No zones → deny.
- **onPlayerInteract:**
  1. Allow if holding WHITE/RED banner.
  2. Own order → allow.
  3. Others order → deny.
  4. Own front → allow.
  5. No zones → deny.

### `ExplosionListener`
- **Event:** `EntityExplodeEvent`
- **Behavior:** Iterates exploded blocks and removes any blocks that are identified as order flags (`WHITE_BANNER` with order marker) or front flags (`RED_BANNER` with front marker) from the explosion block list, preventing their destruction.

### `FlagDeletionConfirmListener`
- **Event:** `InventoryClickEvent`
- **Trigger:** Inventory title contains `"Подтверждение удаления"`.
- **Slot 2 (confirm):**
  - Deletes order via `OrderService`.
  - Breaks the banner block.
  - Drops a custom flag item.
  - Closes inventory.
- **Slot 6 (cancel):**
  - Closes inventory without action.
- **Other slots:** Ignored / cancelled.

### `FlagItemProtectionListener`
- **Events:** `PlayerDropItemEvent`, `InventoryClickEvent` (moving flag items into containers)
- **Behavior:** Prevents players from accidentally dropping or storing custom flag items (`WHITE_BANNER` or `RED_BANNER` with custom display names/lore). Cancels the event and sends a warning message.

---

## GUI Layer

### `PartyMenu` (45 slots)
- **Title:** `Партийные услуги`
- **Layout:**
  - Slot `20` — **Order**: Create new order if none; open `OrderMenu` if exists.
  - Slot `24` — **Front**: Open `FrontMenu` if front exists; otherwise give front flag item.
  - Slot `31` — **Treasury**: Open `TreasuryMenu`.
  - Slot `40` — **Workdays Balance**: Show current balance (read-only info button).

### `OrderMenu` (45 slots)
- **Title:** `Ордер №{id}`
- **Layout:**
  - Slot `20` — **Info**: Order ID, owner, creation date.
  - Slot `22` — **Size**: Current size (`radius * 2 + 1`).
  - Slot `24` — **Upgrade**: If not max level, shows next level cost; click to upgrade.
  - Slot `31` — **Restore Flag**: Gives the player a new order flag item.
  - Slot `39` — **Back**: Return to `PartyMenu`.

### `FrontMenu` (45 slots)
- **Title:** `Трудовой Фронт`
- **Layout:**
  - Slot `20` — **Info**: Owner, creation date.
  - Slot `22` — **Radius**: Current radius and size.
  - Slot `24` — **Move**: Deactivates current front and gives a new front flag item.
  - Slot `39` — **Back**: Return to `PartyMenu`.

### `TreasuryMenu`
- **Title:** `Казна`
- **Purpose:** Resource donation interface for contributing items to the party treasury.

### `AdminMenu`
- **Title:** `Админ`
- **Access:** Requires permission `comminusm.admin`.
- **Purpose:** Administrative tools (inspect players, force operations, etc.).

---

## Commands

| Command | Permission | Behavior |
|---------|------------|----------|
| `/party` | (none) | Opens `PartyMenu` |
| `/party admin` | `comminusm.admin` | Opens `AdminMenu`. Denied with message if no permission. |

---

## Chunk Caching

### `ChunkCacheManager`
Stores territorial metadata directly on `Chunk` objects via **PersistentDataContainer (PDC)** to avoid heavy database lookups on every block event.

### PDC Keys (`NamespacedKey`)
| Key | Type | Purpose |
|-----|------|---------|
| `order_owner` | `STRING` | UUID of player who owns an order in this chunk |
| `front_owner` | `STRING` | UUID of player who owns a front in this chunk |

### Methods
| Method | Behavior |
|--------|----------|
| `markOrderChunk(chunk, ownerUuid)` | Writes `order_owner` PDC key |
| `removeOrderChunk(chunk)` | Removes `order_owner` PDC key |
| `hasOrderMarker(chunk) -> Boolean` | Checks if `order_owner` exists |
| `getOrderOwner(chunk) -> UUID?` | Reads `order_owner` value |
| `markFrontChunk(chunk, ownerUuid)` | Writes `front_owner` PDC key |
| `removeFrontChunk(chunk)` | Removes `front_owner` PDC key |
| `hasFrontMarker(chunk) -> Boolean` | Checks if `front_owner` exists |

> **Note:** There is **no `getFrontOwner`** method — only existence check (`hasFrontMarker`) is used.

### Lifecycle
- **Activation** (`OrderService.activate`, `WorkFrontService.activate`) → mark chunk.
- **Deletion / Deactivation** (`OrderService.deleteByOwner`, `WorkFrontService.deactivate`) → unmark chunk.
- **Server Restart** → markers persist in world save (PDC is saved with chunk data); no rebuild required.

---

## Configuration

### `PluginConfig` (YAML-backed)
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `minDistanceBetweenCenters` | `Int` | `30` | Minimum blocks between two order centers |
| `orderLevels` | `List<OrderLevel>` | see below | Level definitions for orders |
| `frontRadius` | `Int` | `25` | Radius of a Work Front |
| `passiveIncome` | `PassiveIncomeSettings` | — | Workdays earned per time period |
| `resourceRates` | `Map<String, Int>` | — | Item-to-workday conversion rates for treasury |

### Order Levels Default Table
| Level | Radius | Cost (Workdays) |
|-------|--------|-----------------|
| 1 | 2 | 0 |
| 2 | 3 | 30 |
| 3 | 4 | 80 |
| 4 | 5 | 150 |
| 5 | 7 | 300 |

---

## Security Model

### Permission Nodes
| Node | Description | Default |
|------|-------------|---------|
| `comminusm.admin` | Access to `/party admin` and admin GUI | OP only |

### Zone Ownership Rules
| Action | Own Order | Others Order | Own Front | Others Front | Wilderness |
|--------|-----------|--------------|-----------|--------------|------------|
| Break block | ✅ Allow | ❌ Deny | ✅ Allow | ❌ Deny | ❌ Deny |
| Place block | ✅ Allow | ❌ Deny | ✅ Allow | ❌ Deny | ❌ Deny |
| Interact (open doors, etc.) | ✅ Allow | ❌ Deny | ✅ Allow | ❌ Deny | ❌ Deny |
| Break order flag | 🔔 Confirm GUI | ❌ Deny | — | — | — |
| Break front flag | — | — | ✅ Drop item | ❌ Deny | — |

### Special Cases
- Holding a WHITE/RED banner bypasses place/interact denial (allows flag placement).
- Explosions cannot destroy order or front flags (they are removed from explosion list).
- Order deletion requires explicit GUI confirmation to prevent accidental loss.
- Flag items cannot be dropped or placed into containers.

---

## Test Coverage

### Existing Unit Tests
| Test Class | Coverage |
|------------|----------|
| `OrderServiceTest` | `create` (success + duplicate), radius/cost mapping per level, overlap detection (far = allow, near = deny) |
| `WorkFrontServiceTest` | `activate`, `replace` old front, `deactivate`, `getAllInWorld` |
| `OrderRepositoryTest` | `insert` + `findByOwner`, `updateLevel`, `activate` (set center) |
| `WorkFrontRepositoryTest` | `upsert`, `delete`, workdays `add`/`get` |

### Test Gaps (to be addressed)
- Listener integration tests (GUI click flows, block event cancellation).
- Chunk cache persistence across server restarts.
- Concurrent order creation edge cases.
- Front placement inside another player's order boundary.

---

## Known Limitations / TODOs

1. **No `getFrontOwner` in ChunkCacheManager** — only `hasFrontMarker` exists; front ownership lookups require repository fallback if exact owner is needed from chunk cache.
2. **Front in different world than Order** — `FrontFlagListener` checks for activated order but does not explicitly validate that front and order are in the same world. This may be intentional (cross-world fronts) or a gap.
3. **Workdays balance negative guard** — `upgrade()` deducts cost but relies on repository/SQL `CHECK` or application logic to prevent negative balances; verify constraint exists.
4. **Concurrent order creation** — two simultaneous `create()` calls could race; no explicit locking or unique constraint violation handling is documented beyond SQL `UNIQUE` on `owner_uuid`.
5. **Unloaded chunk order flag** — `OrderFlagListener` relies on chunk being loaded; behavior if banner is placed in an unloaded chunk is undefined.
6. **Banner placed by non-player** (dispenser, etc.) — `BlockPlaceEvent` checks `player` context; dispenser-placed banners are ignored and will not activate orders.
7. **Order upgrade while not activated** — `OrderMenu` may show upgrade button for non-activated orders; behavior if clicked is not specified (should be guarded).
