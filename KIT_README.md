# KIT_README — ComminusmPlugin

Источник истины — `.aikit/manifest.yaml`. Все файлы в `.claude/`, `.opencode/`, `CLAUDE.md` и `AGENTS.md` генерируются из него командой `kit-setup generate .aikit/manifest.yaml`. Проект подключён к двум раннерам: **Claude Code** и **OpenCode**.

---

## Ежедневные команды

### `/kit-new-feature "<описание>"`
Используйте, когда нужно реализовать новую функциональность с полным циклом: спецификация → план → TDD-реализация → ревью.

**Пайплайн:**
```
Architect (planning, gate: approve)
  → CodeWriter (implementation, gate: auto, retry×3, rollback on fail)
  → Verifier (review, gate: auto, rollback on fail)
  → Verifier (review, gate: ground-truth)
  → Verifier (review, gate: diff-review)
```

### `/kit-fix "<описание бага>"`
Используйте при известном баге с воспроизводимым сценарием.

**Пайплайн:**
```
BugFixer (triage, gate: auto)
  → BugFixer (debug, gate: auto)
  → Verifier (review, gate: approve)
```

### `/kit-techdebt "<цель рефакторинга>"`
Используйте для планового рефакторинга конкретной части кодовой базы.

**Пайплайн:**
```
Architect (planning, gate: approve)
  → CodeWriter (refactor, gate: auto)
  → Verifier (review, gate: diff-review)
```

### `/kit-sleep "<фича>"`
Автономный ночной прогон. Все gate-точки подтверждаются автоматически, бюджет retry удваивается. Запрещён для critical-задач.

**Пайплайн:**
```
Architect (planning, gate: auto)
  → CodeWriter (implementation, gate: auto, retry×6)
  → Verifier (review, gate: auto, retry×6)
  → Verifier (review, gate: auto)
```

---

## Состав агентов

| Агент | Роль | Ответственность | Предпочтительный tier |
|---|---|---|---|
| `Main` | orchestrator | Единственная точка входа; классифицирует задачи и диспетчеризует | reasoner |
| `Architect` | architect | Создаёт `spec.md` + `plan.md`; один проход без реплана | reasoner |
| `CodeWriter` | code-writer | TDD-first реализация, один шаг за раз | balanced |
| `Verifier` | verifier | Mode-driven review: test-execute / DoD / trace / mutation-sample | reasoner |
| `BugFixer` | bug-fixer | Debug + fix + regression test | balanced (critical → reasoner) |

Конкретная модель разрешается при рендере из `models[]` на основе `tier` и активного провайдера раннера. Чтобы сменить модель — отредактируйте `.aikit/manifest.yaml` и перегенерируйте.

---

## Структура знаний

### Долгосрочные спецификации (KnowledgeOS)
Бэкенд: `mcp`, tool: `knowledge-os`, URL: `http://localhost:8010/mcp`

| Тип документа | Frontmatter-фильтр |
|---|---|
| Фичи | `fm.type: domain, fm.scope: feature` |
| Подсистемы | `fm.type: reference, fm.scope: subsystem` |
| Решения (ADR) | `fm.type: decision` |
| Техдолг | `fm.type: tech-debt` |
| Документация | `fm.type: documentation` |

### Сессионное состояние (filesystem)
Путь: `.planning/`

| Файл | Назначение |
|---|---|
| `.planning/CURRENT.md` | Текущая активная задача |
| `.planning/tasks/{task_id}.md` | Детали конкретной задачи |
| `.planning/DECISIONS.md` | Оперативные решения сессии |

### Секции конституции
- **routing** — таблица маршрутизации по командам
- **conventions** — соглашения по коду, документации, git
- **retrieval-hooks** — как агенты обращаются к холодному хранилищу
- **orchestration** — протоколы передачи контекста между агентами

---

## Прямой вызов агентов

**Claude Code:** `@Architect`, `@CodeWriter`, `@Verifier`, `@BugFixer`, `@Main`

Пример: `@Architect разработай план для добавления системы налогообложения ордеров`

**OpenCode:** агенты находятся в `.opencode/agents/`. Для прямого вызова используйте механизм переключения агентов вашего раннера.

---

## Обновление кита

1. Отредактируйте `.aikit/manifest.yaml`
2. `kit-setup.exe verify .aikit/manifest.yaml` — убедитесь что нет ошибок
3. `kit-setup.exe generate .aikit/manifest.yaml` — перегенерируйте файлы
4. Закоммитьте манифест и сгенерированные файлы вместе

---

## Сгенерированные файлы

**Claude Code:**
- `CLAUDE.md`
- `.claude/agents/` — Main, Architect, CodeWriter, Verifier, BugFixer
- `.claude/skills/` — bug-retro, definition-of-done, eval-collector, gate-telemetry, look-up, mutation-sample, pre-mortem, replan-on-discovery, spec-to-code-trace, tech-debt-record
- `.claude/commands/` — kit-approve, kit-attach, kit-config, kit-defect, kit-extend, kit-fix, kit-lint, kit-map, kit-mutate, kit-new-feature, kit-prepare, kit-resume, kit-revert, kit-revert-step, kit-review, kit-rework, kit-sleep, kit-status, kit-step-resume, kit-techdebt, kit-uninstall, kit-update
- `.claude/prompts/explore-module.md`
- `.claude/settings.json`

**OpenCode:**
- `AGENTS.md`
- `.opencode/agents/` — Main, Architect, CodeWriter, Verifier, BugFixer
- `.opencode/skills/` — аналогичный набор из 10 навыков
- `.opencode/commands/` — аналогичный набор команд
- `.opencode/prompts/explore-module.md`
- `opencode.json`
