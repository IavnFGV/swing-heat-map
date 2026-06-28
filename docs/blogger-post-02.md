# Swing - 10 лет спустя - 3

Статус: черновик для третьей публикации.

Тема: добавляем runtime profiler и debug window.

Рекомендуемые labels:

```text
java, swing, performance, profiler, visualization
```

Перед публикацией желательно добавить:

- screenshot версии `2ff60c4 Add runtime profiling for heatmap rendering pipeline`;
- screenshot окна `Debug info`;
- screenshot кода `Profiler` или мест, где вызывается `Profiler.measure(...)`.

---

В прошлой части мы посчитали, сколько работы делает первая версия heatmap.

Получилось не страшно, но уже интересно:

```text
примерно 1.6 млн копирований int / сек
примерно 1.6 млн проверок ячеек / сек
плюс цвет, координаты и fillRect для непустых ячеек
```

Для первого прототипа это нормально. Он работает. Он рисует. Он даже выглядит так, будто у него есть план на жизнь.

Но дальше нагрузка начинает расти:

- tick становится чаще;
- событий становится больше;
- генерация данных усложняется;
- рисование остаётся на Swing;
- EDT всё ещё один, маленький и беззащитный.

И вот здесь появляется опасный момент.

Можно начать гадать.

`System.arraycopy` виноват? `new Color`? `fillRect`? Генератор событий? Swing? Windows? Фаза луны? Java опять 42?

Гадать приятно, но бесполезно. Поэтому следующий шаг — не оптимизация.

Следующий шаг — добавить измерения.

## Не настоящий profiler, а первый фонарик

Конечно, можно сразу открыть нормальный профилировщик. И позже это придётся сделать.

Но в прототипе мне хотелось сначала иметь маленькое runtime-окно рядом с приложением: чтобы прямо во время запуска видеть базовые числа.

Не идеально точные. Не академические. Просто достаточно полезные, чтобы перестать спорить с воображаемым bottleneck.

Появляется отдельное окно:

```java
public class InfoFrame extends JFrame {

    private final JTextArea textArea = new JTextArea();

    public InfoFrame() {
        super("Debug info");

        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        textArea.setEditable(false);

        setContentPane(new JScrollPane(textArea));
        setSize(420, 500);
        setLocation(1250, 100);
    }

    public void updateText(String text) {
        textArea.setText(text);
    }
}
```

Да, это просто `JFrame` с `JTextArea`.

Никакого реактивного dashboard, графиков, Prometheus и маленького Kubernetes под столом.

Просто окно, куда можно каждую итерацию писать состояние приложения.

> Здесь вставить screenshot окна приложения и `Debug info`: `artifacts/profiler/01-profiler-ui.png`.

## Что показывать

В `HeatmapPanel` появляется метод `updateDebugInfo()`.

Он собирает текст:

```java
sb.append("=== PERFORMANCE ===\n");
sb.append("Mode               : ").append(scrollMode).append('\n');
sb.append("FPS                : ").append(String.format("%.0f", fps)).append('\n');
sb.append("Generate ms        : ").append(String.format("%.3f", lastGenerateMs)).append('\n');
sb.append("Memory MB          : ").append(usedMb).append('\n');
```

Первый блок — базовое самочувствие:

- какой режим прокрутки включён;
- сколько кадров в секунду примерно рисуется;
- сколько заняла генерация данных;
- сколько памяти сейчас используется.

Это не заменяет профилировщик, но сразу отвечает на вопрос: приложение живое или уже делает вид.

Дальше блок нагрузки:

```java
sb.append("=== LOAD ===\n");
sb.append("Events/tick        : ").append(eventsPerTick).append('\n');
sb.append("Approx events/sec  : ").append(eventsPerTick * 60L).append('\n');
sb.append("Total events       : ").append(totalEvents).append('\n');
sb.append("Total traded volume: ").append(totalTradedVolume).append('\n');
```

Здесь важно видеть не только FPS, но и то, под какой нагрузкой он получился.

60 FPS при почти пустой программе и 60 FPS при десятках тысяч событий в секунду — это разные 60 FPS. Одни пришли с отпуска, другие после смены на заводе.

Дальше — состояние рынка:

```java
sb.append("=== MARKET ===\n");
sb.append("Best bid           : ").append(bestBid).append('\n');
sb.append("Best ask           : ").append(bestAsk).append('\n');

if (bestBid != -1 && bestAsk != -1) {
    sb.append("Spread             : ").append(bestAsk - bestBid).append('\n');
    sb.append("Mid                : ").append((bestBid + bestAsk) / 2.0).append('\n');
}
```

Это уже не главная тема статьи, но полезный контекст: данные не просто случайно мигают, у них есть bid/ask и spread.

## Маленький Profiler

Сам profiler получился совсем небольшим:

```java
public class Profiler {

    private static final Map<EventType, Double> metrics = new ConcurrentHashMap<>();

    public static void measure(EventType name, Runnable runnable) {
        long start = System.nanoTime();

        try {
            runnable.run();
        } finally {
            metrics.put(
                    name,
                    (System.nanoTime() - start) / 1_000_000.0
            );
        }
    }

    public static Double get(EventType name) {
        return metrics.get(name);
    }

    public enum EventType {
        APPLY_EVENTS,
        GEN_DATA,
        PAINT
    }
}
```

Идея простая:

1. запомнить `System.nanoTime()` перед операцией;
2. выполнить код;
3. посчитать разницу;
4. сохранить последнее значение в миллисекундах.

Это не статистика, не percentiles, не flame graph.

Это просто первый фонарик: куда примерно уходит время прямо сейчас.

> Здесь вставить screenshot кода `Profiler`: `artifacts/profiler/02-profiler-code.png`.

## Куда навесить измерения

Первое место — генерация данных:

```java
Timer timer = new Timer(16, e -> {
    long start = System.nanoTime();

    Profiler.measure(GEN_DATA, this::generateFakeData);

    lastGenerateMs = (System.nanoTime() - start) / 1_000_000.0;

    updateDebugInfo();
    repaint();
});
```

Теперь видно, сколько занимает подготовка данных перед перерисовкой.

Внутри генерации отдельно измеряется применение событий:

```java
private void applyEvents() {
    Profiler.measure(APPLY_EVENTS, () -> {
        for (int i = 0; i < eventsPerTick; i++) {
            BookEvent event = eventGenerator.nextEvent();
            orderBook.apply(event);

            totalEvents++;

            if (event.type() == EventType.TRADE) {
                totalTradedVolume += event.volume();
            }
        }
    });
}
```

Это важно, потому что теперь можно отделить:

```text
генерация всей колонки
от применения пачки событий
```

Третье место — отрисовка:

```java
@Override
protected void paintComponent(Graphics g) {
    Profiler.measure(PAINT, () -> draw(g));
}
```

Теперь хотя бы грубо видно, сколько занимает Swing-рисование.

И это уже меняет разговор.

До этого вопрос звучал так:

```text
мне кажется, тормозит вот это
```

После этого:

```text
GEN_DATA занимает столько-то
APPLY_EVENTS занимает столько-то
PAINT занимает столько-то
```

Меньше магии. Больше скучной пользы.

## Режимы прокрутки: первая развилка

В этом же месте появляется важная подготовка к будущей оптимизации:

```java
public enum ScrollMode {
    SHIFT_COPY,
    CIRCULAR_BUFFER
}
```

И управление с клавиатуры:

```java
1  -> SHIFT_COPY
2  -> CIRCULAR_BUFFER
+  -> increase load
-  -> decrease load
```

Зачем это нужно?

Потому что в прошлой части мы увидели цену `SHIFT_COPY`: при каждой новой колонке сдвигается почти вся история.

Но прежде чем переписывать всё на кольцевой буфер, удобно иметь возможность переключать режимы прямо в приложении и смотреть разницу.

То есть это ещё не финальная оптимизация.

Это подготовка к честному сравнению.

## Что важно не перепутать

Такой profiler легко переоценить.

Он показывает полезные числа, но у него есть ограничения:

- он хранит только последнее измерение;
- он не показывает распределение;
- он не показывает GC-паузы нормально;
- он сам тоже выполняется внутри приложения;
- Swing `repaint()` не означает немедленный `paintComponent()`;
- EDT может объединять перерисовки.

То есть это не “истина”.

Это приборная панель. Спидометр тоже не объясняет физику двигателя, но без него ехать 180 по ощущениям — сомнительное занятие.

## Итог

После первых двух частей у нас была рабочая heatmap и понимание её базовой цены.

Теперь появился следующий слой:

```text
не просто рисуем
а наблюдаем, что происходит во время работы
```

Мы добавили:

- debug window;
- FPS;
- время генерации;
- memory usage;
- нагрузку events/tick и events/sec;
- простые метрики `GEN_DATA`, `APPLY_EVENTS`, `PAINT`;
- переключатель `SHIFT_COPY` / `CIRCULAR_BUFFER`.

Это всё ещё не промышленный profiling.

Но это важный переход: дальше можно не гадать, а сравнивать.

В следующей части уже можно будет поставить рядом два подхода:

```text
SHIFT_COPY
vs
CIRCULAR_BUFFER
```

И проверить главную идею: что если проблема не в том, что мы медленно копируем массив, а в том, что мы вообще его копируем.

Код проекта:

https://github.com/IavnFGV/swing-heat-map
