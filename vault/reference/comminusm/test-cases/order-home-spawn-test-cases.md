---
genre: reference
module: comminusm
title: Test Cases — Order Home & Spawn
topic: order-home-spawn
status: living
generated: 2026-05-06 by @QA
last_updated: 2026-05-06 by @QA (IMPLEMENTATION FINAL)
related:
  - vault/concepts/comminusm/requirements/order-home-spawn.md
  - vault/concepts/comminusm/plans/order-home-spawn-corner-cases.md
---

# Test Cases: Order Home & Spawn

**Module:** comminusm
**Feature:** order-home-spawn
**Generated:** 2026-05-06 by @QA
**Spec:** `[[reference/comminusm/spec/order-home-spawn]]`
**Requirements:** `[[concepts/comminusm/requirements/order-home-spawn]]`

---

## How this file works

This is a **living document**. Ownership is split:

- **@QA** (REQUIREMENTS phase) — creates this file from requirements + corner cases. Fills the table with one row per TC. All Status default to `PEND`. Notes empty.
- **@QA** (IMPLEMENTATION phase, DRAFT/FINAL) — appends impl-level TCs (unit-edge, integration, error). Append-only.
- **@TestRunner** — interactive walkthrough (mode `EXECUTE`). Updates **Status only**. When a TC fails, allocates DEF-id and appends one entry to the Defects log (which references the TC by id).
- **@BugFixer** — after a fix, updates Status `FAIL → PASS` and Defects log `OPEN → FIXED` for the row it fixed.
- **Manual tester** — fills **Notes** when a TC fails (bug root cause, remarks). May also copy the `TC-00: Template` block and fill it in for any TC where elaboration helps (typically failing cases).

AI agents do NOT touch the Notes column. AI agents do NOT generate per-TC detailed sections.

`/kit-fix` reads this file, scans for `FAIL` and `PEND` rows, asks PO which to fix, dispatches @BugFixer per chosen TC, then dispatches @TestRunner (RERUN) to verify.

---

## Status legend

`PEND`  •  `PASS`  •  `FAIL`  •  `SKIP`

## Defect lifecycle

`OPEN` → `FIXED` → `VERF`

---

> Filled by AI agents. Columns AI may edit: **Status only**.
> The **Notes** column is owned by the manual tester — written when a TC fails.

| ID     | Status | Notes | Type        | Description                                                                                                          | To be                                                                                  |
|--------|--------|-------|-------------|----------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| TC-01  | PEND   | —     | happy path  | Член ордера с активным флагом открывает меню ордера — ожидается кнопка «Вернуться домой» (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Кнопка «Вернуться домой» видна и доступна для нажатия (AC-01)                          |
| TC-02  | PEND   | —     | acceptance  | Игрок, не состоящий в ордере, открывает меню ордера — кнопка не должна отображаться (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Кнопка «Вернуться домой» отсутствует или недоступна (AC-02)                            |
| TC-03  | PEND   | —     | happy path  | Член ордера с активным флагом нажимает «Вернуться домой» — запускается 30-секундный таймер (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt, src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Таймер запущен, игрок получает уведомление о начале обратного отсчёта (AC-03)          |
| TC-04  | PEND   | —     | acceptance  | Таймер запущен — каждую секунду игрок видит обновляемый прогресс оставшегося времени (ActionBar)                     | Счётчик обновляется ежесекундно, отображает оставшееся количество секунд (AC-04)       |
| TC-05  | PEND   | —     | acceptance  | Таймер запущен, игрок перемещается (изменяет координаты) — таймер должен прерваться (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-05) |
| TC-06  | PEND   | —     | acceptance  | Таймер запущен, игрок получает урон — таймер должен прерваться (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-06) |
| TC-07  | PEND   | —     | acceptance  | Таймер запущен, игрок атакует любую сущность — таймер должен прерваться (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-07) |
| TC-08  | PEND   | —     | happy path  | Игрок ожидает 30 секунд без движения, урона и атак — телепортация к флагу (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Игрок телепортируется к точке флага своего ордера (AC-08)                              |
| TC-09  | PEND   | —     | acceptance  | Член ордера с активным флагом погибает — возрождается у флага, а не на мировом спавне (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Игрок возрождается у координат флага ордера (AC-09)                                    |
| TC-10  | PEND   | —     | acceptance  | Член ордера с уничтоженным/отсутствующим флагом погибает — фолбэк на мировой спавн (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Игрок возрождается в мировой точке спавна (AC-10)                                      |
| TC-11  | PEND   | —     | acceptance  | Игрок без ордера погибает — стандартное возрождение на мировом спавне (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Игрок возрождается в мировой точке спавна (AC-11)                                      |
| TC-12  | PEND   | —     | acceptance  | Таймер уже запущен, игрок повторно нажимает «Вернуться домой» — повторное нажатие игнорируется (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt, src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Таймер не сбрасывается и не запускается второй раз (AC-12)                             |
| TC-13  | PEND   | —     | acceptance  | Флаг ордера уничтожается во время работы таймера — таймер должен прерваться (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/FlagEventListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление о недоступности флага (AC-13) |
| TC-14  | PEND   | —     | acceptance  | Таймер запущен, игрок падает под действием гравитации (изменение Y-координаты) — таймер прерывается (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-14) |
| TC-15  | PEND   | —     | acceptance  | Таймер запущен, игрок соскальзывает с лестницы или сносится водой — таймер прерывается (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-14) |
| TC-16  | PEND   | —     | acceptance  | Таймер запущен, игрок выходит с сервера (disconnect) — таймер тихо отменяется (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён без ошибок; при повторном входе таймер не возобновляется (AC-15)        |
| TC-17  | PEND   | —     | acceptance  | Таймер запущен, игрок телепортируется внешней командой (/tp, /home) на другие координаты — таймер прерывается (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-16) |
| TC-18  | PEND   | —     | corner case | Таймер запущен, внешняя телепорт-команда перемещает игрока на идентичные координаты (teleport to self) — таймер не прерывается (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер продолжает работу (AC-16, исключение)                                           |
| TC-19  | SKIP   | NOT APPLICABLE — AC deferred (no member/role/capture system in v1) | acceptance  | Таймер запущен, игрока исключают из ордера (кик) — таймер прерывается                                                | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-17) |
| TC-20  | PEND   | —     | acceptance  | Флаг relocated в тот же мир во время таймера — по истечении таймер телепортирует к новым координатам (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/FlagEventListenerTest.kt) | Игрок телепортируется к актуальной (новой) позиции флага (AC-18)                       |
| TC-21  | PEND   | —     | acceptance  | Флаг relocated в другой мир во время таймера — таймер немедленно прерывается (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/FlagEventListenerTest.kt) | Таймер отменён в момент перемещения флага в другой мир, игрок получает уведомление (AC-18a) |
| TC-22  | PEND   | —     | acceptance  | Таймер запущен, игрок умирает во время ожидания — таймер отменяется, применяется механика respawn (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён; respawn по AC-09 или AC-10 в зависимости от наличия флага (AC-19)      |
| TC-23  | SKIP   | NOT APPLICABLE — AC deferred (no member/role/capture system in v1) | acceptance  | Флаг ордера захвачен вражеским ордером во время таймера — таймер прерывается                                         | Таймер отменён, телепортация не происходит, игрок получает уведомление о недоступности флага (AC-20) |
| TC-24  | PEND   | —     | acceptance  | Данные PDC/БД о позиции флага повреждены или отсутствуют в момент срабатывания таймера — телепортация не производится (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Телепортация не выполнена, игрок видит сообщение «Флаг недоступен» (AC-21)             |
| TC-25  | PEND   | —     | acceptance  | При смерти данные о флаге ордера недоступны (chunk не загружен, данные отсутствуют) — фолбэк на мировой спавн (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Игрок возрождается в мировой точке спавна (AC-22)                                      |
| TC-26  | PEND   | —     | acceptance  | Таймер запущен, игрок закрывает меню ордера — таймер продолжает работать в фоне (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Прогресс таймера по-прежнему отображается (ActionBar), телепортация произойдёт по истечении (AC-23) |
| TC-27  | PEND   | —     | acceptance  | Таймер запущен, игрок сидит на маунте, маунт физически перемещается — таймер прерывается (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён, телепортация не происходит, игрок получает уведомление об отмене (AC-24) |
| TC-28  | PEND   | —     | acceptance  | После спешивания с маунта таймер не возобновляется автоматически                                                     | Для повторного запуска игрок должен снова нажать «Вернуться домой» (AC-24)             |
| TC-29  | PEND   | —     | acceptance  | Таймер завершился без прерывания — игрок телепортируется точно к координатам баннера флага                           | Игрок оказывается точно у баннера флага ордера (AC-25)                                 |
| TC-30  | SKIP   | NOT APPLICABLE — AC deferred (no member/role/capture system in v1) | acceptance  | Лидер ордера нажимает «Вернуться домой» — кнопка доступна, механика таймера идентична рядовому члену                 | Таймер запускается, работает аналогично TC-03 — TC-08 (AC-26)                          |
| TC-31  | PEND   | —     | acceptance  | Член ордера с активным флагом и установленной кроватью погибает — возрождается у флага, а не у кровати (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Игрок возрождается у флага ордера; приоритет: флаг > кровать > мировой спавн (AC-27)  |
| TC-32  | PEND   | —     | acceptance  | Игрок и флаг ордера находятся в разных мирах (Overworld vs Nether/End) — кнопка «Вернуться домой» недоступна (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Кнопка disabled/скрыта или отображается с уведомлением о невозможности межмировой телепортации; телепортация не запускается (AC-28) |
| TC-33  | SKIP   | NOT APPLICABLE — AC deferred (no member/role/capture system in v1) | acceptance  | Кандидат в ордер (не полноправный член) открывает меню ордера — кнопка «Вернуться домой» не отображается             | Кнопка отсутствует или недоступна для кандидата (AC-29)                                |
| TC-34  | PEND   | —     | corner case | [CC-01 Crit] Игрок погибает в том же тике, когда флаг уничтожается — respawn-обработчик не должен выдать ошибку и должен корректно применить фолбэк | Игрок возрождается в мировой точке спавна без исключений и без ошибок на сервере (CC-01) |
| TC-35  | PEND   | —     | corner case | [CC-02 Crit] Координаты флага зарегистрированы в PDC, но флаг физически деактивирован — телепортация или спавн не должны вести к «мёртвой» точке (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt, src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Система обнаруживает неактивный флаг: при телепортации — сообщение об ошибке (AC-21); при смерти — фолбэк на мировой спавн (AC-10) (CC-02) |
| TC-36  | PEND   | —     | corner case | [CC-03 High] Игрок погибает в Nether/End, флаг ордера находится в Overworld — respawn должен произойти в мире флага (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Игрок возрождается в Overworld у координат флага, независимо от мира гибели (CC-03)    |
| TC-37  | PEND   | —     | corner case | [CC-04 High] Chunk с координатами флага не загружен в момент обработки PlayerRespawnEvent — система должна загрузить chunk или применить фолбэк (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Chunk загружается и игрок возрождается у флага; при невозможности загрузки — мировой спавн (CC-04) |
| TC-38  | PEND   | —     | corner case | [CC-05 High] Сервер перезагружается во время работы таймера «Вернуться домой» — состояние таймера не персистируется  | После реконнекта таймер отсутствует; игрок должен нажать кнопку заново (CC-05)         |
| TC-39  | PEND   | —     | corner case | [CC-06 High] Флаг relocated в тот же тик, когда таймер срабатывает — система читает актуальные координаты в момент телепортации (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Игрок телепортируется к новым (актуальным) координатам флага, а не к устаревшим (CC-06) |
| TC-40  | PEND   | —     | corner case | [CC-07 High] Таймер запущен, игрок открывает другое инвентарь (сундук, верстак) — единственный способ отмены таймера остаётся движение/урон/атака | Таймер продолжает работать; отмена происходит только при движении, уроне или атаке (CC-07) |
| TC-41  | PEND   | —     | corner case | [CC-09 Medium] Сервер фиксирует минимальный клиентский drift координат (< порога) без реального движения — таймер не должен прерываться | Таймер продолжает работу; незначительный drift ниже порога не считается движением (CC-09) |
| TC-42  | PEND   | —     | unit-edge   | [spec] HomeTimerManager.startTimer: вызов при уже активном таймере для игрока — повторный запуск должен быть отклонён (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | startTimer возвращает false, существующий таймер не сбрасывается (AC-12) |
| TC-43  | PEND   | —     | error       | [spec][NOT IMPLEMENTED] HomeTimerManager.tick(): исключение внутри tick-коллбэка поглощается верхнеуровневым try/catch — состояние менеджера не повреждается | Карта активных таймеров остаётся консистентной, последующие tick-вызовы продолжают работу без NPE (CC-01) |
| TC-44  | PEND   | —     | unit-edge   | [spec] HomeTimerManager.cancelTimer: двойной вызов подряд для одного игрока — второй вызов не вызывает ошибки (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Второй cancelTimer завершается без исключений; карта таймеров не содержит записи для игрока |
| TC-45  | PEND   | —     | unit-edge   | [spec] HomeTimerManager.cancelTimersForOrder: итерация по снимку (snapshot) карты, а не по живому view — изменение карты во время итерации не вызывает ConcurrentModificationException (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Все таймеры ордера отменены без ConcurrentModificationException |
| TC-46  | PEND   | —     | integration | [spec] HomeTimerManager.onDisable: все активные BukkitTask отменены, внутренняя карта очищена после вызова (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Карта таймеров пуста; все BukkitTask переведены в состояние cancelled |
| TC-47  | PEND   | —     | unit-edge   | [spec] HomeTimerManager.executeHomeTP: чтение позиции флага из PDC в момент телепортации (не из кэша) — флаг relocated до срабатывания (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Игрок телепортируется к актуальным координатам флага из PDC, а не к координатам, зафиксированным при запуске таймера (CC-06) |
| TC-48  | PEND   | —     | error       | [spec] HomeTimerManager.executeHomeTP: isFlagActive возвращает false в момент срабатывания таймера — телепортация не выполняется (impl: src/test/kotlin/ru/kyamshanov/comminusm/service/HomeTimerManagerTest.kt) | Игрок получает ActionBar-сообщение об ошибке; телепортации нет (CC-02) |
| TC-49  | PEND   | —     | unit-edge   | [spec][NOT IMPLEMENTED] HomeTimerManager.executeHomeTP: AtomicBoolean cancelled выставлен в true до момента телепортации (гонка) — телепортация должна быть прервана | Телепортация не происходит; cancelled=true проверяется непосредственно перед вызовом teleport() |
| TC-50  | PEND   | —     | unit-edge   | [spec] HomeTimerCancelListener (PlayerMoveEvent): смещение координат < 0.1 блока — таймер не должен отменяться (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | cancelTimer не вызывается; таймер продолжает работу (CC-09) |
| TC-51  | PEND   | —     | integration | [spec] HomeTimerCancelListener (PlayerMoveEvent): смещение координат >= 0.1 блока — таймер отменяется (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | cancelTimer вызван; игрок получает уведомление об отмене (AC-05, AC-14) |
| TC-52  | PEND   | —     | unit-edge   | [spec] HomeTimerCancelListener (EntityDamageByEntityEvent): источник урона — не игрок (моб, проджектайл без владельца) — таймер не отменяется (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | cancelTimer не вызывается; обработчик возвращается без действий |
| TC-53  | PEND   | —     | integration | [spec] HomeTimerCancelListener (PlayerQuitEvent): игрок отключается при активном таймере — cancelTimer вызывается с флагом silent=true (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/HomeTimerCancelListenerTest.kt) | Таймер отменён; игрок НЕ получает ActionBar-уведомление об отмене (AC-15) |
| TC-54  | PEND   | —     | unit-edge   | [spec][NOT IMPLEMENTED] OrderMenu (кнопка «Вернуться домой»): orderId читается из ItemMeta PDC при клике, а не из БД в момент открытия меню | orderId, сохранённый в PDC кнопки, совпадает с orderId ордера игрока; БД-запрос при клике не производится |
| TC-55  | PEND   | —     | error       | [spec][NOT IMPLEMENTED] OrderMenu: клик на неверный слот или неверный материал — homeTimerManager.startTimer не вызывается | Таймер не запускается; обработчик завершается без действий (Q10 spec) |
| TC-56  | PEND   | —     | integration | [spec] OrderMenu: клик «Вернуться домой» при уже запущенном таймере — startTimer возвращает false, второй таймер не создаётся (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Игрок получает уведомление «таймер уже активен»; в карте таймеров остаётся одна запись (AC-12) |
| TC-57  | PEND   | —     | error       | [spec] OrderRespawnListener: исключение внутри обработчика PlayerRespawnEvent поглощается catch-блоком — Bukkit применяет поведение по умолчанию (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Сервер не крашится; исключение залогировано; игрок возрождается на мировом спавне (CC-01) |
| TC-58  | PEND   | —     | error       | [spec][NOT IMPLEMENTED] OrderRespawnListener: flagLoc.world == null (мир выгружен) — обработчик возвращается без изменения точки спавна | Bukkit применяет поведение по умолчанию; сообщений об ошибках нет |
| TC-59  | PEND   | —     | integration | [spec] OrderRespawnListener: EventPriority.HIGH — обработчик переопределяет точку спавна у кровати в пользу флага ордера (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/OrderRespawnListenerTest.kt) | Игрок возрождается у флага, а не у кровати; приоритет HIGH гарантирует переопределение (AC-27) |
| TC-60  | PEND   | —     | unit-edge   | [spec] FlagEventListener (FlagRelocatedEvent): oldWorld == newWorld (переезд в том же мире) — cancelTimersForOrder НЕ вызывается (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/FlagEventListenerTest.kt) | Активные таймеры ордена сохраняются; cancelTimersForOrder не вызывается (AC-18) |
| TC-61  | PEND   | —     | integration | [spec] FlagEventListener (FlagDeactivatedEvent): флаг деактивируется — cancelTimersForOrder вызывается ровно один раз с причиной FLAG_DEACTIVATED (impl: src/test/kotlin/ru/kyamshanov/comminusm/listener/FlagEventListenerTest.kt) | Все таймеры ордера отменены; cancelTimersForOrder вызван один раз (AC-13) |

> **Description** = что тестировать и как (однострочная сводка).
> **To be** = ожидаемый наблюдаемый результат.

---

> Всё ниже заполняется тестировщиком вручную. Блок `TC-00: Template`
> остаётся неизменным — копируйте и заполняйте только для конкретных TC,
> когда нужна детализация (обычно при сбое). Агенты НЕ генерируют секции TC-NN.

---

## TC-00: Template

**Description:**
что тестировать, как тестировать

**Steps:**

1. шаг 1

**As is:**
текущее поведение

**To be:**
ожидаемое поведение

---

## Defects log

> Append-only. Each entry references a TC by id. AI agents (@TestRunner / @BugFixer) maintain this section.

- (пусто)
