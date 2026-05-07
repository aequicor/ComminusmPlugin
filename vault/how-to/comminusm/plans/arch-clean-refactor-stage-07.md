# Stage 07: Refactor GUI + Commands (Presentation Layer)

**Цель:** Рефакторить все GUI компоненты и команды так, чтобы они зависели от Use Case интерфейсов вместо конкретных сервисов (OrderService, WorkFrontService, WorkdaysService).

**Принцип:** Dependency Inversion — высокоуровневые модули (GUI/Commands) зависят от абстракций (Use Case interfaces), не от низкоуровневых реализаций (Services).

---

## Обзор

На этой стадии будут отрефакторены:

### Команды (4 файла)
1. **PartyCommand** — создание/управление орденом
2. **CommuneCommand** — управление коммуной
3. **OrderCommuneInfoCommand** — получение информации об ордене в коммуне
4. **DelegatingCommandExecutor** — делегирующий исполнитель

### GUI Компоненты (10 файлов)
1. **PartyMenu** — меню партийных услуг
2. **AdminMenu** — админ-меню
3. **OrderMenu** — меню ордена
4. **CommuneMenu** — меню коммуны
5. **CommuneOrderMenu** — меню ордена в коммуне
6. **CommunePartyMenu** — меню партии в коммуне
7. **OrderMembersMenu** — меню членов ордена
8. **FrontMenu** — меню фронта
9. **TreasuryMenu** — меню казны
10. **CommunePartyMenu** — меню партии коммуны

---

## Текущие зависимости

**Команды и GUI зависят от 3 сервисов:**

```kotlin
// Текущее состояние:
class PartyCommand(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,      // ← конкретный класс
    private val workFrontService: WorkFrontService?,  // ← конкретный класс
)

class PartyMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,    // ← конкретный класс
    private val orderService: OrderService?,          // ← конкретный класс
    private val workFrontService: WorkFrontService?,  // ← конкретный класс
)
```

**Методы, вызываемые из GUI/Commands:**

На `orderService`:
- `findByOwner(uuid)` — получить ордер по владельцу
- `getOrderById(id)` — получить ордер по ID
- `create(uuid)` — создать новый ордер
- `upgrade(uuid)` — улучшить ордер
- `isLeader(uuid, orderId)` — проверить лидерство
- `getCostForLevel(level)` — стоимость уровня
- `getMaxLevel()` — макс уровень
- `getRadiusForLevel(level)` — радиус для уровня

На `workdaysService`:
- `getBalance(uuid)` — получить баланс рабочих дней
- `spend(uuid, amount)` — потратить рабочие дни
- `increment(uuid, amount)` — добавить рабочие дни

На `workFrontService`:
- `getByOwner(uuid)` — получить фронт по владельцу
- `create(uuid)` — создать новый фронт
- `delete(uuid)` — удалить фронт

---

## Таблица соответствия: ServiceMethod → UseCase

| Service | Method | GUI/Command | → | Required UseCase |
|---------|--------|-------------|---|------------------|
| OrderService | findByOwner(uuid) | OrderMenu, PartyMenu | → | GetOrderByOwnerUseCase |
| OrderService | getOrderById(id) | CommuneMenu | → | GetOrderByIdUseCase |
| OrderService | create(uuid) | PartyMenu | → | CreateOrderUseCase |
| OrderService | upgrade(uuid) | OrderMenu | → | UpgradeOrderUseCase |
| OrderService | isLeader(uuid, orderId) | CommuneMenu, CommuneOrderMenu, ... | → | CheckOrderLeadershipUseCase |
| OrderService | getCostForLevel(level) | OrderMenu | → | GetOrderCostForLevelUseCase |
| OrderService | getMaxLevel() | OrderMenu | → | GetMaxOrderLevelUseCase |
| OrderService | getRadiusForLevel(level) | OrderMenu | → | GetRadiusForLevelUseCase |
| WorkdaysService | getBalance(uuid) | TreasuryMenu | → | GetWorkdaysBalanceUseCase |
| WorkdaysService | spend(uuid, amount) | OrderMenu, ... | → | SpendWorkdaysUseCase |
| WorkdaysService | increment(uuid, amount) | ... | → | IncrementWorkdaysUseCase |
| WorkFrontService | getByOwner(uuid) | PartyMenu, AdminMenu | → | GetWorkFrontByOwnerUseCase |
| WorkFrontService | create(uuid) | PartyMenu | → | CreateWorkFrontUseCase |
| WorkFrontService | delete(uuid) | ... | → | DeleteWorkFrontUseCase |

---

## Шаги реализации

### Шаг 1: Проверить существование Use Cases

Проверить, что следующие Use Cases уже созданы:
- `GetOrderByOwnerUseCase` (interface + impl)
- `GetOrderByIdUseCase` (interface + impl)
- `CreateOrderUseCase` (interface + impl)
- `UpgradeOrderUseCase` (interface + impl)
- `CheckOrderLeadershipUseCase` (interface + impl)
- `GetOrderCostForLevelUseCase` (interface + impl)
- `GetMaxOrderLevelUseCase` (interface + impl)
- `GetRadiusForLevelUseCase` (interface + impl)
- `GetWorkdaysBalanceUseCase` (interface + impl)
- `SpendWorkdaysUseCase` (interface + impl)
- `IncrementWorkdaysUseCase` (interface + impl)
- `GetWorkFrontByOwnerUseCase` (interface + impl)
- `CreateWorkFrontUseCase` (interface + impl)
- `DeleteWorkFrontUseCase` (interface + impl)

Если отсутствуют - создать перед рефакторингом GUI/Commands.

### Шаг 2: Рефакторить команды (4 файла)

Для каждой команды (PartyCommand, CommuneCommand, OrderCommuneInfoCommand, DelegatingCommandExecutor):

1. Заменить конкретные сервисы на Use Case интерфейсы в конструкторе
2. Обновить все вызовы сервисов на вызовы Use Cases
3. Обновить инъекцию в DIContainer.kt
4. Обновить тесты для использования мок-объектов Use Cases

**Пример рефакторинга PartyCommand:**

```kotlin
// БЫЛО:
class PartyCommand(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?,
)

// СТАЛО:
class PartyCommand(
    private val config: PluginConfig,
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val createOrderUseCase: CreateOrderUseCase,
    private val getWorkFrontByOwnerUseCase: GetWorkFrontByOwnerUseCase,
    private val createWorkFrontUseCase: CreateWorkFrontUseCase,
)
```

### Шаг 3: Рефакторить GUI компоненты (10 файлов)

Для каждого GUI компонента (PartyMenu, AdminMenu, OrderMenu, и т.д.):

1. Заменить конкретные сервисы на Use Case интерфейсы в конструкторе
2. Обновить все вызовы методов сервисов на вызовы Use Cases
3. Обновить инъекцию в DIContainer.kt
4. Обновить тесты (если есть)

**Пример рефакторинга PartyMenu:**

```kotlin
// БЫЛО:
class PartyMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?,
)

fun open(player: Player) {
    val hasOrder = orderService?.findByOwner(uuid) != null
    val hasFront = workFrontService?.getByOwner(uuid) != null
}

// СТАЛО:
class PartyMenu(
    private val config: PluginConfig,
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val getWorkFrontByOwnerUseCase: GetWorkFrontByOwnerUseCase,
)

fun open(player: Player) {
    val hasOrder = getOrderByOwnerUseCase(uuid) != null
    val hasFront = getWorkFrontByOwnerUseCase(uuid) != null
}
```

### Шаг 4: Обновить DIContainer.kt

Убедиться, что все Use Case экземпляры инъектируются корректно в конструкторы команд и GUI компонентов:

```kotlin
val partyCommand by lazy {
    PartyCommand(
        pluginConfig,
        getOrderByOwnerUseCase,
        createOrderUseCase,
        getWorkFrontByOwnerUseCase,
        createWorkFrontUseCase,
    )
}

val partyMenu by lazy {
    PartyMenu(
        pluginConfig,
        getOrderByOwnerUseCase,
        getWorkFrontByOwnerUseCase,
    )
}
```

### Шаг 5: Компиляция и тесты

1. Запустить `./gradlew compileKotlin` — должна быть без ошибок
2. Запустить `./gradlew test` — все тесты должны пройти
3. Запустить `./gradlew detekt ktlintCheck` — 0 нарушений

---

## Критерии завершения

- [x] Все 4 команды обновлены на Use Cases
- [x] Все 10 GUI компонентов обновлены на Use Cases
- [x] DIContainer.kt обновлён с правильными инъекциями
- [x] Компиляция успешна (./gradlew compileKotlin)
- [x] Все тесты проходят (./gradlew test)
- [x] Lint без нарушений (detekt + ktlint)
- [x] Все GUI/Commands зависят ТОЛЬКО от Use Cases, не от Services

---

## Риски и смягчение

| Риск | Вероятность | Смягчение |
|------|-------------|----------|
| Недостающие Use Cases | СРЕДНЯЯ | Перед рефакторингом проверить все требуемые Use Cases созданы |
| Нарушение сигнатуры Use Cases | СРЕДНЯЯ | Аккуратно следовать таблице соответствия выше |
| Тесты не обновлены | СРЕДНЯЯ | Обновить все параметры конструкторов в тестах |
| Нарушение зависимостей в DIContainer | ВЫСОКАЯ | Компиляция сразу покажет проблему; проверить инъекции для всех GUI |

---

## Следующие шаги

После завершения Stage 07:
- Перейти к Stage 08 (Unit + Integration Tests) — написать полное покрытие для Use Cases и Repository implementations
- Закончить полный CI/CD pipeline
