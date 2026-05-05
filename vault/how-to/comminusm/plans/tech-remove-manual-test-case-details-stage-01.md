# Stage 01 — Remove pre-filled manual test case details

**Goal:** Удалить все заранее заполненные детальные секции ручных тест-кейсов (TC-01..TC-70), оставив сводную таблицу, легенду статусов и один пустой шаблон.

**Files:**
- Modify: `vault/reference/comminusm/test-cases/privates-orders-fronts-test-cases.md`

**Plan:**
1. Сохранить строки 1–110 (header, legend, defect lifecycle, summary table 70 rows, comment).
2. Удалить всё с строки 111 до конца (все детальные ручные секции: TC-00, TC-ID, TC-01..TC-70).
3. Добавить в конце один пустой шаблон `## TC-00: Шаблон`.
