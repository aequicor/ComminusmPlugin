# Стадия 03: Application Layer — Use Cases (разбор больших сервисов)

**Цель:** Разбить OrderService, CommuneService и другие на маленькие, fokusированные Use Cases

**Масштаб:** ~15-20 Use Cases с интерфейсами и реализациями

**Время:** ~4-5 часов

---

## Задачи

### 3.1 Создать интерфейсы Use Cases для Order

```kotlin
// application/usecases/order/CreateOrderUseCase.kt
package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

interface CreateOrderUseCase {
    operator fun invoke(ownerUuid: UUID): Result<Order>
}

// application/usecases/order/ActivateOrderUseCase.kt
package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.WorldLocation
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

interface ActivateOrderUseCase {
    operator fun invoke(ownerUuid: UUID, location: WorldLocation): Result<Unit>
}

// application/usecases/order/UpgradeOrderUseCase.kt
interface UpgradeOrderUseCase {
    operator fun invoke(ownerUuid: UUID): Result<Order>
}

// application/usecases/order/DeleteOrderUseCase.kt
interface DeleteOrderUseCase {
    operator fun invoke(ownerUuid: UUID): Result<Unit>
}

// application/usecases/order/GetOrderByOwnerUseCase.kt
interface GetOrderByOwnerUseCase {
    operator fun invoke(ownerUuid: UUID): Order?
}

// application/usecases/order/GetOrderByIdUseCase.kt
interface GetOrderByIdUseCase {
    operator fun invoke(orderId: Long): Order?
}

// application/usecases/order/FindOrdersInWorldUseCase.kt
interface FindOrdersInWorldUseCase {
    operator fun invoke(world: String): List<Order>
}

// application/usecases/order/CheckOrderOverlapUseCase.kt
interface CheckOrderOverlapUseCase {
    operator fun invoke(x: Int, y: Int, z: Int, radius: Int, worldName: String): Boolean
}
```

### 3.2 Реализовать Order Use Cases

```kotlin
// application/usecases/order/CreateOrderUseCaseImpl.kt
package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

class CreateOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val orderLevelConfig: OrderLevelConfig,
) : CreateOrderUseCase {
    override fun invoke(ownerUuid: UUID): Result<Order> {
        val existing = orderRepository.findByOwner(ownerUuid)
        if (existing != null) {
            return Result.failure("Order already exists for $ownerUuid")
        }
        
        val firstLevel = orderLevelConfig.levels.firstOrNull()
            ?: return Result.failure("No level configuration")
        
        val order = Order(
            ownerUuid = ownerUuid,
            level = firstLevel.level,
            radius = firstLevel.radius,
        )
        
        val id = orderRepository.insert(order)
        return Result.success(order.copy(id = id))
    }
}

// application/usecases/order/ActivateOrderUseCaseImpl.kt
class ActivateOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val overlapChecker: CheckOrderOverlapUseCase,
) : ActivateOrderUseCase {
    override fun invoke(ownerUuid: UUID, location: WorldLocation): Result<Unit> {
        val order = orderRepository.findByOwner(ownerUuid)
            ?: return Result.failure("Order not found")
        
        if (order.isActivated()) {
            return Result.failure("Order already activated")
        }
        
        // Check for overlaps
        val hasOverlap = overlapChecker(location.x, location.y, location.z, order.radius, location.world)
        if (hasOverlap) {
            return Result.failure("Order overlaps with another")
        }
        
        orderRepository.activate(
            ownerUuid,
            location.world,
            location.x,
            location.y,
            location.z,
        )
        
        return Result.success(Unit)
    }
}

// application/usecases/order/UpgradeOrderUseCaseImpl.kt
class UpgradeOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val workdaysRepository: WorkdaysRepository,
    private val orderLevelConfig: OrderLevelConfig,
) : UpgradeOrderUseCase {
    override fun invoke(ownerUuid: UUID): Result<Order> {
        val order = orderRepository.findByOwner(ownerUuid)
            ?: return Result.failure("Order not found")
        
        val currentLevel = order.level
        val maxLevel = orderLevelConfig.getMaxLevel()
        
        if (currentLevel >= maxLevel) {
            return Result.failure("Already at max level")
        }
        
        val nextLevel = currentLevel + 1
        val cost = orderLevelConfig.getCostForLevel(nextLevel)
        
        // Check workdays
        val balance = workdaysRepository.findByOwner(ownerUuid)
        if (balance == null || balance.amount < cost) {
            return Result.failure("Insufficient workdays: need $cost, have ${balance?.amount ?: 0}")
        }
        
        // Spend workdays
        workdaysRepository.decrement(ownerUuid, cost)
        
        // Upgrade order
        val newRadius = orderLevelConfig.getRadiusForLevel(nextLevel)
        orderRepository.updateLevel(ownerUuid, nextLevel, newRadius)
        
        return Result.success(order.copy(level = nextLevel, radius = newRadius))
    }
}

// application/usecases/order/DeleteOrderUseCaseImpl.kt
class DeleteOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val flagCleanupHelper: FlagCleanupHelper,  // Infrastructure dependency
) : DeleteOrderUseCase {
    override fun invoke(ownerUuid: UUID): Result<Unit> {
        val order = orderRepository.findByOwner(ownerUuid)
            ?: return Result.failure("Order not found")
        
        if (order.isActivated()) {
            // Cleanup flag in world
            flagCleanupHelper.cleanupOrder(order)  // This handles the infrastructure part
        }
        
        orderRepository.deleteByOwner(ownerUuid)
        return Result.success(Unit)
    }
}

// ... и т.д. для остальных Use Cases
```

### 3.3 Создать интерфейсы Use Cases для Commune

```kotlin
// application/usecases/commune/CreateCommuneUseCase.kt
interface CreateCommuneUseCase {
    operator fun invoke(name: String, ownerId: UUID): Result<Commune>
}

// application/usecases/commune/InviteMemberUseCase.kt
interface InviteMemberUseCase {
    operator fun invoke(communeId: UUID, inviteeId: UUID, inviterId: UUID): Result<CommuneInvitation>
}

// application/usecases/commune/AcceptInvitationUseCase.kt
interface AcceptInvitationUseCase {
    operator fun invoke(invitationId: UUID): Result<Unit>
}

// application/usecases/commune/RemoveMemberUseCase.kt
interface RemoveMemberUseCase {
    operator fun invoke(communeId: UUID, memberId: UUID): Result<Unit>
}

// application/usecases/commune/GetCommuneUseCase.kt
interface GetCommuneUseCase {
    operator fun invoke(communeId: UUID): Commune?
}

// application/usecases/commune/ListCommunesForMemberUseCase.kt
interface ListCommunesForMemberUseCase {
    operator fun invoke(memberId: UUID): List<Commune>
}

// application/usecases/commune/DisbandCommuneUseCase.kt
interface DisbandCommuneUseCase {
    operator fun invoke(communeId: UUID, requesterId: UUID): Result<Unit>
}
```

### 3.4 Реализовать Commune Use Cases

```kotlin
// application/usecases/commune/CreateCommuneUseCaseImpl.kt
class CreateCommuneUseCaseImpl(
    private val communeRepository: CommuneRepository,
) : CreateCommuneUseCase {
    override fun invoke(name: String, ownerId: UUID): Result<Commune> {
        if (name.isBlank()) {
            return Result.failure("Commune name cannot be blank")
        }
        
        if (name.length > 50) {
            return Result.failure("Commune name too long")
        }
        
        // Check uniqueness
        val existing = communeRepository.findByName(name)
        if (existing != null) {
            return Result.failure("Commune with name '$name' already exists")
        }
        
        val commune = Commune(
            name = name,
            ownerId = ownerId,
            memberIds = setOf(ownerId),  // Owner is member
        )
        
        val id = communeRepository.insert(commune)
        return Result.success(commune.copy(id = id))
    }
}

// application/usecases/commune/InviteMemberUseCaseImpl.kt
class InviteMemberUseCaseImpl(
    private val communeRepository: CommuneRepository,
    private val invitationRepository: CommuneInvitationRepository,
) : InviteMemberUseCase {
    override fun invoke(communeId: UUID, inviteeId: UUID, inviterId: UUID): Result<CommuneInvitation> {
        val commune = communeRepository.findById(communeId)
            ?: return Result.failure("Commune not found")
        
        // Check permission: only owner or member can invite
        if (commune.ownerId != inviterId && !commune.memberIds.contains(inviterId)) {
            return Result.failure("Not permitted to invite members")
        }
        
        if (commune.memberIds.contains(inviteeId)) {
            return Result.failure("Already a member")
        }
        
        val invitation = CommuneInvitation(
            communeId = communeId,
            inviteeId = inviteeId,
            inviterId = inviterId,
        )
        
        invitationRepository.insert(invitation)
        return Result.success(invitation)
    }
}

// ... остальные реализации
```

### 3.5 Создать интерфейсы Use Cases для Workdays

```kotlin
// application/usecases/workdays/IncrementWorkdaysUseCase.kt
interface IncrementWorkdaysUseCase {
    operator fun invoke(uuid: UUID, amount: Int): Result<Int>
}

// application/usecases/workdays/SpendWorkdaysUseCase.kt
interface SpendWorkdaysUseCase {
    operator fun invoke(uuid: UUID, amount: Int): Result<Boolean>
}

// application/usecases/workdays/GetWorkdaysBalanceUseCase.kt
interface GetWorkdaysBalanceUseCase {
    operator fun invoke(uuid: UUID): Int
}
```

### 3.6 Обновить ComminusmPlugin.kt: использовать Use Cases

```kotlin
override fun onEnable() {
    // Инициализация repositories и config
    val db = DatabaseManager(this)
    val orderRepository = OrderRepositoryImpl(db.connection)
    val communeRepository = CommuneRepositoryImpl(db.connection)
    val workdaysRepository = WorkdaysRepositoryImpl(db.connection)
    
    val orderLevelConfig = OrderLevelConfig(config)
    
    // Use Cases для Order
    val createOrderUseCase = CreateOrderUseCaseImpl(orderRepository, orderLevelConfig)
    val activateOrderUseCase = ActivateOrderUseCaseImpl(orderRepository, checkOrderOverlapUseCase)
    val upgradeOrderUseCase = UpgradeOrderUseCaseImpl(orderRepository, workdaysRepository, orderLevelConfig)
    val deleteOrderUseCase = DeleteOrderUseCaseImpl(orderRepository, flagCleanupHelper)
    
    // Use Cases для Commune
    val createCommuneUseCase = CreateCommuneUseCaseImpl(communeRepository)
    val inviteMemberUseCase = InviteMemberUseCaseImpl(communeRepository, invitationRepository)
    
    // Передать Use Cases в listeners/commands
    val partyCommand = PartyCommand(createOrderUseCase, activateOrderUseCase, upgradeOrderUseCase)
    val communeCommand = CommuneCommand(createCommuneUseCase, inviteMemberUseCase)
    
    getCommand("party")?.setExecutor(partyCommand)
    getCommand("commune")?.setExecutor(communeCommand)
}
```

### 3.7 Обновить старые сервисы: создать адаптеры (опционально)

Если есть старые сервисы типа OrderService, создать им адаптеры, которые делегируют Use Cases:

```kotlin
// Deprecated, используйте Use Cases напрямую
@Deprecated("Use OrderUseCase instead")
class OrderService(
    private val createOrderUseCase: CreateOrderUseCase,
    private val activateOrderUseCase: ActivateOrderUseCase,
    // ...
) {
    fun create(uuid: UUID): Order? = createOrderUseCase(uuid).getOrNull()
    fun activate(uuid: UUID, location: Location): Boolean = 
        activateOrderUseCase(uuid, adapter(location)).isSuccess
    // ...
}
```

---

## Критерии завершения

- [ ] Все интерфейсы Use Cases созданы в application/usecases/
- [ ] Все Use Cases реализованы в application/usecases/ (Impl классы)
- [ ] Каждый Use Case фокусирован на одной операции
- [ ] Конструкторы Use Cases ≤ 3-4 параметра
- [ ] Каждый файл Use Case ≤ 50-100 строк
- [ ] ComminusmPlugin создаёт и регистрирует Use Cases
- [ ] Listeners и Commands используют Use Cases
- [ ] Старые большие сервисы удалены или задепрекейчены
- [ ] Компиляция успешна: `./gradlew compileKotlin`
- [ ] Все тесты зелёные: `./gradlew test`
- [ ] Lint проходит: `./gradlew detekt ktlintCheck`

---

## Файлы, которые нужно создать

### Use Case interfaces:
- `application/usecases/order/CreateOrderUseCase.kt`
- `application/usecases/order/ActivateOrderUseCase.kt`
- `application/usecases/order/UpgradeOrderUseCase.kt`
- `application/usecases/order/DeleteOrderUseCase.kt`
- `application/usecases/order/GetOrderByOwnerUseCase.kt`
- `application/usecases/order/FindOrdersInWorldUseCase.kt`
- `application/usecases/order/CheckOrderOverlapUseCase.kt`
- `application/usecases/commune/CreateCommuneUseCase.kt`
- `application/usecases/commune/InviteMemberUseCase.kt`
- `application/usecases/commune/AcceptInvitationUseCase.kt`
- `application/usecases/commune/RemoveMemberUseCase.kt`
- `application/usecases/commune/GetCommuneUseCase.kt`
- `application/usecases/workdays/IncrementWorkdaysUseCase.kt`
- `application/usecases/workdays/SpendWorkdaysUseCase.kt`

### Use Case implementations (Impl):
- `application/usecases/order/*UseCaseImpl.kt` (7-8 файлов)
- `application/usecases/commune/*UseCaseImpl.kt` (6-7 файлов)
- `application/usecases/workdays/*UseCaseImpl.kt` (3 файла)

### Удалить/Задепрекейчить:
- `service/OrderService.kt` (Или сохранить как адаптер, если нужен для обратной совместимости)

---

## Потенциальные проблемы

| Проблема | Решение |
|----------|---------|
| Use Cases зависят друг от друга | Это нормально (например, AcceptInvitation может вызвать AddMemberToCommune). Главное — прямые зависимости, не циклические. |
| Много копирования кода между Use Cases | Создавать утилиты/helpers для повторяющейся логики |
| Как организовать общую бизнес-логику? | Создавать domain services (для логики, не для оркестрации) |

---

## Чек-лист реализации

- [ ] Создать все интерфейсы Use Cases
- [ ] Реализовать все Use Cases (Impl классы)
- [ ] Обновить ComminusmPlugin на создание Use Cases
- [ ] Обновить Commands и Listeners на использование Use Cases
- [ ] Запустить `./gradlew compileKotlin` — ошибок нет
- [ ] Запустить `./gradlew detekt ktlintCheck` — проходит
- [ ] Запустить `./gradlew test` — все тесты зелёные
- [ ] Протестировать игровую функциональность
- [ ] Git commit: "refactor: introduce application layer with Use Cases (Stage 03)"
