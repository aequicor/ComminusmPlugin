# Architecture Refactor — Test Cases

**Feature:** Clean Architecture + SOLID Refactoring  
**Module:** comminusm  
**Type:** TECH (Architecture refactoring)  
**Status:** IMPLEMENTATION  

---

## Test Cases

### TC-101: Repository Interfaces Exist

| Field | Value |
|-------|-------|
| TC ID | TC-101 |
| Title | Domain repository interfaces created |
| Type | unit |
| Status | PEND |
| Priority | CRITICAL |
| Description | Verify 4 repository interfaces exist in domain/repositories/ package |
| AC | • OrderRepository interface exists<br/>• WorkFrontRepository interface exists<br/>• WorkdaysRepository interface exists<br/>• CommuneRepository interface exists<br/>• No concrete repository classes in domain package |
| Impl | (impl: src/test/kotlin/ru/kyamshanov/comminusm/architecture/RepositoryInterfaceTest.kt) |
| Notes | Architecture test verifying contracts |

---

### TC-102: Repository Implementations in Infrastructure

| Field | Value |
|-------|-------|
| TC ID | TC-102 |
| Title | Infrastructure repository implementations exist |
| Type | unit |
| Status | PEND |
| Priority | CRITICAL |
| Description | Verify implementations exist in infrastructure/repositories/ |
| AC | • OrderRepositoryImpl exists and implements OrderRepository<br/>• WorkFrontRepositoryImpl exists and implements WorkFrontRepository<br/>• WorkdaysRepositoryImpl exists and implements WorkdaysRepository<br/>• CommuneRepositoryImpl exists and implements CommuneRepository |
| Impl | (impl: src/main/kotlin/ru/kyamshanov/comminusm/infrastructure/repositories/) |
| Notes | All Impl classes created |

---

### TC-103: Services Use Repository Interfaces

| Field | Value |
|-------|-------|
| TC ID | TC-103 |
| Title | Services depend on repository interfaces not concrete classes |
| Type | unit |
| Status | PEND |
| Priority | CRITICAL |
| Description | Verify all services have been updated to use repository interfaces |
| AC | • OrderService constructor uses OrderRepository interface parameter<br/>• WorkFrontService uses WorkFrontRepository interface<br/>• WorkdaysService uses WorkdaysRepository interface<br/>• No service imports concrete storage.* classes |
| Impl | (impl: src/main/kotlin/ru/kyamshanov/comminusm/service/) |
| Notes | Dependency Inversion achieved |

---

### TC-104: ComminusmPlugin Creates Implementations

| Field | Value |
|-------|-------|
| TC ID | TC-104 |
| Title | Plugin creates repository implementations and injects interfaces |
| Type | unit |
| Status | PEND |
| Priority | HIGH |
| Description | ComminusmPlugin instantiates implementations and passes interfaces to services |
| AC | • orderRepository: OrderRepository = OrderRepositoryImpl(...)<br/>• frontRepository: WorkFrontRepository = WorkFrontRepositoryImpl(...)<br/>• workdaysRepository: WorkdaysRepository = WorkdaysRepositoryImpl(...)<br/>• Services receive interfaces, not implementations |
| Impl | (impl: src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt:100-120) |
| Notes | DI pattern applied in main plugin |

---

### TC-105: Build Succeeds

| Field | Value |
|-------|-------|
| TC ID | TC-105 |
| Title | Project compiles without errors |
| Type | integration |
| Status | PEND |
| Priority | CRITICAL |
| Description | Full project compilation succeeds |
| AC | • ./gradlew compileKotlin completes successfully<br/>• No compilation errors<br/>• No warnings (or expected warnings only) |
| Impl | (impl: gradle build process) |
| Notes | Verified by CodeWriter |

---

### TC-106: Tests Pass

| Field | Value |
|-------|-------|
| TC ID | TC-106 |
| Title | All existing tests still pass |
| Type | integration |
| Status | PEND |
| Priority | CRITICAL |
| Description | Regression tests verify no functionality was broken |
| AC | • ./gradlew test passes all unit tests<br/>• No test regressions<br/>• All repositories work with new interface/implementation split |
| Impl | (impl: src/test/kotlin/) |
| Notes | Tests updated to use new implementations |

---

### TC-107: Lint Checks Pass

| Field | Value |
|-------|-------|
| TC ID | TC-107 |
| Title | Code style and detekt checks pass |
| Type | unit |
| Status | PEND |
| Priority | HIGH |
| Description | Code follows project style guidelines |
| AC | • ./gradlew detekt passes<br/>• ./gradlew ktlintCheck passes<br/>• No style violations |
| Impl | (impl: gradle linting) |
| Notes | Verified by CodeWriter |

---

### TC-108: No Circular Dependencies

| Field | Value |
|-------|-------|
| TC ID | TC-108 |
| Title | Dependency direction is correct (no cycles) |
| Type | unit |
| Status | PEND |
| Priority | HIGH |
| Description | domain → no dependencies, application → domain, infrastructure → domain |
| AC | • domain/repositories/*.kt have 0 infrastructure/application imports<br/>• infrastructure/repositories/*Impl.kt import from domain.repositories only<br/>• services import domain.repositories, not infrastructure.repositories |
| Impl | (impl: architecture test) |
| Notes | Verify with grep for imports |

---

### TC-109: Old Storage Classes Removed

| Field | Value |
|-------|-------|
| TC ID | TC-109 |
| Title | Legacy storage/* repository classes deleted |
| Type | unit |
| Status | PEND |
| Priority | MEDIUM |
| Description | Old storage/OrderRepository.kt, storage/WorkFrontRepository.kt, etc removed |
| AC | • storage/OrderRepository.kt does not exist<br/>• storage/WorkFrontRepository.kt does not exist<br/>• storage/WorkdaysRepository.kt does not exist<br/>• All references updated to infrastructure.repositories.* |
| Impl | (impl: git status check) |
| Notes | Cleanup of old code |

---

### TC-110: Plugin Starts Successfully

| Field | Value |
|-------|-------|
| TC ID | TC-110 |
| Title | Plugin loads and initializes without errors |
| Type | integration |
| Status | PEND |
| Priority | CRITICAL |
| Description | Game server can load the plugin, ComminusmPlugin.onEnable() succeeds |
| AC | • Server startup completes<br/>• No exceptions in plugin initialization<br/>• All repositories initialized<br/>• Order/Commune/WorkFront systems functional |
| Impl | (impl: manual server test or integration test) |
| Notes | Manual verification or integration test |

---

## Test Summary

| Category | Count | Priority |
|----------|-------|----------|
| Architecture | 3 | CRITICAL |
| Build/Compile | 2 | CRITICAL |
| Code Quality | 2 | HIGH |
| Functionality | 2 | CRITICAL |
| Cleanup | 1 | MEDIUM |
| **TOTAL** | **10** | - |

---

## Defects Log

| DEF ID | TC ID | Title | Severity | Status | Notes |
|--------|-------|-------|----------|--------|-------|
| - | - | - | - | - | None recorded yet |

---

## Sign-Off

**Author:** @Main  
**Date:** 2026-05-07  
**Version:** 1.0 (Stage 01)  
**Status:** IMPLEMENTATION PHASE
