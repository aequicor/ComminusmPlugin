---
genre: reference
module: comminusm
title: Test Cases — Flag Stability
topic: flag-stability
status: Living
generated: 2026-05-05
last_updated: 2026-05-06
author: "@QA"
related:
  - vault/concepts/comminusm/requirements/flag-stability.md
  - vault/concepts/comminusm/plans/flag-stability-corner-cases.md
---

# Test Cases: Flag Stability

**Module:** comminusm
**Feature:** flag-stability
**Generated:** 2026-05-05 by @QA
**Spec:** `[[reference/comminusm/spec/flag-stability]]`
**Requirements:** `[[concepts/comminusm/requirements/flag-stability]]`

---

## How this file works

This is a **living document**. Ownership is split:

- **@QA** (REQUIREMENTS phase) — creates this file from requirements + corner cases. All Status default to `PEND`. Notes empty.
- **@QA** (IMPLEMENTATION phase, DRAFT/FINAL) — appends impl-level TCs (unit-edge, integration, error). Append-only.
- **@TestRunner** — interactive walkthrough (mode `EXECUTE`). Updates **Status only**. When a TC fails, allocates DEF-id and appends one entry to the Defects log.
- **@BugFixer** — after a fix, updates Status `FAIL → PASS` and Defects log `OPEN → FIXED`.
- **Manual tester** — fills **Notes** when a TC fails (bug root cause, remarks).

AI agents do NOT touch the Notes column. AI agents do NOT generate per-TC detailed sections.

---

## Status legend

`PEND`  •  `PASS`  •  `FAIL`  •  `SKIP`

## Defect lifecycle

`OPEN` → `FIXED` → `VERF`

---

> Filled by AI agents. Columns AI may edit: **Status only**.
> The **Notes** column is owned by the manual tester — written when a TC fails.

| ID     | Status | Notes | Type        | Description                                                                                                                         | To be                                                                                              |
|--------|--------|-------|-------------|-------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| TC-01  | PEND   | —     | happy path  | [US-01] Владелец ордера устанавливает флаг — опорный блок заменяется на неуничтожимый материал                                      | Под баннером появляется BEDROCK (или OBSIDIAN по конфигу), флаг активирован                        |
| TC-02  | PEND   | —     | happy path  | [US-02] Владелец фронта устанавливает красный флаг — опорный блок заменяется на неуничтожимый материал                              | Под RED_BANNER появляется BEDROCK (или OBSIDIAN), флаг фронта активирован                          |
| TC-03  | PEND   | —     | happy path  | [US-03] Любой игрок смотрит в сторону активированного флага — видит имя владельца над баннером                                      | Над баннером виден невидимый armor stand с именем владельца в формате из конфига                    |
| TC-04  | PEND   | —     | happy path  | [US-04] Установка флага ордера при наличии ≥2 свободных блоков над позицией — активация проходит успешно                            | Опорный блок заменён, armor stand создан, PDC чанка содержит UUID armor stand                      |
| TC-05  | PEND   | —     | happy path  | [US-05] Владелец ордера удаляет ордер через GUI подтверждения — мир и БД очищаются                                                  | Опорный блок заменён на AIR, armor stand удалён, запись в БД удалена, кэш чанка очищен             |
| TC-06  | PEND   | —     | happy path  | [US-06] Владелец фронта перемещает флаг через GUI — старые объекты удалены, новые созданы на новом месте                            | Старый опорный блок и armor stand удалены; на новом месте появляются новый опорный блок и armor stand |
| TC-07  | PEND   | —     | happy path  | [US-07] После любого воздействия на флаг (игрок, взрыв, вода) баннер никогда не появляется как item entity в мире                   | В мире нет item-дропа баннера ни при каком сценарии разрушения                                     |
| TC-08  | PEND   | —     | acceptance  | [AC-01] Игрок в survival пытается сломать опорный блок активированного ордерного флага                                              | Событие отменяется, блок не ломается, флаг не выпадает, игрок получает сообщение                   |
| TC-09  | PEND   | —     | acceptance  | [AC-01] Чужой игрок пытается сломать опорный блок активированного ордерного флага                                                   | Событие отменяется, блок не ломается, игрок получает сообщение                                     |
| TC-10  | PEND   | —     | acceptance  | [AC-02] Взрыв крипера рядом с опорным блоком и баннером активированного ордерного флага                                             | Опорный блок и блок баннера исключены из blockList взрыва и не разрушаются                         |
| TC-11  | PEND   | —     | acceptance  | [AC-02] Взрыв TNT рядом с опорным блоком и баннером активированного ордерного флага                                                 | Опорный блок и блок баннера исключены из blockList взрыва и не разрушаются                         |
| TC-12  | PEND   | —     | acceptance  | [AC-03] Поток воды достигает опорного блока активированного флага                                                                   | BlockFromToEvent отменяется, опорный блок не смывается, флаг остаётся                              |
| TC-13  | PEND   | —     | acceptance  | [AC-03] Поток лавы достигает опорного блока активированного флага                                                                   | BlockFromToEvent отменяется, опорный блок не уничтожается лавой, флаг остаётся                     |
| TC-14  | PEND   | —     | acceptance  | [AC-04] Поршень пытается сдвинуть опорный блок активированного флага (extend)                                                       | BlockPistonExtendEvent отменяется, опорный блок не двигается, флаг не выпадает                     |
| TC-15  | PEND   | —     | acceptance  | [AC-04] Поршень втягивает блоки рядом с опорным блоком активированного флага (retract)                                              | BlockPistonRetractEvent отменяется, опорный блок не двигается                                      |
| TC-16  | PEND   | —     | acceptance  | [AC-05] Игрок пытается сломать опорный блок активированного трудового фронта (RED_BANNER)                                           | Событие отменяется, блок не ломается                                                               |
| TC-17  | PEND   | —     | acceptance  | [AC-06] Взрыв/вода/лава/поршень воздействует на опорный блок активированного трудового фронта                                       | Опорный блок не разрушается и не двигается ни одним из этих механизмов                             |
| TC-18  | PEND   | —     | acceptance  | [AC-07] Armor stand с именем владельца виден над активированным флагом ордера                                                        | Над баннером ордера отображается имя владельца в формате «Ордер — {playerName}»                    |
| TC-19  | PEND   | —     | acceptance  | [AC-07] Armor stand с именем владельца виден над активированным флагом фронта                                                        | Над баннером фронта отображается имя владельца в формате «Трудовой Фронт — {playerName}»           |
| TC-20  | PEND   | —     | acceptance  | [AC-08] Установка флага ордера на валидную позицию (≥2 свободных блока над баннером)                                                | Под флагом появляется неуничтожимый опорный блок, проверка воздуха пройдена успешно                |
| TC-21  | PEND   | —     | acceptance  | [AC-09] Попытка установить флаг, если над позицией занято (твёрдый блок на +1 или +2)                                               | Активация отклоняется, игрок получает сообщение «недостаточно места над флагом»                    |
| TC-22  | PEND   | —     | acceptance  | [AC-09] Попытка установить флаг при Y баннера ≥ 318 — достаточно 1 свободного блока над баннером                                    | Активация проходит успешно при наличии 1 свободного блока (особый случай у границы мира)           |
| TC-23  | PEND   | —     | acceptance  | [AC-09] Жидкость (вода/лава) над позицией не блокирует активацию флага                                                              | Активация проходит успешно — жидкость считается свободным пространством                            |
| TC-24  | PEND   | —     | acceptance  | [AC-10] Удаление ордера через GUI: опорный блок заменяется на AIR, armor stand удалён, запись и кэш очищены                         | На месте опорного блока — AIR; armor stand не существует в мире; БД и кэш чанка очищены            |
| TC-25  | PEND   | —     | acceptance  | [AC-11] Перемещение флага фронта: старые объекты удалены, новые созданы на новом месте                                              | Старый опорный блок = AIR, старый armor stand отсутствует; на новом месте — BEDROCK + armor stand  |
| TC-26  | PEND   | —     | acceptance  | [AC-12] Падающий блок (песок/гравий) падает на опорный блок или блок баннера активированного флага                                  | EntityChangeBlockEvent отменяется, опорный блок и баннер не разрушаются, дропа нет                 |
| TC-27  | PEND   | —     | acceptance  | [AC-12] Баннер не выпадает как item entity при попытке разрушения через любой механизм (сводный — игрок, взрыв, вода, поршень)      | Ни в одном сценарии баннер не появляется в мире как предмет (item entity)                          |
| TC-28  | PEND   | —     | acceptance  | [AC-13] Игрок в creative mode пытается сломать опорный блок (BEDROCK) активированного флага                                         | Событие отменяется, бедрок не ломается, флаг не выпадает                                           |
| TC-29  | PEND   | —     | acceptance  | [AC-14] При загрузке чанка: опорный блок флага подменён командой /setblock — система восстанавливает материал из PDC                | ChunkLoadEvent обнаруживает несоответствие, заменяет блок на BEDROCK/OBSIDIAN, логирует WARN        |
| TC-30  | PEND   | —     | acceptance  | [AC-14] При загрузке чанка: опорный блок удалён командой /setblock на AIR — система выполняет lazy repair (AC-23)                   | ChunkLoadEvent обнаруживает отсутствие опорного блока и восстанавливает его                         |
| TC-31  | PEND   | —     | acceptance  | [AC-15] Wither-босс атакует блоки рядом с опорным блоком активированного флага (BEDROCK)                                            | Опорный блок не разрушается (BEDROCK неразрушим для Wither)                                        |
| TC-32  | PEND   | —     | acceptance  | [AC-15] Wither-босс атакует опорный блок флага из OBSIDIAN — защита через EntityExplodeEvent                                        | Опорный блок OBSIDIAN исключён из blockList события взрыва Wither и не разрушается                 |
| TC-33  | PEND   | —     | acceptance  | [AC-16] Удаление флага при недоступном armor stand — удаление выполняется gracefully                                                 | Если armor stand не найден (ни по PDC UUID, ни по bounding box) — удаление ордера/фронта завершается без ошибок |
| TC-34  | PEND   | —     | acceptance  | [AC-17] Два игрока одновременно активируют флаги в одной локации — второй получает отказ                                            | Только один флаг создаётся; второй игрок получает сообщение «локация занята»                       |
| TC-35  | PEND   | —     | acceptance  | [AC-18/AC-24] Краш сервера после замены опорного блока, до создания armor stand — при перезапуске armor stand создаётся автоматически | ChunkLoadEvent: PDC-ключ флага есть, ключ armor stand отсутствует → armor stand создаётся (repair) |
| TC-36  | PEND   | —     | acceptance  | [AC-19] Смена владельца ордера/фронта — armor stand обновляет отображаемое имя на нового владельца                                  | После смены владельца (синхронно или при следующей загрузке чанка) armor stand показывает актуальное имя |
| TC-37  | PEND   | —     | acceptance  | [AC-20] Попытка установить флаг в Незере — установка запрещена                                                                      | Активация отклоняется с сообщением об ограничении мира                                             |
| TC-38  | PEND   | —     | acceptance  | [AC-20] Попытка установить флаг в Энде — установка запрещена                                                                        | Активация отклоняется с сообщением об ограничении мира                                             |
| TC-39  | PEND   | —     | acceptance  | [AC-21] Игрок деактивирует фронт при полном инвентаре — флаг не дропается, игрок получает сообщение об ожидающем флаге              | Флаг не появляется в мире как item; игрок получает сообщение; маркер «ожидающего флага» записан в PDC |
| TC-40  | PEND   | —     | acceptance  | [AC-22] 101-й флаг в чанке — активация отклоняется с сообщением о лимите                                                            | Сообщение «Достигнут лимит флагов в этом чанке»; флаг не создаётся                                |
| TC-41  | PEND   | —     | acceptance  | [AC-23] Краш после удаления armor stand, до замены опорного блока на AIR — при перезапуске система завершает удаление               | ChunkLoadEvent: ключ armor stand отсутствует, ключ флага есть, запись в БД отсутствует → опора заменяется на AIR, PDC очищен |
| TC-42  | PEND   | —     | acceptance  | [AC-24] ChunkLoadEvent: PDC содержит ключ флага, опорный блок BEDROCK/OBSIDIAN, ключ armor stand отсутствует → полный repair          | Armor stand создаётся, его UUID сохраняется в PDC; флаг полностью активирован                      |
| TC-43  | PEND   | —     | acceptance  | [AC-24] ChunkLoadEvent: баннер присутствует, опорный блок не BEDROCK/OBSIDIAN (легаси-флаг) → система ничего не делает               | Легаси-флаг не трогается; лишние armor stand не создаются                                          |
| TC-44  | PEND   | —     | acceptance  | [AC-25] После смены никнейма владельца — lazy refresh при следующей загрузке чанка обновляет имя на armor stand                      | После загрузки чанка armor stand отображает актуальный никнейм из Bukkit.getOfflinePlayer           |
| TC-45  | PEND   | —     | acceptance  | [AC-26] ChunkLoadEvent: BEDROCK без PDC-ключа (природный или уложенный вручную) — система не создаёт armor stand                     | Природный BEDROCK не получает armor stand; phantom-entity не создаётся                              |
| TC-46  | PEND   | —     | acceptance  | [AC-27] Краш после удаления armor stand, до замены опоры; при перезапуске: запись в БД существует — система НЕ завершает удаление, восстанавливает armor stand | ChunkLoadEvent: БД-запись активна → armor stand создаётся заново; приват владельца сохраняется    |
| TC-47  | PEND   | —     | acceptance  | [AC-27] Краш после удаления armor stand, до замены опоры; при перезапуске: запись в БД отсутствует — система завершает удаление      | ChunkLoadEvent: БД-запись отсутствует → опора заменяется AIR, баннер удалён, PDC очищен            |
| TC-48  | PEND   | —     | acceptance  | [AC-28] При наличии маркера ожидающего флага: /party не создаёт новый флаг, а предлагает забрать ожидающий                          | /party возвращает ожидающий флаг при наличии места; не создаёт дублирующий флаг                   |
| TC-49  | PEND   | —     | acceptance  | [AC-28] При отсутствии маркера ожидающего флага: /party работает в штатном режиме и выдаёт новый флаг                               | Новый флаг создаётся и добавляется в инвентарь игрока                                              |
| TC-50  | PEND   | —     | acceptance  | [AC-29] Откат активации (armor stand не создан) — система восстанавливает точный исходный материал опорного блока                    | На месте опорного блока восстановлен исходный материал (DIRT, STONE и т.д.), а не AIR             |
| TC-51  | PEND   | —     | acceptance  | [AC-30] Конкурентные активации в одном чанке с задержкой > 5 секунд — второй запрос отклоняется                                     | Второй игрок получает сообщение «Попробуйте ещё раз»                                               |
| TC-52  | PEND   | —     | acceptance  | [AC-31] Ошибка записи в БД после создания опоры и armor stand — полный откат (armor stand удалён, опора восстановлена, PDC очищен)   | Мир возвращён в исходное состояние; в мире нет незарегистрированных неуничтожимых блоков           |
| TC-53  | PEND   | —     | acceptance  | [AC-32] Игрок в creative mode пытается сломать блок баннера (не опорный блок) активированного флага                                 | BlockBreakEvent отменяется; баннер не ломается, не выпадает; creative-клик ЛКМ не даёт предмет   |
| TC-54  | PEND   | —     | acceptance  | [AC-33] Краш при перемещении фронта: старые объекты удалены, новые не созданы; БД содержит новые координаты — lazy completion        | При загрузке нового чанка система создаёт опорный блок и armor stand по координатам из БД          |
| TC-55  | PEND   | —     | acceptance  | [AC-33] Краш при перемещении фронта: старые объекты удалены, новые не созданы; БД содержит старые координаты — repair на старом месте | При загрузке старого чанка система восстанавливает флаг на старых координатах; владелец не теряет приват |
| TC-56  | PEND   | —     | acceptance  | [AC-34] Легаси-флаг (до обновления): опорный блок разрушен игровым механизмом — новая защита не применяется                         | Легаси-флаг не защищён; это принятый риск (AC-34/OS-03); дюп через легаси-флаг технически возможен |
| TC-57  | PEND   | —     | acceptance  | [AC-35] Опора флага в одном чанке, баннер в соседнем — PDC записан в чанк опорного блока                                            | PDC-ключи привязаны к чанку опорного блока; ChunkLoadEvent баннерного чанка не выполняет проверок  |
| TC-58  | PEND   | —     | acceptance  | [AC-36] ChunkLoadEvent: БД недоступна при проверке AC-27 — система не изменяет мир и не блокирует загрузку чанка                    | Чанк загружается штатно; логируется WARN; мировые изменения откладываются до следующей загрузки    |
| TC-59  | PEND   | —     | acceptance  | [AC-37] Откат активации: удаление armor stand завершается ошибкой — откат продолжается (опора восстановлена, PDC очищен, маркер dirty записан) | Опора восстановлена; PDC очищен; маркер dirty_armorstand записан; при следующей загрузке чанка — повторная попытка удаления |
| TC-60  | PEND   | —     | acceptance  | [AC-37] ChunkLoadEvent обнаруживает маркер dirty_armorstand — пытается удалить entity; если найдена — удаляет, маркер снимается       | Orphan armor stand удалён из мира; маркер dirty_armorstand удалён из PDC                           |
| TC-61  | PEND   | —     | acceptance  | [AC-37] ChunkLoadEvent обнаруживает маркер dirty_armorstand, entity не найдена — маркер всё равно удаляется                          | Маркер dirty_armorstand удалён из PDC; нет ошибок; мир корректен                                   |
| TC-62  | PEND   | —     | acceptance  | [AC-38] Перемещение фронта: порядок операций соблюдён (БД обновляется первой, затем мировые изменения)                               | БД содержит новые координаты до любых изменений в мире; при краше на любом шаге AC-33 применяется корректно |
| TC-63  | PEND   | —     | acceptance  | [AC-38] Ошибка обновления БД на шаге 1 при перемещении фронта — вся операция отменяется, мир не изменяется                          | Флаг остаётся на прежнем месте без изменений; игрок получает сообщение об ошибке                   |
| TC-64  | PEND   | —     | acceptance  | [AC-39] /party при наличии маркера ожидающего флага и свободного места в инвентаре — флаг выдаётся, маркер удаляется                 | Ожидающий флаг добавлен в инвентарь; маркер PDC удалён; новый флаг не создаётся                   |
| TC-65  | PEND   | —     | acceptance  | [AC-39] /party при наличии маркера ожидающего флага, но инвентарь полон — игрок получает сообщение, маркер остаётся                  | Флаг не добавляется и не дропается; игрок получает сообщение «Освободите место в инвентаре»        |
| TC-66  | PEND   | —     | acceptance  | [AC-40] Пассивная верификация при ChunkLoadEvent сравнивает материал с PDC (comminusm:support_material/{id}), а не с текущим конфигом | После смены конфига BEDROCK→OBSIDIAN существующий флаг с BEDROCK не восстанавливается ложно        |
| TC-67  | PEND   | —     | acceptance  | [AC-40] Ключ comminusm:support_material/{id} удаляется при откате активации (AC-31)                                                   | После отката активации PDC не содержит ключ support_material; мир не загрязнён                    |
| TC-68  | PEND   | —     | acceptance  | [AC-41] Исходный материал опорного блока сохраняется как локальная переменная на main-thread до передачи в async-задачу               | Параллельные активации в одном чанке не создают гонки за исходный материал; каждый откат восстанавливает правильный материал |
| TC-69  | PEND   | —     | corner case | [CC-01 Crit] getOfflinePlayer(uuid).name возвращает null при активации флага — флаг активируется с UUID как fallback-именем           | Armor stand создаётся с именем-fallback (UUID или «[unknown]»); активация не падает с NPE          |
| TC-70  | PEND   | —     | corner case | [CC-01 Crit] getOfflinePlayer(uuid).name возвращает null при lazy refresh — обновление пропускается, логируется WARN                  | Armor stand не обновляется; WARN в консоли; мир не изменяется                                     |
| TC-71  | PEND   | —     | corner case | [CC-02 Crit] Удаление ордера/фронта, когда чанк с флагом не загружен — система выполняет lazy cleanup или принудительно загружает чанк | Опорный блок BEDROCK не остаётся в мире без владельца навсегда; armor stand удалён                 |
| TC-72  | PEND   | —     | corner case | [CC-03 Crit] Два одновременных вызова deactivate/deleteByOwner для одного флага — второй вызов idempotent                             | Нет NPE, нет DB constraint violation; финальное состояние корректно (блок AIR, armor stand отсутствует, БД очищена) |
| TC-73  | PEND   | —     | corner case | [CC-04 High] flag.supportBlockMaterial содержит невалидное значение («WOOD») — плагин откатывается к BEDROCK, логирует ERROR          | Плагин запускается, выводит ERROR в консоль, флаги активируются с BEDROCK по умолчанию            |
| TC-74  | PEND   | —     | corner case | [CC-05 High] Игрок отключается в момент незавершённой async DB-записи при активации флага                                            | Откат (если нужен) выполняется на main-thread без ссылки на player; нет NPE; мир корректен         |
| TC-75  | PEND   | —     | corner case | [CC-06 High] Попытка установить флаг фронта на позицию, уже занятую ордерным флагом                                                  | Активация отклоняется: «Эта позиция уже занята флагом»; PDC-ключ первого флага сохранён            |
| TC-76  | PEND   | —     | corner case | [CC-07 High] Запись PDC нового чанка при перемещении фронта завершается ошибкой после выполнения шагов 1–6                           | Система логирует ERROR, выполняет retry × 1; при повторной неудаче — логирует CRITICAL             |
| TC-77  | PEND   | —     | corner case | [CC-08 High] Удаление ордера (GUI) конкурирует с перемещением фронта на ту же позицию — финальное состояние детерминировано           | Chunk-level synchronized гарантирует: позиция занята ровно одной сущностью; промежуточных состояний нет |
| TC-78  | PEND   | —     | corner case | [CC-09 High] Null UUID в записи БД ордера/фронта — система использует «[unknown]» вместо краша                                       | Armor stand создаётся с именем «[unknown]»; логируется ERROR; NPE не выбрасывается                 |
| TC-79  | PEND   | —     | corner case | [CC-10 Med] flag.allowedWorlds содержит пустой список — все активации отклоняются, WARN в консоли                                    | Плагин логирует WARN при старте; все попытки установки флага отклоняются с понятным сообщением     |
| TC-80  | PEND   | —     | corner case | [CC-11 Med] ChunkLoadEvent срабатывает несколько раз подряд для одного чанка — lazy repair idempotent                                 | Второй и последующие запуски repair не создают дублирующих armor stand и PDC-ключей               |
| TC-81  | PEND   | —     | corner case | [CC-12 Med] Lazy refresh имени срабатывает во время удаления armor stand при перемещении фронта                                       | Refresh проверяет существование armor stand перед обновлением; если уже удалён — пропускает без ошибок |
| TC-82  | PEND   | —     | corner case | [CC-13 Med] flag.maxPerChunk = 0 — плагин применяет дефолт 50, логирует WARN                                                         | При старте выводится WARN; флаги активируются в пределах дефолтного лимита                         |
| TC-83  | PEND   | —     | corner case | [CC-14 Low] flag.titleFormat содержит незакрытую скобку — armor stand получает сырую строку, WARN при старте                          | Активация не падает; armor stand имеет сырую строку шаблона как имя; WARN в консоли               |
| TC-84  | PEND   | —     | corner case | [CC-15 Low] 50 флагов в одном чанке — ChunkLoadEvent с lazy refresh не блокирует main-thread заметно                                  | Время обработки ChunkLoadEvent с 50 флагами остаётся приемлемым (нет заметного lag spike)         |
| TC-85  | PEND   | —     | unit-edge   | [spec] FlagStabilityManager.blockPos: кодирование позиции с отрицательными X и Z — encode(-100, 64, -200) без переполнения             | Encoded value round-trips correctly: decode(encode(-100,64,-200)) == (-100,64,-200); no sign-extension error |
| TC-86  | PEND   | —     | unit-edge   | [spec] FlagStabilityManager.isFlagPosition: кэш пуст при cold-start, PDC чанка содержит ключ флага — метод возвращает true через PDC   | isFlagPosition returns true; PDC value loaded into cache; no NPE on empty cache                    |
| TC-87  | PEND   | —     | unit-edge   | [spec] chunkKey generation: world name containing colons (e.g. "custom:world:v2") and negative chunk coords (chunkX=-1, chunkZ=-1)     | chunkKey is unique and stable; colons in world name do not split key incorrectly; negative coords encoded without collision |
| TC-88  | PEND   | —     | integration | [spec] Activation aborted at world-check step — verify no world changes and clean state after rejection                                | Support block not placed; armor stand not spawned; DB unchanged; player receives world-restriction message |
| TC-89  | PEND   | —     | integration | [spec] Activation aborted at air-check step (block above banner is solid) — verify no world changes and clean state                    | Support block not placed; armor stand not spawned; DB unchanged; player receives «недостаточно места» message |
| TC-90  | PEND   | —     | integration | [spec] Activation aborted at chunk-limit check (100 flags already in chunk) — verify no world changes                                  | Support block not placed; DB unchanged; player receives chunk-limit message                        |
| TC-91  | PEND   | —     | integration | [spec] Activation aborted at lock-timeout step (tryLock returns false) — verify no world changes and player notified                   | Support block not placed; no DB write; player receives retry message; original block material intact |
| TC-92  | PEND   | —     | integration | [spec] Activation aborted at DB write failure (after support block placed, armor stand spawned) — full rollback performed              | Armor stand removed; support block restored to original material; PDC cleared; no orphan BEDROCK left in world |
| TC-93  | PEND   | —     | integration | [spec] Activation aborted at armor-stand spawn failure — rollback restores support block and clears PDC                               | Support block restored to original material; PDC key absent; DB entry not persisted; player receives error message |
| TC-94  | PEND   | —     | integration | [spec] Rollback after DB failure restores STONE original material exactly                                                              | Block at flag position is STONE after rollback; not AIR; not BEDROCK                               |
| TC-95  | PEND   | —     | integration | [spec] Rollback after DB failure restores GRAVEL original material exactly                                                             | Block at flag position is GRAVEL after rollback                                                    |
| TC-96  | PEND   | —     | integration | [spec] Rollback after DB failure restores DIRT original material exactly                                                               | Block at flag position is DIRT after rollback                                                      |
| TC-97  | PEND   | —     | integration | [spec] Rollback after DB failure restores NETHERRACK original material exactly                                                         | Block at flag position is NETHERRACK after rollback                                                |
| TC-98  | PEND   | —     | unit-edge   | [spec] tryLock(0) fails on contested position — player receives retry message; lock state unchanged after failed attempt               | Player sees «Попробуйте ещё раз через 5 секунд»; no partial state written; lock remains held by original holder |
| TC-99  | PEND   | —     | unit-edge   | [spec] Two concurrent activations on same position with 1-tick gap — second activation sees locked state and is rejected cleanly       | Only one flag created; second activation returns error; no double DB insert; no duplicate armor stand |
| TC-100 | PEND   | —     | integration | [spec] ChunkLoadEvent: BEDROCK block at flag-candidate position has no PDC key — block is not treated as a flag (AC-26)                | No armor stand spawned for natural BEDROCK; no phantom entity; no PDC written                      |
| TC-101 | PEND   | —     | performance | [spec] ChunkLoadEvent for chunk containing 50 flags — all repair/refresh actions processed; no operation times out or is skipped        | All 50 flags inspected; armor stands created/updated where needed; main-thread not blocked; no timeout error logged |
| TC-102 | PEND   | —     | integration | [spec] Front-move DB failure at step 1 (before any world change) — operation aborted, world unchanged, player gets error               | Flag remains at original position; original support block intact; original armor stand present; DB unchanged; player receives error message |
| TC-103 | PEND   | —     | unit-edge   | [spec] Front-move lock acquisition with lexicographic ordering: old="world:5:3", new="world:3:5" — new key acquired first              | Lock for "world:3:5" acquired before "world:5:3"; no deadlock; operation completes or rolls back cleanly |
| TC-104 | PEND   | —     | integration | [spec] /party with PDC chunk marker present but marker value missing/reset (marker gone) — /party falls through to normal flag issuance (A-06) | New flag issued normally; no error thrown; player receives new flag item                           |
| TC-105 | PEND   | —     | error       | [spec] /party with PDC marker value starting with "ITEM:" but containing corrupted Base64 — marker deleted, ERROR logged, /party falls through to normal issuance | Corrupted marker removed from PDC; ERROR entry in server log; player receives new flag via normal flow |
| TC-106 | PASS   | —     | error       | [bug-fix] Player moves front flag (places RED_BANNER in new location) while owning an existing active front — old BEDROCK support block and old ArmorStand must be fully removed | Old BEDROCK support block is removed (restored to original material), old ArmorStand is despawned; no orphaned entities or indestructible blocks remain at the old position |
| TC-107 | PASS   | —     | error       | [bug-fix] Player breaks the BEDROCK support block of their front flag (e.g. in creative mode) — block must be restored to original material instead of leaving a void | Support block position is restored to the original material (STONE, DIRT, GRAVEL, etc.) that was saved at activation time; no void/AIR hole remains |
| TC-108 | PASS   | —     | error       | [bug-fix] Игрок ломает цветок (POPPY/DANDELION/любой short flower), стоящий рядом с активным флагом фронта (RED_BANNER) — баг: флаг фронта удаляется как побочный эффект разрушения соседнего цветка. Source: bug-fix. | При разрушении цветка-соседа активный флаг фронта остаётся целым (BEDROCK опорный блок, ArmorStand с именем владельца, запись в БД, PDC чанка) — никаких изменений в состоянии флага не происходит. |

> The TC-00 block is a single static template. Manual tester copies it on demand
> when they want to elaborate on one specific TC (typically a failing one).
> Agents do not generate per-TC sections.

## TC-00: Template

**Description:** what to test, how to test it

**Steps:**

1. …

**As is:** …

**To be:** …

---

## Defects log

> Append-only. Each entry references a TC by id. AI agents (@TestRunner / @BugFixer) maintain this section.

| DEF-id | TC-id  | Status | Description                                                                                          |
|--------|--------|--------|------------------------------------------------------------------------------------------------------|
| DEF-05 | TC-106 | FIXED  | Old BEDROCK support block and ArmorStand orphaned on front flag relocation — FrontFlagListener used manual banner-only removal instead of FlagCleanupHelper.cleanupFlag() (fix: vault/guidelines/comminusm/reports/fix-front-flag-relocation-cleanup.md) |
| DEF-06 | TC-107 | FIXED  | Breaking support block leaves void instead of restoring original material — FlagCleanupHelper always set AIR; BlockListener overwrote restoration with explicit AIR set (fix: vault/guidelines/comminusm/reports/fix-front-flag-relocation-cleanup.md) |
| DEF-07 | TC-108 | VERF   | Front flag removed as side-effect when neighbor flower (POPPY/DANDELION) is broken — likely incorrect block-break event handler match for support block neighbors (fix: vault/guidelines/comminusm/reports/fix-flower-break-deletes-front.md) — VERF (auto, 11 unit tests pass) |
