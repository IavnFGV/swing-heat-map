# Swing Heat Map — experiment notes

Этот файл хранит фактические запуски, параметры, измерения и проверенные гипотезы.

Главный план и текущее состояние проекта находятся в `AI/002-project-roadmap.md`.

## Правило записи

Для каждого содержательного запуска копировать шаблон:

```text
Дата:
Версия/commit:
Java:
Машина:
Режим данных:
Scroll mode:
Размеры окна:
Events/bucket cap:
Approx events/sec:
Time scale:
FPS:
Generate ms:
Memory:
Profiler:
Что проверяли:
Что увидели:
Вывод:
Артефакты:
Следующий тест:
```

Не заполнять неизвестные значения по памяти. Если показатель не измерялся, писать `не измерялся`.

---

## Environment

Дата проверки: 2026-06-24.

```text
OS: Windows 11 10.0 amd64
Java: Eclipse Temurin OpenJDK 17.0.19+10
Gradle: 8.10
Build: .\gradlew.bat clean build
Run: java -cp build/classes/java/main io.drozda.coding.demo.Starter
```

Результат автоматической проверки:

- Gradle build завершился успешно.
- Тестов в проекте пока нет.
- Процесс Swing-приложения успешно стартовал и продолжал работать через 4 секунды.
- Визуальная корректность окна вручную пока не проверена.
- При наличии `BTCUSDT-aggTrades-2026-06-12.csv` приложение по умолчанию запускает `HISTORICAL_REPLAY`.

Helper запуска:

```text
artifacts/current/start.cmd
```

Он собирает проект и запускает `io.drozda.coding.demo.Starter`. Корень репозитория определяется относительно расположения самого helper, поэтому запускать его можно из любого рабочего каталога.

## Управление

```text
1  -> SHIFT_COPY
2  -> CIRCULAR_BUFFER
M  -> HISTORICAL_REPLAY / SYNTHETIC_STRESS
C  -> очистить состояние
[  -> уменьшить временной масштаб
]  -> увеличить временной масштаб
+  -> увеличить нагрузку
-  -> уменьшить нагрузку
```

## Первый ручной прогон

Статус: медиафайлы получены; первичная проверка выполнена.

Цель: зафиксировать современное состояние приложения, не анализируя производительность глубоко.

Нужно получить:

- `artifacts/current/01-current-ui.png`
- `artifacts/current/02-debug-info.png`
- `artifacts/current/03-current-ui.mp4` — 10–20 секунд, без голоса
- одну заполненную запись запуска по шаблону выше

Порядок:

1. Выполнить `.\gradlew.bat clean build`.
2. Выполнить `java -cp build/classes/java/main io.drozda.coding.demo.Starter`.
3. Подождать 15–30 секунд.
4. Убедиться, что heatmap, depth и activity panels обновляются.
5. Снять главное окно вместе с debug window.
6. Нажать `M` и проверить переключение режима данных.
7. Нажать `1`, затем `2` и проверить смену scroll mode в debug window.
8. Несколько раз нажать `+`, не пытаясь пока найти предел.
9. Записать наблюдаемые FPS, Generate ms, Memory и Events/bucket cap.
10. Вернуться в `CIRCULAR_BUFFER`, записать 10–20 секунд экрана.

На этом первый прогон закончен. Профилирование и baseline в эту сессию не начинать.

### Результат первого ручного прогона

Дата: 2026-06-24.

Артефакты:

- `artifacts/current/01-current-ui.png` — общий вид UI и debug window;
- `artifacts/current/02-debug-info.png` — debug window под резко увеличенной настройкой нагрузки;
- `artifacts/current/03-current-ui.mp4` — 58 секунд исходной записи экрана;
- `artifacts/current/start.cmd` — воспроизводимый helper сборки и запуска.

Визуально подтверждено по `01-current-ui.png`:

```text
Data mode: HISTORICAL_REPLAY
Scroll mode: CIRCULAR_BUFFER
FPS: 50
Generate ms: 3.453
Memory: 119 MB
Events/bucket cap: 1000
Approx events/sec: 60000
Price levels: 2000
Time buckets: 5000
RENDER_FRAME_BUILD: 3.3904 ms
HEATMAP_PAINT: 0.661 ms
```

Визуально подтверждено по `02-debug-info.png`:

```text
Data mode: HISTORICAL_REPLAY
Scroll mode: CIRCULAR_BUFFER
FPS: 49
Generate ms: 9.143
Memory: 223 MB
Events/bucket cap: 1342177728
Approx events/sec: 80530636800
RENDER_FRAME_BUILD: 9.044 ms
HEATMAP_PAINT: 0.9744 ms
```

Наблюдения:

- текущий UI работает и показывает heatmap, depth/COB, Trades/CVD и встроенные метрики;
- первый screenshot подходит как доказательство текущего состояния, но не как финальный публичный screenshot: в кадре остаются IDE и лишний фон;
- отдельный debug screenshot читаемый и полезный для исследования;
- видео сохранено как сырой материал; его содержимое покадрово пока не проверялось;
- многократное нажатие `+` позволяет получить неправдоподобно высокий `Events/bucket cap`;
- в `HISTORICAL_REPLAY` эта настройка не равна реально обработанным событиям в секунду, поэтому её нельзя использовать как фактическую throughput-метрику;
- несмотря на огромный cap, FPS сохранился около 49, а основное измеренное время оказалось в `RENDER_FRAME_BUILD`.

Предварительная гипотеза:

- `eventsPerTick` не ограничен сверху и при увеличении может дойти до переполнения;
- historical generator возвращает только доступные записи, поэтому cap не отражает реальную нагрузку;
- для нагрузочного сравнения следует использовать `SYNTHETIC_STRESS` и отдельно записывать фактически обработанное количество событий.

Это пока наблюдение, а не окончательный вывод о bottleneck.

### Решение о роли текущей версии

Дата: 2026-06-24.

Текущая версия используется только как вступительная демонстрация результата:

> Вот что сейчас получилось. Оно визуально работает и выглядит достаточно цельно. Теперь вернёмся назад и посмотрим, как проект к этому пришёл.

Для этого кадра не требуется:

- искать максимальную нагрузку;
- доказывать производительность текущей версии;
- исправлять все существующие баги;
- использовать `Events/bucket cap` как benchmark;
- отдельно тестировать `SYNTHETIC_STRESS`.

Полученных артефактов достаточно для вступления. Наблюдение про неограниченный рост cap сохранено как известная проблема, но не входит в первую публичную историю.

Следующее исследование начинается с исторического baseline. Именно там должны появиться воспроизводимая проблема, измерения и сравнение.

---

## Baseline investigation

### Первый рабочий коммит

Дата проверки: 2026-06-24.

```text
Commit: b0658a523b568fc877b93613a16fa5263a639b8b
Message: initial commit - success render with stubs
Worktree: D:\projects\swing-heat-map-baseline
Build: successful
Automated launch: process remained alive after 4 seconds
```

Устройство:

```text
One JPanel
int[200][800]
Swing Timer: 100 ms
100 synthetic updates per tick
System.arraycopy for every price row
Full 200 × 800 scan in paintComponent
new Color(...) for every non-empty rendered cell
```

Решение:

- использовать `b0658a5` как визуальное начало истории и объяснение первой реализации;
- не считать его окончательным performance baseline без дополнительной проверки;
- сначала сохранить чистый screenshot/video этого состояния.

Helper запуска:

```text
artifacts/baseline/start.cmd
```

### Кандидат нагрузочного baseline

Коммит `45d73e8` является более естественным началом performance-проблемы:

```text
Commit: 45d73e8
Message: Add synthetic order book state for heatmap rendering
Swing Timer: 16 ms
Events per tick: 1000
Approx configured rate: 60000 events/sec
Matrix: 200 × 800
```

Он сохраняет простую архитектуру первого варианта, но добавляет:

- synthetic order book;
- применение 1000 событий на каждом tick;
- заполнение новой колонки из состояния order book;
- целевой интервал обновления около 60 раз в секунду.

Коммит `c5c2073` переносит эти параметры в `MarketConfig` и выводит configured events/sec на экран. Он может оказаться удобнее для записи и изменения нагрузки.

Предварительная структура исторического сравнения:

```text
b0658a5  -> первый работающий экран и объяснение идеи
45d73e8 или c5c2073 -> воспроизводимый нагрузочный baseline
2ff60c4 -> появление встроенного profiling и сравнения scroll modes
```

Окончательный load baseline пока не выбран. Нужно собрать и визуально сравнить `45d73e8` и `c5c2073`.

### Найденная проблема запуска

Дата: 2026-06-24.

Наблюдение:

- первая версия `artifacts/current/start.cmd` завершала Gradle-сборку, но не запускала приложение.

Причина:

- Windows batch-файл должен вызывать другой `.bat`/`.cmd` через `call`, если после него нужно продолжить выполнение;
- без `call` управление после `gradlew.bat` не вернулось в `start.cmd`.

Исправление:

- используется `call gradlew.bat clean build`;
- корень репозитория определяется независимо от текущего каталога;
- добавлена остановка при ошибке сборки.
