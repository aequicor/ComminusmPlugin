# Стадия 01: Infrastructure Repositories (Interfaces + Implementations)

**Цель:** Создать интерфейсы для всех репозиториев в domain слое, реализации перенести в infrastructure

**Масштаб:** ~5-6 интерфейсов, перенос 5+ реализаций

**Время:** ~2-3 часа

---

## Задачи

### 1.1 Создать domain/repositories/OrderRepository.kt (интерфейс)

```kotlin
package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.domain.entities.Order
import java.util.UUID

interface OrderRepository {
    fun findByOwner(uuid: UUID): Order?
    fun findById(id: Long): Order?
    fun findAllInWorld(world: String): List<Order>
    fun findAllActivated(): List<Order>
    fun insert(order: Order): Long
    fun update(order: Order)
    fun activate(uuid: UUID, world: String, x: Int, y: Int, z: Int)
    fun updateLevel(uuid: UUID, level: Int, radius: Int)
    fun deleteByOwner(uuid: UUID)
}
```

### 1.2 Создать domain/repositories/WorkFrontRepository.kt (интерфейс)

```kotlin
package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.domain.entities.WorkFront
import java.util.UUID

interface WorkFrontRepository {
    fun findByOwner(uuid: UUID): WorkFront?
    fun findById(id: Long): WorkFront?
    fun findAllInWorld(world: String): List<WorkFront>
    fun findAllActivated(): List<WorkFront>
    fun insert(front: WorkFront): Long
    fun update(front: WorkFront)
    fun activate(uuid: UUID, world: String, x: Int, y: Int, z: Int)
    fun deleteByOwner(uuid: UUID)
}
```

### 1.3 Создать domain/repositories/WorkdaysRepository.kt (интерфейс)

```kotlin
package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.domain.entities.WorkdaysBalance
import java.util.UUID

interface WorkdaysRepository {
    fun findByOwner(uuid: UUID): WorkdaysBalance?
    fun insert(balance: WorkdaysBalance): Long
    fun update(balance: WorkdaysBalance)
    fun increment(uuid: UUID, amount: Int)
    fun decrement(uuid: UUID, amount: Int)
}
```

### 1.4 Создать domain/repositories/CommuneRepository.kt (интерфейс)

```kotlin
package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.domain.entities.Commune
import java.util.UUID

interface CommuneRepository {
    fun findById(id: UUID): Commune?
    fun findByName(name: String): Commune?
    fun findAllByMember(memberId: UUID): List<Commune>
    fun insert(commune: Commune): UUID
    fun update(commune: Commune)
    fun delete(id: UUID)
}
```

### 1.5 Обновить storage/OrderRepository.kt → infrastructure/repositories/OrderRepositoryImpl.kt

Переместить весь код текущего OrderRepository в новый класс с реализацией интерфейса:

```kotlin
package ru.kyamshanov.comminusm.infrastructure.repositories

import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.entities.Order
import java.sql.Connection
import java.util.UUID

class OrderRepositoryImpl(private val connection: Connection) : OrderRepository {
    // Весь код из текущего src/main/kotlin/ru/kyamshanov/comminusm/storage/OrderRepository.kt
    override fun findByOwner(uuid: UUID): Order? {
        // ... текущая реализация ...
    }
    // ... другие методы ...
}
```

### 1.6 Обновить storage/WorkFrontRepository.kt → infrastructure/repositories/WorkFrontRepositoryImpl.kt

Аналогично 1.5 для WorkFront.

### 1.7 Обновить storage/WorkdaysRepository.kt → infrastructure/repositories/WorkdaysRepositoryImpl.kt

Аналогично 1.5 для Workdays.

### 1.8 Обновить ComminusmPlugin.kt: использовать интерфейсы

```kotlin
override fun onEnable() {
    val db = DatabaseManager(this)
    
    // Используем интерфейсы, создаём имплементации:
    val orderRepository: OrderRepository = OrderRepositoryImpl(db.connection)
    val frontRepository: WorkFrontRepository = WorkFrontRepositoryImpl(db.connection)
    val workdaysRepository: WorkdaysRepository = WorkdaysRepositoryImpl(db.connection)
    
    // Передаём интерфейсы в сервисы:
    val orderService = initializeOrderService(
        orderRepository,  // ← интерфейс, не конкретный класс
        // ...
    )
    // ...
}
```

### 1.9 Обновить OrderService.kt: использовать интерфейс

```kotlin
class OrderService(
    private val orderRepository: OrderRepository,  // ← интерфейс вместо класса
    // ...
) {
    // Весь код остаётся прежним
}
```

### 1.10 Обновить другие сервисы (WorkFrontService, WorkdaysService, etc)

Аналогично 1.9 — заменить конкретные классы репозиториев на интерфейсы.

### 1.11 Удалить старые файлы

```
rm src/main/kotlin/ru/kyamshanov/comminusm/storage/OrderRepository.kt
rm src/main/kotlin/ru/kyamshanov/comminusm/storage/WorkFrontRepository.kt
rm src/main/kotlin/ru/kyamshanov/comminusm/storage/WorkdaysRepository.kt
```

---

## Критерии завершения

- [ ] Все интерфейсы репозиториев созданы в domain/repositories/
- [ ] Реализации перенесены в infrastructure/repositories/ с суффиксом Impl
- [ ] Все зависимости обновлены на интерфейсы (no concrete classes)
- [ ] ComminusmPlugin создаёт имплементации, использует интерфейсы
- [ ] Компиляция успешна: `./gradlew compileKotlin`
- [ ] Все тесты зелёные (если есть): `./gradlew test`
- [ ] Lint проходит: `./gradlew detekt ktlintCheck`

---

## Потенциальные проблемы

| Проблема | Решение |
|----------|---------|
| Circular imports между domain и infrastructure | Domain НЕ импортирует infrastructure. Infrastructure импортирует domain — однонаправленно. |
| Текущий код имеет реализационные детали в интерфейсе | Очистить интерфейсы, оставить только контракт. Детали в Impl классе. |
| Сигнатуры методов не совпадают | Унифицировать под общий интерфейс. Переделать реализации. |
| Tests падают | Обновить тесты на использование интерфейсов + mock implementations. |

---

## Файлы, которые нужно создать/изменить

### Создать:
- `domain/repositories/OrderRepository.kt`
- `domain/repositories/WorkFrontRepository.kt`
- `domain/repositories/WorkdaysRepository.kt`
- `domain/repositories/CommuneRepository.kt`
- `infrastructure/repositories/OrderRepositoryImpl.kt`
- `infrastructure/repositories/WorkFrontRepositoryImpl.kt`
- `infrastructure/repositories/WorkdaysRepositoryImpl.kt`
- `infrastructure/repositories/CommuneRepositoryImpl.kt` (from commune/repository/)

### Изменить:
- `plugin/ComminusmPlugin.kt` — использовать интерфейсы
- `service/OrderService.kt` — обновить зависимость на интерфейс
- `service/WorkFrontService.kt` — обновить зависимость на интерфейс
- `service/WorkdaysService.kt` — обновить зависимость на интерфейс
- `commune/service/CommuneService.kt` — обновить зависимость на интерфейс

### Удалить:
- `storage/OrderRepository.kt` (переехал в infrastructure)
- `storage/WorkFrontRepository.kt` (переехал в infrastructure)
- `storage/WorkdaysRepository.kt` (переехал в infrastructure)
- `commune/repository/OrderMembersRepository.kt` (переехал в infrastructure)

---

## Чек-лист реализации

- [ ] Создать все интерфейсы в domain/repositories/
- [ ] Перенести реализации в infrastructure/repositories/
- [ ] Обновить все импорты в сервисах на интерфейсы
- [ ] Обновить ComminusmPlugin.kt на создание имплементаций через интерфейсы
- [ ] Запустить `./gradlew compileKotlin` — ошибок нет
- [ ] Запустить `./gradlew detekt ktlintCheck` — проходит
- [ ] Запустить `./gradlew test` — все тесты зелёные
- [ ] Протестировать игровую функциональность (создание ордеров, активация, etc)
- [ ] Git commit: "refactor: move repositories to interfaces (Stage 01)"
