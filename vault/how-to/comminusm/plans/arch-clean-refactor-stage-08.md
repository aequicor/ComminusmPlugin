# Stage 08: Comprehensive Tests (Unit + Integration)

**Цель:** Написать полное покрытие unit и integration тестов для всех Use Case реализаций и Repository implementations. Целевое покрытие >70%.

**Принцип:** Test-Driven Quality — каждый Use Case и Repository должны иметь тесты, проверяющие как happy path, так и error scenarios.

---

## Обзор компонентов для тестирования

### Use Cases (20+ реализаций)

**Order Use Cases (11):**
- CreateOrderUseCase
- ActivateOrderUseCase
- UpgradeOrderUseCase
- DeleteOrderUseCase
- GetOrderByOwnerUseCase
- GetOrderByIdUseCase
- FindOrdersInWorldUseCase
- GetNativeOrdersOfPlayerUseCase
- CheckOrderOverlapUseCase
- CheckOrderLeadershipUseCase
- GetOrderCostForLevelUseCase

**Workdays Use Cases (3):**
- IncrementWorkdaysUseCase
- SpendWorkdaysUseCase
- GetWorkdaysBalanceUseCase

**WorkFront Use Cases (4):**
- GetWorkFrontByOwnerUseCase
- GetWorkFrontsInWorldUseCase
- DeactivateWorkFrontUseCase

**Commune Use Cases (5+):**
- GetCommuneOfOrderUseCase
- GetToggleModeUseCase
- BroadcastToCommuneUseCase
- CheckCommuneFriendlyFireUseCase
- RecalculateCrossOrderRightsUseCase

### Repositories (3)

- OrderRepository (OrderRepositoryImpl)
- WorkFrontRepository (WorkFrontRepositoryImpl)
- WorkdaysRepository (WorkdaysRepositoryImpl)

---

## Стратегия тестирования

### Unit Tests
- Один тест-класс на каждый Use Case
- Один тест-класс на каждую Repository реализацию
- Mock-зависимости (repository/service колл-ы)
- Проверка happy path + error cases

### Integration Tests
- Repository tests с реальной БД (in-memory SQLite для тестов)
- Use Case integration с реальными Repository инстансами
- Проверка complete workflows

### Test Структура

```kotlin
// Unit Test Example
class CreateOrderUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val useCase = CreateOrderUseCaseImpl(orderRepository)
    
    @Test
    fun `should create order with valid UUID`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val expectedOrder = Order(id=1, ownerUuid=uuid, level=1, ...)
        every { orderRepository.save(any()) } returns expectedOrder
        
        // Act
        val result = useCase(uuid)
        
        // Assert
        assert(result.ownerUuid == uuid)
        verify { orderRepository.save(any()) }
    }
    
    @Test
    fun `should throw when UUID is null`() {
        // Arrange
        val uuid = null
        
        // Act & Assert
        assertThrows<IllegalArgumentException> { useCase(uuid) }
    }
}

// Integration Test Example
class OrderRepositoryIntegrationTest {
    private val repository = OrderRepositoryImpl(testDatabase)
    
    @Test
    fun `should persist and retrieve order`() {
        // Arrange
        val order = Order(id=1, ownerUuid=UUID.randomUUID(), ...)
        
        // Act
        repository.save(order)
        val retrieved = repository.findById(1)
        
        // Assert
        assert(retrieved?.ownerUuid == order.ownerUuid)
    }
}
```

---

## Шаги реализации

### Шаг 1: Order Use Cases Tests (5 дней)

**Файлы для создания:**
```
src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/
├── CreateOrderUseCaseTest.kt
├── ActivateOrderUseCaseTest.kt
├── UpgradeOrderUseCaseTest.kt
├── DeleteOrderUseCaseTest.kt
├── GetOrderByOwnerUseCaseTest.kt
├── GetOrderByIdUseCaseTest.kt
├── FindOrdersInWorldUseCaseTest.kt
├── GetNativeOrdersOfPlayerUseCaseTest.kt
├── CheckOrderOverlapUseCaseTest.kt
├── CheckOrderLeadershipUseCaseTest.kt
└── GetOrderCostForLevelUseCaseTest.kt
```

**Для каждого теста:**
- Happy path (успешное выполнение)
- Error case: invalid input
- Error case: not found
- Edge cases (если применимо)

### Шаг 2: Workdays + WorkFront Use Cases Tests (2 дня)

```
src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/
├── workdays/
│   ├── IncrementWorkdaysUseCaseTest.kt
│   ├── SpendWorkdaysUseCaseTest.kt
│   └── GetWorkdaysBalanceUseCaseTest.kt
└── workfront/
    ├── GetWorkFrontByOwnerUseCaseTest.kt
    ├── GetWorkFrontsInWorldUseCaseTest.kt
    └── DeactivateWorkFrontUseCaseTest.kt
```

### Шаг 3: Commune Use Cases Tests (3 дня)

```
src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/commune/
├── GetCommuneOfOrderUseCaseTest.kt
├── GetToggleModeUseCaseTest.kt
├── BroadcastToCommuneUseCaseTest.kt
├── CheckCommuneFriendlyFireUseCaseTest.kt
└── RecalculateCrossOrderRightsUseCaseTest.kt
```

### Шаг 4: Repository Integration Tests (3 дня)

```
src/test/kotlin/ru/kyamshanov/comminusm/infrastructure/repositories/
├── OrderRepositoryIntegrationTest.kt
├── WorkFrontRepositoryIntegrationTest.kt
└── WorkdaysRepositoryIntegrationTest.kt
```

**Для каждого Repository:**
- Create + Read
- Update (если applicable)
- Delete (если applicable)
- Query methods (findBy*, getAll, etc.)
- Constraint violations (unique, not null, etc.)

### Шаг 5: Coverage Report (1 день)

1. Запустить `./gradlew jacocoTestReport`
2. Проверить coverage метрики
3. Документировать gaps (если есть)
4. Убедиться >70% coverage

---

## Критерии завершения

- [ ] 11 Order Use Case tests написаны (100+ test cases)
- [ ] 7 Workdays/WorkFront Use Case tests написаны
- [ ] 5 Commune Use Case tests написаны
- [ ] 3 Repository Integration tests написаны
- [ ] Все тесты проходят (./gradlew test)
- [ ] Coverage >70% (./gradlew jacocoTestReport)
- [ ] Lint без нарушений (detekt + ktlintCheck)

---

## Приблизительная трудоёмкость

- **Unit Tests:** ~150-200 test cases
- **Integration Tests:** ~30-50 test cases
- **Итого:** ~180-250 test cases
- **Время:** ~5-7 рабочих дней при 20-30 cases/день

---

## Риски и смягчение

| Риск | Вероятность | Смягчение |
|------|-------------|----------|
| Низкое покрытие (edge cases) | СРЕДНЯЯ | Использовать чек-лист corner cases из Stage 07 |
| Медленные integration tests | СРЕДНЯЯ | Использовать in-memory БД (H2/SQLite), параллелизм |
| Нестабильные тесты | СРЕДНЯЯ | Избегать random data, использовать фиксированные fixtures |
| Duplicate test code | СРЕДНЯЯ | Использовать shared test utilities, base classes |

---

## Следующие шаги

После завершения Stage 08:
- Проверить coverage отчёт и документировать результаты
- Рассмотреть дополнительные performance/stress tests (если необходимо)
- Закрыть задачу arch-clean-refactor

