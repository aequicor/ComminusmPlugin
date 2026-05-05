# feat-privates-orders-fronts

## Type
FEATURE (reverse-engineered — code exists, needs spec + test cases)

## Module
comminusm

## Description
Создать техническую спецификацию и тест-кейсы для реализованной фичи "Приваты — ордера и фронты".
- Приват — система приватизации территорий.
- Ордер — территория дома игрока (private zone).
- Трудовой фронт — область добычи ресурсов (mining/work zone).
- Есть команды и GUI.
- Используются SQLite + кеширование в чанках мира.

## Status
- Created: 2026-05-05
- Phase: CLOSE

## Checkpoints

## 2026-05-05T00:00:00Z
- DONE: Task created, clarifying questions answered
- NEXT: Reverse-engineer codebase to understand feature, then write spec + test cases

## 2026-05-05T12:00:00Z
- DONE: Reverse-engineered all source files (models, repos, services, listeners, GUI, commands, config, DB, tests)
- NEXT: Write spec.md and test-cases.md via @CodeWriter

## 2026-05-05T12:30:00Z
- DONE: Spec and test cases written and saved
  - `vault/reference/comminusm/spec/privates-orders-fronts.md` (413 lines)
  - `vault/reference/comminusm/test-cases/privates-orders-fronts-test-cases.md` (175 lines, 70 TCs)
- NEXT: Archive task, update CURRENT.md
