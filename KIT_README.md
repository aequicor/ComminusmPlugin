# AI Kit — ComminusmPlugin

Документация по настроенному AI-рабочему процессу для проекта **ComminusmPlugin**
(Minecraft Paper плагин, Kotlin/Gradle, Clean Architecture).

---

## Раннеры

| Раннер | Файл конфига | Файл агентов |
|--------|-------------|--------------|
| Claude Code | `.claude/settings.json` | `CLAUDE.md` |
| OpenCode | `opencode.json` | `AGENTS.md` |

---

## Агенты

| Агент | Роль | Модель (по умолчанию) |
|-------|------|----------------------|
| `Main` | Оркестратор — точка входа, классифицирует задачи | Reasoner (opus / kimi-k2) |
| `Architect` | Spec.md + plan.md | Reasoner |
| `CodeWriter` | TDD-first реализация, один срез за раз | Balanced (sonnet / deepseek-flash) |
| `Verifier` | Mode-driven ревью (build / review / DoD / trace / mutation-sample) | Reasoner |
| `BugFixer` | Debug + fix + регрессионный тест | Balanced / Reasoner (по severity) |
| `Researcher` | Исследование библиотек и паттернов (только чтение) | Reasoner |

---

## Ежедневные команды

### Новая фича
```
/kit-new-feature
```
Запускает полный pipeline: Architect → CodeWriter → Verifier → DoD gate.

### Фикс бага
```
/kit-fix
```
Сканирует FAIL/PEND тест-кейсы → BugFixer → Verifier.

### Технический долг
```
/kit-techdebt
```
Drains backlog в `vault/tech-debt/` в контролируемом батче.

### Ревью текущих изменений
```
/kit-review
```

### Линт
```
/kit-lint
```
Запускает `./gradlew detekt ktlintCheck`.

### Статус задачи
```
/kit-status
```
Показывает текущий активный task из `.planning/CURRENT.md`.

### Одобрение плана
```
/kit-approve
```
Ручное одобрение плана/stage (заменяет ожидание PO в интерактивном режиме).

### Откат шага
```
/kit-revert-step
```

### Обновление кита
```
/kit-update
```

---

## Воркфлоу

```
feature:    /kit-new-feature → Architect (plan) → CodeWriter (TDD) → Verifier (review) → DoD
bug:        /kit-fix         → BugFixer (triage+debug) → Verifier (review)
tech-debt:  /kit-techdebt    → Architect (plan) → CodeWriter (refactor) → Verifier (review)
sleep:      /kit-sleep       → автономный режим без интерактивных gate'ов
```

---

## Сборка и тесты

| Команда | Назначение |
|---------|-----------|
| `./gradlew compileKotlin` | Быстрая проверка компиляции |
| `./gradlew test` | Запуск тестов (JUnit + MockK + MockBukkit) |
| `./gradlew detekt ktlintCheck` | Lint + code-style |
| `./gradlew build` | Полная сборка (включает shadowJar) |

---

## Переменные окружения

| Переменная | Назначение | Обязательна |
|-----------|-----------|-------------|
| `OLLAMA_API_KEY` | API-ключ для Ollama Cloud (kimi-k2, deepseek-flash) | Для OpenCode |
| `CONTEXT7_API_KEY` | API-ключ для context7 MCP (поиск документации) | Нет (disabled) |

---

## Структура `.aikit/`

```
.aikit/
└── manifest.yaml   # Единый источник истины для всех раннеров
```

Для обновления раннер-файлов после изменения манифеста:
```powershell
.\kit-setup.exe verify   # проверить манифест
.\kit-setup.exe generate # перегенерировать файлы раннеров
```

---

## Профили (активные)

| Профиль | Ось | Что даёт |
|---------|-----|---------|
| `kotlin-gradle` | language | Kotlin LSP, detekt, ktlint, serena MCP |
| `paper-plugin` | framework | Minecraft Paper/Bukkit API конвенции |
| `clean-architecture` | capability | Правила dependency rule, ports/adapters |
| `solid` | capability | SOLID + class-level OO правила |
| `security-baseline` | capability | Запрет literal API keys, SQL-инъекций |
| `quality-gates` | capability | Test-quality + трассировка требований |

---

## MCP-инструменты

| Инструмент | Тип | URL / Команда |
|-----------|-----|--------------|
| `serena` | mcp-stdio | `serena start-mcp-server` |
| `knowledge-my-app` | mcp-http | `http://localhost:8010/mcp` |
| `context7` | mcp-http | `https://mcp.context7.com/mcp` (disabled) |
