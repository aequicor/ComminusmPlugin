# Тест-кейсы — Приваты: Ордера и Фронты

**Module:** comminusm
**Feature:** privates-orders-fronts
**Generated:** 2026-05-05 by @QA
**Spec:** `[[reference/comminusm/spec/privates-orders-fronts]]`
**Requirements:** `[[concepts/comminusm/requirements/privates-orders-fronts]]`

---

## Status legend

`PEND`  •  `PASS`  •  `FAIL`  •  `SKIP`

## Defect lifecycle

`OPEN` → `FIXED` → `VERF`

---

> Filled by AI agents (@QA, @TestRunner, @BugFixer). The **Notes** column may be edited by the manual tester to record bug root cause or remarks.

| ID    | Status | Notes                                                                 | Type        | Pre-requirements                                                                                  | To be                                                                                                 |
| ----- |--------|-----------------------------------------------------------------------| ----------- | ------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-01 | PASS   | `OrderService.kt:create`                                              | acceptance  | У игрока нет Ордера                                                                               | Ордер создан уровня 1, радиус из конфига, выдан предмет флага                                         |
| TC-02 | PASS   | `OrderService.kt:create` возвращает null                              | acceptance  | У игрока уже есть Ордер                                                                           | Новый Ордер не создан; игрок уведомлён, что Ордер уже есть                                            |
| TC-03 | PASS   | Создание заблокированно до освобождения места                         | acceptance  | Инвентарь игрока полон (нет пустых слотов)                                                        | Ордер создан, но предмет флага выброшен на землю ИЛИ создание заблокировано до освобождения места     |
| TC-04 | PEND   | Логика сборки предмета                                                | acceptance  | Игрок получил предмет флага Ордера                                                                | Предмет — WHITE_BANNER с названием, содержащим «Флаг Ордера», и кастомным описанием                   |
| TC-05 | PEND   | Обработчик команды                                                    | acceptance  | Игрок имеет право выполнять `/party`                                                              | Открывается инвентарь PartyMenu с корректно заполненными слотами                                      |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-06 | PEND   | `OrderFlagListener.kt`, `OrderService.kt:activate`                    | acceptance  | У игрока есть неактивированный Ордер, предмет — флаг Ордера                                       | Ордер активирован; координаты центра сохранены; чанк помечен в PDC                                    |
| TC-07 | PEND   | `OrderService.kt:checkOverlap`                                        | acceptance  | Другой Ордер существует в пределах `minDistanceBetweenCenters`                                    | Установка отменена; игрок уведомлён о пересечении                                                     |
| TC-08 | PEND   | `OrderFlagListener.kt`                                                | acceptance  | У игрока нет записи об Ордере                                                                     | Установка отменена; игрок уведомлён об отсутствии Ордера                                              |
| TC-09 | PEND   | `OrderFlagListener.kt`                                                | acceptance  | У игрока есть активированный Ордер                                                                | Установка отменена; игрок уведомлён, что Ордер уже активен                                            |
| TC-10 | PEND   | `OrderMenu.kt`                                                        | acceptance  | У игрока есть активированный Ордер                                                                | Новый предмет флага Ордера добавлен в инвентарь                                                       |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-11 | PEND   | `OrderService.kt:upgrade`, `OrderRepository.kt:updateLevel`           | acceptance  | Уровень Ордера < максимального, достаточно трудодней                                              | Уровень увеличен на 1, радиус обновлён, трудодни списаны, БД обновлена                                |
| TC-12 | PEND   | `OrderService.kt:upgrade` возвращает false                            | acceptance  | Уровень Ордера < максимального, баланс < стоимости                                                | Улучшение отклонено; игрок уведомлён о нехватке трудодней                                             |
| TC-13 | PEND   | Логика отрисовки `OrderMenu.kt`                                       | acceptance  | Ордер на максимальном уровне (5)                                                                  | Кнопка улучшения скрыта или неактивна; нажатие не имеет эффекта                                       |
| TC-14 | PEND   | `OrderService.kt:upgrade`, `PluginConfig.kt`                          | acceptance  | Ордер уровня 1, радиус 2                                                                          | Новый радиус = 3; размер изменился с 5 на 7; защита применяется немедленно                            |
| TC-15 | PEND   | Репозиторий/сервис трудодней                                          | acceptance  | Баланс = 100, стоимость уровня 2 = 30                                                             | Новый баланс = 70; баланс в репозитории обновлён                                                      |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-16 | PEND   | `FlagDeletionConfirmListener.kt`, `OrderService.kt:deleteByOwner`     | acceptance  | У игрока есть активированный Ордер, он смотрит на свой флаг                                       | Ордер удалён из БД, блок флага убран, предмет флага выброшен, кеш чанка очищен                        |
| TC-17 | PEND   | `FlagDeletionConfirmListener.kt`                                      | acceptance  | У игрока есть активированный Ордер, открыт GUI подтверждения                                      | Инвентарь закрыт; Ордер остаётся нетронутым; блок флага сохраняется                                   |
| TC-18 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | У игрока есть активированный Ордер                                                                | Открывается GUI подтверждения удаления; событие ломания отменено                                      |
| TC-19 | PEND   | `FlagDeletionConfirmListener.kt`                                      | acceptance  | Удаление Ордера подтверждено                                                                      | Кастомный предмет флага Ордера (WHITE_BANNER с описанием) выброшен на месте блока                     |
| TC-20 | PEND   | `ChunkCacheManager.kt:removeOrderChunk`                               | acceptance  | Ордер был активирован и помечен в PDC чанка                                                       | Ключ `order_owner` удалён из PDC чанка; `hasOrderMarker` возвращает false                             |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-21 | PEND   | `FrontFlagListener.kt`                                                | acceptance  | У игрока нет записи об Ордере                                                                     | Установка отменена; игрок уведомлён о необходимости Ордера                                            |
| TC-22 | PEND   | `FrontFlagListener.kt`                                                | acceptance  | У игрока есть неактивированный Ордер                                                              | Установка отменена; игрок уведомлён о необходимости активации Ордера                                  |
| TC-23 | PEND   | `FrontFlagListener.kt`, `BlockListener.kt` — проверка пересечения     | acceptance  | У игрока есть активированный Ордер; целевая точка внутри чужого Ордера                            | Установка отменена; игрок уведомлён, что он на чужой территории                                       |
| TC-24 | PEND   | `WorkFrontService.kt:activate`, `FrontFlagListener.kt`                | acceptance  | У игрока уже есть активированный Фронт                                                            | Старый блок флага Фронта убран из мира; новый Фронт активирован в новом месте                         |
| TC-25 | PEND   | `WorkFrontService.kt:activate`, `ChunkCacheManager.kt:markFrontChunk` | acceptance  | У игрока есть активированный Ордер, нет существующего Фронта                                      | Фронт создан в БД; чанк помечен `front_owner`; доступно меню `FrontMenu`                              |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-26 | PEND   | `FrontMenu.kt`, `WorkFrontService.kt:deactivate`                      | acceptance  | У игрока есть активированный Фронт                                                                | Фронт деактивирован (баннер убран, запись из БД удалена, чанк размечен); выдан новый предмет флага    |
| TC-27 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | У игрока есть активированный Фронт                                                                | Фронт деактивирован; кастомный предмет флага Фронта выброшен на месте                                 |
| TC-28 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | Игрок рядом с флагом Фронта другого игрока                                                        | Ломание отменено; игрок уведомлён                                                                     |
| TC-29 | PEND   | `BlockListener.kt:isForeignFrontSupportBlock`                         | acceptance  | Флаг Фронта установлен на блок                                                                    | Ломание отменено (опорный блок защищён как часть территории Фронта)                                   |
| TC-30 | PEND   | `FrontMenu.kt`                                                        | acceptance  | У игрока есть активированный Фронт; инвентарь полон                                               | Фронт всё равно деактивирован; предмет флага выброшен на землю ИЛИ перенос заблокирован               |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-31 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | Игрок внутри своего активированного Ордера                                                        | Блок ломается нормально; выпадают предметы                                                            |
| TC-32 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | Игрок внутри Ордера другого игрока                                                                | Ломание отменено; игрок уведомлён                                                                     |
| TC-33 | PEND   | `BlockListener.kt:onBlockPlace`                                       | acceptance  | Игрок внутри своего активированного Ордера                                                        | Блок установлен успешно                                                                               |
| TC-34 | PEND   | `BlockListener.kt:onBlockPlace`                                       | acceptance  | Игрок внутри Ордера другого игрока                                                                | Установка отменена; игрок уведомлён                                                                   |
| TC-35 | PEND   | `BlockListener.kt:onPlayerInteract`                                   | acceptance  | Игрок внутри своего активированного Ордера                                                        | Взаимодействие успешно                                                                                |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-36 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | Игрок внутри своего активированного Фронта                                                        | Блок ломается нормально                                                                               |
| TC-37 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | Игрок внутри Фронта другого игрока                                                                | Ломание отменено; игрок уведомлён                                                                     |
| TC-38 | PEND   | `BlockListener.kt:onBlockPlace`                                       | acceptance  | Игрок внутри своего активированного Фронта                                                        | Блок установлен успешно                                                                               |
| TC-39 | PEND   | `BlockListener.kt:onBlockPlace`                                       | acceptance  | Игрок внутри Фронта другого игрока                                                                | Установка отменена; игрок уведомлён                                                                   |
| TC-40 | PEND   | `BlockListener.kt:onPlayerInteract`                                   | acceptance  | Игрок внутри своего активированного Фронта                                                        | Взаимодействие успешно                                                                                |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-41 | PEND   | `BlockListener.kt:onBlockBreak`                                       | acceptance  | У игрока нет ни Ордера, ни Фронта                                                                 | Ломание отменено; игрок уведомлён                                                                     |
| TC-42 | PEND   | `BlockListener.kt:onBlockPlace`                                       | acceptance  | У игрока нет ни Ордера, ни Фронта                                                                 | Установка отменена; игрок уведомлён                                                                   |
| TC-43 | PEND   | `BlockListener.kt:onPlayerInteract`                                   | acceptance  | У игрока нет ни Ордера, ни Фронта                                                                 | Взаимодействие отменено; игрок уведомлён                                                              |
| TC-44 | PEND   | Логика сообщений отклонения `BlockListener.kt`                        | acceptance  | У игрока есть активированный Ордер; целевой блок вне радиуса Ордера                               | Действие отклонено с сообщением о том, что игрок вне своей территории                                 |
| TC-45 | PEND   | Логика сообщений отклонения `BlockListener.kt`                        | acceptance  | У игрока нет Ордера                                                                               | Действие отклонено с сообщением о необходимости получить Ордер                                        |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-46 | PEND   | `ExplosionListener.kt`                                                | integration | TNT/Крипер рядом с флагом Ордера                                                                  | Блок флага Ордера остаётся нетронутым; его нет в списке блоков взрыва                                 |
| TC-47 | PEND   | `ExplosionListener.kt`                                                | integration | TNT/Крипер рядом с флагом Фронта                                                                  | Блок флага Фронта остаётся нетронутым; его нет в списке блоков взрыва                                 |
| TC-48 | PEND   | `BlockListener.kt:onBlockBreak`                                       | integration | Игрок рядом с флагом Ордера другого игрока                                                        | Ломание отменено; игрок уведомлён                                                                     |
| TC-49 | PEND   | `BlockListener.kt:onBlockBreak`                                       | integration | Игрок рядом с флагом Фронта другого игрока                                                        | Ломание отменено; игрок уведомлён                                                                     |
| TC-50 | PEND   | `BlockListener.kt:onBlockBreak`, `FlagDeletionConfirmListener.kt`     | integration | У игрока есть активированный Ордер                                                                | Открывается GUI подтверждения удаления; событие ломания отменено                                      |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-51 | PEND   | `PartyMenu.kt`                                                        | manual      | У игрока есть Ордер и Фронт                                                                       | Слот 20 — кнопка Ордера, 24 — кнопка Фронта, 31 — казна, 40 — баланс трудодней                        |
| TC-52 | PEND   | `OrderMenu.kt`                                                        | manual      | У игрока есть активированный Ордер                                                                | Заголовок «Ордер №{id}»; слот 20 — инфо, 22 — размер, 24 — улучшение, 31 — восстановление, 39 — назад |
| TC-53 | PEND   | `FrontMenu.kt`                                                        | manual      | У игрока есть активированный Фронт                                                                | Заголовок «Трудовой Фронт»; слот 20 — инфо, 22 — радиус, 24 — перенос, 39 — назад                     |
| TC-54 | PEND   | `TreasuryMenu.kt`, `PartyMenu.kt`                                     | manual      | Игрок выполнил `/party`                                                                           | Открывается TreasuryMenu; слоты для сдачи ресурсов работают                                           |
| TC-55 | PEND   | Обработчик команды, `AdminMenu.kt`                                    | manual      | У игрока нет права `comminusm.admin`                                                              | Команда отклонена с сообщением о недостатке прав; AdminMenu не открывается                            |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-56 | PEND   | `ChunkCacheManager.kt:markOrderChunk`                                 | integration | Игрок устанавливает флаг Ордера                                                                   | PDC чанка содержит ключ `order_owner` с UUID игрока                                                   |
| TC-57 | PEND   | `ChunkCacheManager.kt:markFrontChunk`                                 | integration | Игрок устанавливает флаг Фронта                                                                   | PDC чанка содержит ключ `front_owner` с UUID игрока                                                   |
| TC-58 | PEND   | `ChunkCacheManager.kt:removeOrderChunk`                               | integration | Ордер был активирован и помечен                                                                   | Ключ `order_owner` удалён из PDC чанка                                                                |
| TC-59 | PEND   | `ChunkCacheManager.kt:removeFrontChunk`                               | integration | Фронт был активирован и помечен                                                                   | Ключ `front_owner` удалён из PDC чанка                                                                |
| TC-60 | PEND   | Персистентность PersistentDataContainer в сохранении мира             | integration | Ордер/Фронт активирован, сервер работает                                                          | Ключи `order_owner` и `front_owner` сохранились после перезапуска                                     |
| TC-ID | Статус | Примечания                                                            | acceptance  | Предусловия                                                                                       | Ожидаемый результат                                                                                   |
| TC-61 | PEND   | `OrderService.kt:checkOverlap`, `PluginConfig.kt`                     | corner case | У игрока А есть Ордер в (0,0,0); игрок Б хочет Ордер в (29,0,0); `minDistanceBetweenCenters` = 30 | Отклонение пересечения; расстояние < 30                                                               |
| TC-62 | PEND   | Математика радиуса в `FrontFlagListener.kt`                           | corner case | Радиус собственного Ордера = 2 (размер 5); флаг Фронта ставится на граничной координате           | Установка разрешена, если внутри своего Ордера; отклонена, если вне или внутри чужого                 |
| TC-63 | PEND   | `OrderService.kt:upgrade`, `OrderMenu.kt`                             | corner case | У игрока есть Ордер уровня 1, но флаг никогда не ставился                                         | Улучшение должно быть заблокировано, либо требуется сначала активировать Ордер                        |
| TC-64 | PEND   | `OrderService.kt:deleteByOwner`                                       | corner case | У игрока есть неактивированный Ордер                                                              | Запись об Ордере удалена из БД; блока ломать не нужно; предмета выбрасывать не нужно                  |
| TC-65 | PEND   | `OrderService.kt:upgrade`, защита баланса в репозитории               | corner case | Баланс игрока = 10; стоимость улучшения = 30                                                      | Улучшение отклонено; баланс остаётся 10; никогда не отрицательный                                     |
| TC-66 | PEND   | `FrontFlagListener.kt`                                                | corner case | У игрока есть активированный Ордер в `world1`                                                     | Поведение зависит от дизайна: разрешено или отклонено. Текущий код явно не проверяет равенство миров. |
| TC-67 | PEND   | `OrderFlagListener.kt` проверяет контекст `player`                    | corner case | Раздатчик загружен предметом WHITE_BANNER с описанием флага                                       | Блок баннера установлен, но **не** считается активацией Ордера; Ордер не активирован                  |
| TC-68 | PEND   | Персистентность PDC чанков                                            | corner case | Ордер активирован в чанке; чанок позже выгружен                                                   | Данные чанка загружены с диска; маркеры PDC на месте; защита работает корректно                       |
| TC-69 | PEND   | `OrderService.kt:create` (разные владельцы)                           | corner case | Два игрока без Ордеров одновременно нажимают слот Ордера                                          | Каждый получает свой Ордер; конфликта нет, так как разные `owner_uuid`                                |
| TC-70 | PEND   | Обработка исключений `DatabaseManager.kt`                             | corner case | Файл SQLite становится недоступен (симулировать сбой)                                             | Корректная обработка: операция прервана, игрок уведомлён, данные не повреждены                        |
| TC-User-01 | PASS | DEF-User-01: `WorkFrontService.deactivate()` не ломал блок баннера | bug | У игрока есть активированный Фронт | Старый баннер удалён из мира при деактивации Фронта |
| TC-User-02 | PASS | DEF-User-02: `isForeignFrontSupportBlock()` проверял только RED_BANNER | bug | У игрока есть активированный Ордер или Фронт | Флаг не выпадает при разрушении опорного блока посторонним игроком |

---

## Defects Log

| ID | Status | Description | Created | Fixed |
|----|--------|-------------|---------|-------|
| DEF-User-01 | FIXED | Old front flag not removed on Move (orphaned banner) | 2026-05-05 | 2026-05-05 |
| DEF-User-02 | FIXED | Order/Front flag drops when support block broken (missing WHITE_BANNER check) | 2026-05-05 | 2026-05-05 |

---

> Detailed sections below are written by the manual tester.

---

## TC-User-01: Не удаляется старый флаг при "Перенести трудовой фронт"

**Pre-requirements:**

* У пользователя уже установлен флаг трудового фронта в мире

**Steps:**

1. ввести команду /партия
2. установить новый флаг

**As is:**

В мире два флага

**To be:**

Старый флаг уничтожается и в мире у тользователя только 1 флаг

## TC-User-02: Флаг Ордера или флаг Трудового фронта можен быть уничножен если сломать под ним блок

**Pre-requirements:**

* У пользователя уже установлен флаг Ордера или фронта

**Steps:**

1. сломать блок на котором установлен флан

**As is:**

Флаг выпадает как предмет

**To be:**

Флаг остаётся на месте. Только взладелец может сломать флаг