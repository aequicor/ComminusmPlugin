# Стадия 02: Domain Layer — Entities + Value Objects (Bukkit-free)

**Цель:** Очистить domain от Bukkit API, перенести сущности в domain слой без framework зависимостей

**Масштаб:** ~8 entity/VO классов, очистка от Bukkit

**Время:** ~2 часа

---

## Задачи

### 2.1 Создать domain/entities/Order.kt (без Bukkit)

Перенести класс Order из model/ в domain/entities/, убрать Bukkit импорты:

```kotlin
package ru.kyamshanov.comminusm.domain.entities

import java.util.UUID

data class Order(
    val id: Long = 0,
    val ownerUuid: UUID,
    val level: Int = 1,
    val radius: Int = 2,
    val centerWorld: String? = null,  // ← String вместо World объекта
    val centerX: Int = 0,
    val centerY: Int = 0,
    val centerZ: Int = 0,
) {
    fun isActivated(): Boolean = centerWorld != null
    
    fun getLocation(): WorldLocation? {
        if (centerWorld == null) return null
        return WorldLocation(centerWorld, centerX, centerY, centerZ)
    }
}

// Value object для Location (Bukkit-free)
data class WorldLocation(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
)
```

### 2.2 Создать domain/entities/WorkFront.kt (без Bukkit)

Аналогично Order:

```kotlin
package ru.kyamshanov.comminusm.domain.entities

import java.util.UUID

data class WorkFront(
    val id: Long = 0,
    val ownerUuid: UUID,
    val radius: Int = 2,
    val centerWorld: String? = null,  // ← String, не World
    val centerX: Int = 0,
    val centerY: Int = 0,
    val centerZ: Int = 0,
) {
    fun isActivated(): Boolean = centerWorld != null
}
```

### 2.3 Создать domain/entities/Commune.kt (без Bukkit)

```kotlin
package ru.kyamshanov.comminusm.domain.entities

import java.util.UUID

data class Commune(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val ownerId: UUID,
    val memberIds: Set<UUID> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
)
```

### 2.4 Создать domain/value_objects/OrderMember.kt

```kotlin
package ru.kyamshanov.comminusm.domain.value_objects

import java.util.UUID

data class OrderMember(
    val uuid: UUID,
    val orderId: Long,
    val joinedAt: Long = System.currentTimeMillis(),
)
```

### 2.5 Создать domain/value_objects/CommuneInvitation.kt

```kotlin
package ru.kyamshanov.comminusm.domain.value_objects

import java.util.UUID

data class CommuneInvitation(
    val id: UUID = UUID.randomUUID(),
    val communeId: UUID,
    val inviteeId: UUID,
    val inviterId: UUID,
    val createdAt: Long = System.currentTimeMillis(),
)
```

### 2.6 Создать domain/value_objects/WorkdaysBalance.kt

```kotlin
package ru.kyamshanov.comminusm.domain.value_objects

import java.util.UUID

data class WorkdaysBalance(
    val uuid: UUID,
    val amount: Int = 0,
)
```

### 2.7 Создать domain/exceptions/ (Domain-specific exceptions)

```kotlin
// domain/exceptions/OrderNotFoundException.kt
package ru.kyamshanov.comminusm.domain.exceptions

import java.util.UUID

class OrderNotFoundException(uuid: UUID) : Exception("Order for $uuid not found")

// domain/exceptions/CommuneNotFoundException.kt
class CommuneNotFoundException(id: String) : Exception("Commune $id not found")

// domain/exceptions/InsufficientFundsException.kt
class InsufficientFundsException(required: Int, available: Int) :
    Exception("Insufficient workdays: required=$required, available=$available")
```

### 2.8 Создать domain/events/ (Domain events)

```kotlin
// domain/events/OrderCreatedEvent.kt
package ru.kyamshanov.comminusm.domain.events

import java.util.UUID

data class OrderCreatedEvent(
    val orderId: Long,
    val ownerUuid: UUID,
    val timestamp: Long = System.currentTimeMillis(),
)

// domain/events/OrderActivatedEvent.kt
data class OrderActivatedEvent(
    val orderId: Long,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
)

// domain/events/CommuneCreatedEvent.kt
data class CommuneCreatedEvent(
    val communeId: UUID,
    val communeName: String,
    val ownerId: UUID,
)
```

### 2.9 Обновить model/Order.kt → перенести в domain

Удалить старый Order.kt из model/, проверить все импорты обновлены на domain.entities.Order.

### 2.10 Обновить все сервисы на новые domain entities

```kotlin
// Было:
import ru.kyamshanov.comminusm.model.Order

// Стало:
import ru.kyamshanov.comminusm.domain.entities.Order
```

### 2.11 Создать инфраструктурные адаптеры для Bukkit

Создать адаптеры, которые конвертируют между Domain и Bukkit:

```kotlin
// infrastructure/adapters/BukkitLocationAdapter.kt
package ru.kyamshanov.comminusm.infrastructure.adapters

import org.bukkit.Bukkit
import org.bukkit.Location
import ru.kyamshanov.comminusm.domain.entities.WorldLocation

object BukkitLocationAdapter {
    fun toDomain(location: Location): WorldLocation {
        return WorldLocation(
            world = location.world!!.name,
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
        )
    }
    
    fun toBukkit(worldLocation: WorldLocation): Location? {
        val world = Bukkit.getWorld(worldLocation.world) ?: return null
        return Location(world, worldLocation.x.toDouble(), worldLocation.y.toDouble(), worldLocation.z.toDouble())
    }
}
```

### 2.12 Обновить Presentation слой на использование адаптеров

Например, в listeners:

```kotlin
// Было:
fun activate(uuid: UUID, location: Location): Boolean {
    val world = location.world!!.name  // ← Bukkit API in business logic
}

// Стало:
fun activate(uuid: UUID, location: Location): Boolean {
    val worldLocation = BukkitLocationAdapter.toDomain(location)
    activateOrderUseCase(uuid, worldLocation)  // ← domain-only operation
}
```

---

## Критерии завершения

- [ ] Order.kt перенесён в domain/entities/, no Bukkit API
- [ ] WorkFront.kt в domain/entities/, no Bukkit API
- [ ] Commune.kt в domain/entities/, no Bukkit API
- [ ] Value Objects созданы в domain/value_objects/
- [ ] Domain exceptions созданы
- [ ] Domain events созданы
- [ ] BukkitLocationAdapter и другие адаптеры созданы в infrastructure/adapters/
- [ ] Все импорты обновлены на domain entities
- [ ] Domain слой НЕ импортирует bukkit, persistence, application, presentation
- [ ] Компиляция успешна: `./gradlew compileKotlin`
- [ ] Все тесты зелёные: `./gradlew test`
- [ ] Lint проходит: `./gradlew detekt ktlintCheck`

---

## Файлы, которые нужно создать

### Создать:
- `domain/entities/Order.kt`
- `domain/entities/WorkFront.kt`
- `domain/entities/Commune.kt`
- `domain/value_objects/OrderMember.kt`
- `domain/value_objects/CommuneInvitation.kt`
- `domain/value_objects/WorkdaysBalance.kt`
- `domain/value_objects/Result.kt` (если не существует)
- `domain/exceptions/OrderNotFoundException.kt`
- `domain/exceptions/CommuneNotFoundException.kt`
- `domain/exceptions/InsufficientFundsException.kt`
- `domain/events/OrderCreatedEvent.kt`
- `domain/events/OrderActivatedEvent.kt`
- `domain/events/CommuneCreatedEvent.kt`
- `infrastructure/adapters/BukkitLocationAdapter.kt`
- `infrastructure/adapters/BukkitWorldAdapter.kt` (если нужен)

### Удалить:
- `model/Order.kt` (переехал в domain/entities/)
- `model/WorkFront.kt` (переехал в domain/entities/)
- Старые исключения из других мест

### Изменить:
- Все импорты в сервисах — на domain entities
- Все импорты в presentation — на domain entities + адаптеры

---

## Потенциальные проблемы

| Проблема | Решение |
|----------|---------|
| Сервисы получают Location, но нужен WorldLocation | Использовать адаптер в presentation слое перед вызовом use case |
| Domain entities нужно сохранять в DB | Repository impl хранит как есть, адаптирует при сохранении/загрузке |
| Старый код использует Location напрямую | Этот код должен быть в presentation слое, он использует адаптеры |

---

## Чек-лист реализации

- [ ] Создать domain/entities/ с Order, WorkFront, Commune
- [ ] Создать domain/value_objects/ с OrderMember, etc
- [ ] Создать domain/exceptions/ и domain/events/
- [ ] Создать infrastructure/adapters/ для Bukkit конверсии
- [ ] Обновить все импорты в сервисах и presentation
- [ ] Запустить `./gradlew compileKotlin` — ошибок нет
- [ ] Запустить `./gradlew detekt ktlintCheck` — проходит
- [ ] Запустить `./gradlew test` — все тесты зелёные
- [ ] Проверить: domain слой НЕ импортирует bukkit.*, persistence.*, application.*, presentation.*
- [ ] Git commit: "refactor: clean domain layer from Bukkit API (Stage 02)"
