# Clean Architecture + SOLID Рефакторинг — План

**Модуль:** comminusm  
**Тип:** TECH (архитектурный рефакторинг)  
**Дата:** 2026-05-07  
**Статус:** Планирование  

---

## 1. Цель

Переструктурировать весь проект с соблюдением:
- **Clean Architecture** (4 слоя: Domain → Application → Infrastructure → Presentation)
- **SOLID принципы** (особенно Dependency Inversion)
- **Тестируемость** (все сервисы и репозитории имеют интерфейсы)

**Результат:**  
Проект будет иметь четкие слои, интерфейсы для всех сервисов, инверсию зависимостей, отсутствие God Classes и высокую тестируемость.

---

## 2. Текущие архитектурные проблемы

### 2.1 God Class: ComminusmPlugin (471 строка)

```kotlin
class ComminusmPlugin : JavaPlugin() {
    // Создаёт всех:
    val db = DatabaseManager(this)
    val orderRepo = OrderRepository(db.connection)
    val pluginConfig = PluginConfig(config)
    // ... 40 more lines ...
    
    // Регистрирует всех:
    server.pluginManager.registerEvents(OrderFlagListener(...), this)
    server.pluginManager.registerEvents(BlockListener(...), this)
    // ... 20+ listeners ...
}
```

**Проблемы:**
- 471 строка одного файла
- Знает о всех классах в проекте
- Нарушает Single Responsibility
- Невозможно тестировать

### 2.2 Отсутствие интерфейсов

```kotlin
// Текущее состояние:
class OrderService(orderRepository: OrderRepository, ...)  // конкретный класс
class OrderRepository(connection: Connection)  // конкретный класс

// Проблема: невозможно мокировать в тестах
val mockRepo = mockk<OrderRepository>()  // ошибка! это конкретный класс
```

### 2.3 Нарушение Dependency Inversion

Высокоуровневые модули (OrderService) зависят от низкоуровневых (OrderRepository):

```kotlin
class OrderService(
    private val orderRepository: OrderRepository,  // ← конкретная класс!
    // ...
)
```

Должно быть:

```kotlin
interface OrderRepository {  // ← абстракция
    fun findByOwner(uuid: UUID): Order?
    // ...
}

class OrderService(
    private val orderRepository: OrderRepository,  // ← интерфейс (Dependency Inversion)
)
```

### 2.4 Смешивание слоёв: Domain + Framework

```kotlin
class OrderService {
    fun activate(uuid: UUID, location: Location): Boolean {  // ← Bukkit API in Domain!
        val world = location.world!!.name  // ← нарушение
        val chunk = world.getChunkAt(x, z)  // ← нарушение
    }
}
```

Domain слой НЕ должен знать о Bukkit. Эти детали — для Presentation слоя.

### 2.5 Отсутствие Use Cases

Сервисы содержат слишком много:

```kotlin
class OrderService(
    private val orderRepository: OrderRepository,
    private val levels: List<OrderLevelConfig>,
    private val workdaysService: WorkdaysService?,
    private val minDistanceBetweenCenters: Int,
    private val chunkCacheManager: ChunkCacheManager? = null,
    private val flagCleanupHelper: FlagCleanupHelper? = null,
    private val flagStabilityManager: FlagStabilityManager? = null,
    private val plugin: org.bukkit.plugin.Plugin? = null,
) {
    fun create(uuid: UUID): Order?
    fun activate(uuid: UUID, location: Location): Boolean
    fun upgrade(uuid: UUID): Boolean
    fun deleteByOwner(uuid: UUID)
    // ... 10+ more methods
}
```

Это должны быть отдельные Use Cases:
- `CreateOrderUseCase`
- `ActivateOrderUseCase`
- `UpgradeOrderUseCase`
- `DeleteOrderUseCase`

---

## 3. Целевая архитектура

### 3.1 Слоистая архитектура

```
┌─────────────────────────────────────────────────────┐
│   PRESENTATION LAYER                                 │
│   ├── listeners/ (event handlers)                   │
│   ├── gui/ (menu systems)                           │
│   ├── commands/ (command executors)                 │
│   └── adapters/ (Bukkit API wrappers)              │
│                                                      │
│   Зависит от: Application, Infrastructure          │
│   Содержит: Bukkit API, event handling, GUI logic   │
└──────────────────────┬──────────────────────────────┘
                       │ зависит от
┌──────────────────────┴──────────────────────────────┐
│   APPLICATION LAYER                                 │
│   ├── usecases/ (бизнес-операции)                  │
│   └── services/ (оркестрация, composition)          │
│                                                      │
│   Зависит от: Domain                                │
│   Содержит: Orchestration, no business rules        │
└──────────────────────┬──────────────────────────────┘
                       │ зависит от
┌──────────────────────┴──────────────────────────────┐
│   DOMAIN LAYER                                       │
│   ├── entities/ (Order, Commune, Front, etc)        │
│   ├── value-objects/ (Result, OrderMember, etc)     │
│   ├── repositories/ (interfaces only!)              │
│   ├── events/ (domain events)                       │
│   └── exceptions/ (domain exceptions)               │
│                                                      │
│   Зависит от: НИЧЕГО (чистый Kotlin)               │
│   Содержит: Business rules, no framework code       │
└──────────────────────┬──────────────────────────────┘
                       │ зависит от
┌──────────────────────┴──────────────────────────────┐
│   INFRASTRUCTURE LAYER                               │
│   ├── repositories/ (SQL, in-memory implementations) │
│   ├── adapters/ (Bukkit adapters, API clients)      │
│   ├── config/ (PluginConfig, etc)                   │
│   └── persistence/ (DatabaseManager, etc)           │
│                                                      │
│   Зависит от: Domain, Application                   │
│   Содержит: Framework integration, SQL, etc         │
└─────────────────────────────────────────────────────┘
```

### 3.2 Структура папок

```
src/main/kotlin/ru/kyamshanov/comminusm/
├── domain/                          # ← NO framework imports!
│   ├── entities/
│   │   ├── Order.kt
│   │   ├── Commune.kt
│   │   ├── WorkFront.kt
│   │   └── ...
│   ├── value_objects/
│   │   ├── OrderMember.kt
│   │   ├── CommuneInvitation.kt
│   │   └── ...
│   ├── repositories/                # ← INTERFACES ONLY
│   │   ├── OrderRepository.kt       # interface
│   │   ├── CommuneRepository.kt     # interface
│   │   └── ...
│   ├── events/
│   │   ├── OrderCreatedEvent.kt
│   │   └── ...
│   └── exceptions/
│       ├── OrderNotFound.kt
│       └── ...
│
├── application/                     # ← Зависит от domain
│   ├── usecases/
│   │   ├── order/
│   │   │   ├── CreateOrderUseCase.kt
│   │   │   ├── ActivateOrderUseCase.kt
│   │   │   ├── UpgradeOrderUseCase.kt
│   │   │   └── DeleteOrderUseCase.kt
│   │   ├── commune/
│   │   │   ├── CreateCommuneUseCase.kt
│   │   │   ├── InviteMemberUseCase.kt
│   │   │   └── ...
│   │   └── ...
│   └── services/
│       ├── ApplicationService.kt    # (если нужна оркестрация)
│       └── ...
│
├── infrastructure/                  # ← Реализует domain интерфейсы
│   ├── repositories/
│   │   ├── OrderRepositoryImpl.kt
│   │   ├── CommuneRepositoryImpl.kt
│   │   └── ...
│   ├── persistence/
│   │   ├── DatabaseManager.kt
│   │   └── ...
│   ├── adapters/
│   │   ├── BukkitLocationAdapter.kt
│   │   ├── BukkitChunkAdapter.kt
│   │   └── ...
│   └── config/
│       ├── PluginConfig.kt
│       └── ...
│
├── presentation/                    # ← Зависит от application
│   ├── listeners/
│   │   ├── PlayerListener.kt
│   │   ├── BlockListener.kt
│   │   └── ...
│   ├── gui/
│   │   ├── OrderMenu.kt
│   │   ├── CommuneMenu.kt
│   │   └── ...
│   ├── commands/
│   │   ├── PartyCommand.kt
│   │   ├── CommuneCommand.kt
│   │   └── ...
│   └── plugin/
│       └── ComminusmPlugin.kt       # ← Точка входа (инжектор)
│
└── di/                              # ← Dependency Injection контейнер
    └── DIContainer.kt              # Создаёт и инжектирует зависимости
```

### 3.3 Пример рефакторинга: OrderService → Use Cases

**До:**
```kotlin
class OrderService(
    private val orderRepository: OrderRepository,
    private val levels: List<OrderLevelConfig>,
    private val workdaysService: WorkdaysService?,
    private val minDistanceBetweenCenters: Int,
    private val chunkCacheManager: ChunkCacheManager? = null,
    private val flagCleanupHelper: FlagCleanupHelper? = null,
    private val flagStabilityManager: FlagStabilityManager? = null,
    private val plugin: org.bukkit.plugin.Plugin? = null,
) {
    fun create(uuid: UUID): Order?
    fun activate(uuid: UUID, location: Location): Boolean
    fun upgrade(uuid: UUID): Boolean
    fun deleteByOwner(uuid: UUID)
}
```

**После:**
```kotlin
// Domain layer — interfaces (ничего не зависит от этого)
interface OrderRepository {
    fun findByOwner(uuid: UUID): Order?
    fun findById(id: Long): Order?
    fun insert(order: Order): Long
    fun update(order: Order)
    fun delete(id: Long)
}

// Application layer — Use Cases (зависит от Domain)
interface CreateOrderUseCase {
    operator fun invoke(ownerId: UUID): Result<Order>
}

class CreateOrderUseCaseImpl(
    private val orderRepository: OrderRepository,  // ← interface
    private val orderFactory: OrderFactory,         # ← factory
) : CreateOrderUseCase {
    override fun invoke(ownerId: UUID): Result<Order> {
        val existing = orderRepository.findByOwner(ownerId)
        if (existing != null) return Result.failure("Order already exists")
        
        val order = orderFactory.createNewOrder(ownerId)
        val id = orderRepository.insert(order)
        return Result.success(order.copy(id = id))
    }
}

// Infrastructure layer — implementations
class OrderRepositoryImpl(private val connection: Connection) : OrderRepository {
    override fun findByOwner(uuid: UUID): Order? {
        // SQL query
    }
}

// Presentation layer — Commands
class PartyCommand(
    private val createOrderUseCase: CreateOrderUseCase,  # ← инжектируется
) : CommandExecutor {
    override fun onCommand(...): Boolean {
        val player = sender as? Player ?: return false
        val result = createOrderUseCase(player.uniqueId)
        // handle result
        return true
    }
}
```

### 3.4 Dependency Injection

**DIContainer.kt**
```kotlin
class DIContainer(private val plugin: ComminusmPlugin) {
    
    // Persistence
    private val db by lazy { DatabaseManager(plugin) }
    private val connection by lazy { db.connection }
    
    // Domain repositories (interfaces)
    val orderRepository: OrderRepository by lazy {
        OrderRepositoryImpl(connection)
    }
    
    val communeRepository: CommuneRepository by lazy {
        CommuneRepositoryImpl(connection)
    }
    
    // Application - Use Cases
    val createOrderUseCase: CreateOrderUseCase by lazy {
        CreateOrderUseCaseImpl(orderRepository)
    }
    
    val activateOrderUseCase: ActivateOrderUseCase by lazy {
        ActivateOrderUseCaseImpl(orderRepository, orderValidator)
    }
    
    // Presentation - Commands
    val partyCommand: PartyCommand by lazy {
        PartyCommand(createOrderUseCase, activateOrderUseCase)
    }
}

// Usage in ComminusmPlugin
class ComminusmPlugin : JavaPlugin() {
    private val di by lazy { DIContainer(this) }
    
    override fun onEnable() {
        // Регистрируем только самое необходимое:
        getCommand("party")?.setExecutor(di.partyCommand)
        server.pluginManager.registerEvents(di.playerListener, this)
        // ...
    }
}
```

---

## 4. Стадии реализации

### Стадия 01: Infrastructure слой (Repositories)

**Цель:** Создать интерфейсы для всех репозиториев и перенести реализации в infrastructure

**Файлы:**
- `domain/repositories/*.kt` — интерфейсы (NO реализации)
- `infrastructure/repositories/*Impl.kt` — реализации

**Критерии завершения:**
- [ ] Все текущие репозитории имеют интерфейсы в domain
- [ ] Реализации перенесены в infrastructure
- [ ] Все зависимости обновлены на интерфейсы
- [ ] Компиляция успешна
- [ ] Тесты зелёные

---

### Стадия 02: Domain слой (Entities + Value Objects)

**Цель:** Очистить domain от Bukkit API, перенести entity definitions в domain

**Файлы:**
- `domain/entities/*.kt` — Order, Commune, Front, etc (без Bukkit)
- `domain/value_objects/*.kt` — OrderMember, CommuneInvitation, Result

**Критерии завершения:**
- [ ] Все сущности в domain/entities, no Bukkit API
- [ ] Value Objects переведены в domain/value_objects
- [ ] Exceptions в domain/exceptions
- [ ] Domain слой не импортирует bukkit, persistence, application, presentation пакеты

---

### Стадия 03: Application слой (Use Cases)

**Цель:** Разбить große сервисы на маленькие Use Cases

**Файлы:**
- `application/usecases/order/*.kt` — CreateOrderUseCase, ActivateOrderUseCase, etc
- `application/usecases/commune/*.kt` — CreateCommuneUseCase, InviteMemberUseCase, etc
- `application/usecases/work/*.kt` — UpdateWorkdaysUseCase, etc

**Критерии завершения:**
- [ ] OrderService разбит на 5+ Use Cases
- [ ] CommuneService разбит на 4+ Use Cases
- [ ] Все Use Cases имеют интерфейсы
- [ ] Конструкторы Use Cases ≤ 3-4 параметра
- [ ] Каждый UC файл ≤ 100 строк

---

### Стадия 04: Infrastructure слой (Adapters + Config)

**Цель:** Создать адаптеры для Bukkit API, настроить конфигурацию

**Файлы:**
- `infrastructure/adapters/*.kt` — BukkitLocationAdapter, BukkitWorldAdapter, etc
- `infrastructure/config/*.kt` — PluginConfig, moved to infrastructure

**Критерии завершения:**
- [ ] Bukkit API обёрнут в адаптеры
- [ ] Domain не импортирует Bukkit
- [ ] Infrastructure имеет доступ к Bukkit

---

### Стадия 05: DI Container + Plugin (Presentation)

**Цель:** Создать DIContainer для инъекции зависимостей, упростить ComminusmPlugin

**Файлы:**
- `di/DIContainer.kt` — Создание всех зависимостей
- `presentation/plugin/ComminusmPlugin.kt` — Упрощённая, только инициализация

**Критерии завершения:**
- [ ] DIContainer создаёт все зависимости
- [ ] ComminusmPlugin ≤ 100 строк
- [ ] Все listeners регистрируются через di
- [ ] Все команды регистрируются через di
- [ ] Компиляция и работа успешны

---

### Стадия 06: Refactor Listeners (Presentation)

**Цель:** Рефакторить listeners, чтобы они зависели от Use Cases вместо старых сервисов

**Файлы:**
- `presentation/listeners/*.kt` — Обновить на новые Use Cases

**Критерии завершения:**
- [ ] Все listeners обновлены на новую архитектуру
- [ ] Listeners используют Use Cases (interfaces)
- [ ] Тестируемы (можно мокировать)

---

### Стадия 07: Refactor GUI + Commands (Presentation)

**Цель:** Рефакторить GUI и команды на новые Use Cases

**Файлы:**
- `presentation/gui/*.kt` — Обновить
- `presentation/commands/*.kt` — Обновить

**Критерии завершения:**
- [ ] Все GUI обновлены
- [ ] Все команды обновлены
- [ ] Компиляция + функциональность работают

---

### Стадия 08: Unit + Integration Tests

**Цель:** Написать тесты для Use Cases, Repository implementations

**Файлы:**
- `test/kotlin/application/usecases/**/*Test.kt` — Unit тесты Use Cases
- `test/kotlin/infrastructure/repositories/**/*Test.kt` — Integration тесты

**Критерии завершения:**
- [ ] Unit тесты для всех Use Cases
- [ ] Mock repositories используются
- [ ] Integration тесты для repositories
- [ ] Coverage > 70%

---

## 5. Метрики успеха

После завершения рефакторинга проект должен иметь:

- [ ] **Слоистая архитектура:** 4 четких слоя (Domain, Application, Infrastructure, Presentation)
- [ ] **100% интерфейсов:** Все сервисы и репозитории имеют интерфейсы
- [ ] **Инверсия зависимостей:** Высокоуровневые модули НЕ зависят от низкоуровневых
- [ ] **Чистый Domain:** Никаких Bukkit API, никаких framework imports в domain
- [ ] **Малые классы:** Никаких классов > 300 строк, идеально ≤ 150
- [ ] **DI контейнер:** Одно место для создания зависимостей
- [ ] **Тестируемость:** Все Use Cases и Repositories можно мокировать
- [ ] **Single Responsibility:** Каждый класс имеет одну ответственность
- [ ] **SOLID соблюдение:** S, O, L, I, D все соблюдены
- [ ] **Документация:** Архитектурные решения описаны в DECISIONS.md

---

## 6. Риски и смягчение

| Риск | Вероятность | Влияние | Смягчение |
|------|-------------|--------|----------|
| Breaking changes в Bukkit API | Средняя | Высокое | Адаптеры для Bukkit абстрагируют API |
| Масштаб рефакторинга слишком большой | Высокая | Высокое | Инкрементальный рефакторинг по стадиям |
| Существующий код сломается | Высокая | Высокое | Обширное тестирование на каждой стадии |
| Производительность деградирует | Низкая | Среднее | Benchmarks на критических путях |
| Team не разумеет архитектуру | Низкая | Среднее | Документация, примеры, code reviews |

---

## 7. Следующие шаги

1. **PO одобрение** этого плана
2. **Создание stage файлов** (arch-clean-refactor-stage-01.md, etc)
3. **Запуск стадии 01** (Infrastructure слой - repositories)
4. **Итеративное выполнение** оставшихся стадий
