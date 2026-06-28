# Swing - 10 лет спустя - 3

Статус: черновик для третьей публикации.

Тема: synthetic order book — как оранжевый шум превращается в состояние стакана.

Рекомендуемые labels:

```text
java, swing, performance, orderbook, visualization
```

Перед публикацией желательно добавить:

- screenshot версии `75ee2b6 best bid best ask as curves on panel`;
- screenshot кода `OrderBook`;
- screenshot кода `EventGenerator`.

---

В прошлой части heatmap была честно разобрана до винтиков:

- есть двумерный массив;
- время — это столбцы;
- цена — это строки;
- объём превращается в яркость;
- `paintComponent` превращает всё это в оранжевые прямоугольники.

Но была одна проблема.

Это всё ещё был просто красивый шум.

Не рынок, не стакан, не заявки, не сделки. Просто генератор случайно тыкал volume в последнюю колонку. Такой аквариум с апельсиновыми пикселями.

Теперь сделаем следующий шаг: добавим synthetic order book.

> Здесь вставить screenshot версии `75ee2b6`: `artifacts/orderbook/01-orderbook-ui.png`.

## Что поменялось

В первой версии новая колонка заполнялась напрямую:

```java
int price = middle + random.nextInt(80) - 40;

if (price >= 0 && price < PRICE_LEVELS) {
    heatmap[price][TIME_BUCKETS - 1] += random.nextInt(100);
}
```

То есть код сразу писал случайный объём в heatmap.

На следующем шаге этого уже мало. Теперь между генератором событий и картинкой появляется промежуточное состояние:

```java
private final EventGenerator eventGenerator = new EventGenerator();
private final OrderBook orderBook = new OrderBook();
```

И это важный момент.

Heatmap больше не является местом, куда генератор напрямую кидает случайные квадратики. Теперь heatmap становится снимком текущего состояния стакана.

Грубо:

```text
события
→ OrderBook
→ объёмы по ценовым уровням
→ новая колонка heatmap
→ картинка
```

## Событие: маленькая запись о том, что случилось

Появляется `BookEvent`:

```java
public record BookEvent(
        long timestampNanos,
        Side side,
        int price,
        int volume,
        EventType type
) {
}
```

В нём есть всё, что нужно для игрушечного стакана:

- время события;
- сторона — `BID` или `ASK`;
- цена;
- объём;
- тип события.

Типов пока три:

```java
public enum EventType {
    ADD,
    CANCEL,
    TRADE
}
```

То есть событие может:

- добавить заявку;
- снять заявку;
- исполнить сделку.

Это ещё не настоящие биржевые данные. Но это уже гораздо ближе к нормальной модели, чем “поставим 73 оранжевых попугая в случайную клетку”.

## Генератор событий

`EventGenerator` создаёт одно случайное событие:

> Здесь вставить screenshot кода `EventGenerator`: `artifacts/orderbook/03-event-generator-code.png`.

```java
public BookEvent nextEvent() {
    Side side = random.nextBoolean() ? Side.BID : Side.ASK;

    int distanceFromMid = random.nextInt(50);
    int price = side == Side.BID
            ? MID_PRICE - distanceFromMid
            : MID_PRICE + distanceFromMid;

    EventType type = switch (random.nextInt(3)) {
        case 0 -> EventType.ADD;
        case 1 -> EventType.CANCEL;
        default -> EventType.TRADE;
    };

    int volume = 1 + random.nextInt(100);

    return new BookEvent(
            System.nanoTime(),
            side,
            price,
            volume,
            type
    );
}
```

Смысл простой:

- случайно выбираем сторону;
- если это `BID`, цена ниже середины;
- если это `ASK`, цена выше середины;
- случайно выбираем тип события;
- случайно выбираем объём.

Центр рынка:

```java
MID_PRICE = 1000
```

Диапазон:

```java
random.nextInt(50)
```

То есть bids появляются ниже `1000`, asks — выше `1000`, в пределах примерно 50 уровней от середины.

Уже лучше. Теперь у данных есть хоть какая-то форма. Не глубокая финансовая мудрость, но хотя бы не полный пиксельный суп.

## OrderBook: две стороны стакана

`OrderBook` хранит объёмы отдельно:

> Здесь вставить screenshot кода `OrderBook`: `artifacts/orderbook/02-orderbook-code.png`.

```java
private final int[] bidVolumes = new int[MarketConfig.PRICE_LEVELS];
private final int[] askVolumes = new int[MarketConfig.PRICE_LEVELS];
```

То есть на каждом ценовом уровне может быть:

- bid volume;
- ask volume.

Когда приходит событие, цена переводится в индекс массива:

```java
int priceLevel = MarketConfig.priceToLevel(event.price());
```

В `MarketConfig` это выглядит так:

```java
public static final int PRICE_LEVELS = 200;
public static final int MID_PRICE = 1000;
public static final int MIN_PRICE = MID_PRICE - PRICE_LEVELS / 2;

public static int priceToLevel(int price) {
    return price - MIN_PRICE;
}
```

Если `MID_PRICE = 1000`, а уровней `200`, то:

```text
MIN_PRICE = 1000 - 100 = 900
```

Значит:

```text
price 900  → level 0
price 1000 → level 100
price 1099 → level 199
```

Вот этот перевод цены в индекс массива — маленькая, но важная вещь. Массив не понимает “цену 1000”. Массив понимает “индекс 100”.

## Как событие меняет стакан

Основная логика:

```java
int[] sideVolumes = event.side() == Side.BID
        ? bidVolumes
        : askVolumes;

switch (event.type()) {
    case ADD -> sideVolumes[priceLevel] += event.volume();
    case CANCEL, TRADE -> sideVolumes[priceLevel] =
            Math.max(0, sideVolumes[priceLevel] - event.volume());
}
```

Если пришёл `ADD`, объём увеличивается.

Если пришёл `CANCEL` или `TRADE`, объём уменьшается.

`Math.max(0, ...)` нужен, чтобы объём не ушёл в минус. Потому что отрицательная ликвидность — это уже не рынок, а бухгалтерия после очень плохого понедельника.

## Как OrderBook превращается в heatmap

Каждый tick теперь выглядит так:

```java
for (int i = 0; i < MarketConfig.EVENTS_PER_TICK; i++) {
    BookEvent event = eventGenerator.nextEvent();
    orderBook.apply(event);
}

for (int priceLevel = 0; priceLevel < MarketConfig.PRICE_LEVELS; priceLevel++) {
    heatmap[priceLevel][MarketConfig.TIME_BUCKETS - 1] =
            orderBook.totalVolumeAt(priceLevel);
}
```

То есть:

1. генерируем пачку событий;
2. применяем их к `OrderBook`;
3. берём текущее состояние стакана;
4. записываем его в последнюю колонку heatmap.

Количество событий:

```java
public static final int EVENTS_PER_TICK = 1000;
```

А таймер теперь:

```java
new Timer(16, ...)
```

То есть код просит примерно 60 обновлений в секунду.

Просит — важное слово. Swing не подписывал контракт кровью, что реально будет успевать идеально каждые 16 мс.

## Best bid и best ask

Теперь можно посчитать лучшие цены:

```java
public int bestBidPrice() {
    for (int i = bidVolumes.length - 1; i >= 0; i--) {
        if (bidVolumes[i] > 0) {
            return MarketConfig.levelToPrice(i);
        }
    }
    return -1;
}
```

Для bid ищем сверху вниз: самая высокая цена покупки.

```java
public int bestAskPrice() {
    for (int i = 0; i < askVolumes.length; i++) {
        if (askVolumes[i] > 0) {
            return MarketConfig.levelToPrice(i);
        }
    }
    return -1;
}
```

Для ask ищем снизу вверх: самая низкая цена продажи.

После этого в `HeatmapPanel` появляется история:

```java
private final int[] bestBidHistory = new int[MarketConfig.TIME_BUCKETS];
private final int[] bestAskHistory = new int[MarketConfig.TIME_BUCKETS];
```

Она сдвигается так же, как heatmap:

```java
System.arraycopy(bestBidHistory, 1, bestBidHistory, 0, MarketConfig.TIME_BUCKETS - 1);
System.arraycopy(bestAskHistory, 1, bestAskHistory, 0, MarketConfig.TIME_BUCKETS - 1);

bestBidHistory[MarketConfig.TIME_BUCKETS - 1] = orderBook.bestBidPrice();
bestAskHistory[MarketConfig.TIME_BUCKETS - 1] = orderBook.bestAskPrice();
```

И потом рисуется линиями:

```java
drawPriceHistory(g, bestBidHistory, Color.BLUE, cellW, cellH);
drawPriceHistory(g, bestAskHistory, Color.RED, cellW, cellH);
```

Теперь на heatmap появляются две кривые:

- синяя — best bid;
- красная — best ask.

И вот это уже визуально меняет историю.

Раньше было:

```text
смотри, оранжевое шевелится
```

Теперь:

```text
смотри, у стакана есть границы
```

## Цена этого шага

На каждом tick теперь добавилось:

```text
1000 генераций BookEvent
1000 применений к OrderBook
200 чтений totalVolumeAt(...)
2 сдвига истории bestBid/bestAsk по 799 int
2 поиска best bid / best ask
```

Плюс старое никуда не исчезло:

```text
200 × 799 = 159 800 копирований int для heatmap
200 очисток последней колонки
полный проход 200 × 800 при paintComponent
```

А частота стала выше:

```text
100 ms → 16 ms
примерно 10 tick/sec → примерно 60 tick/sec
```

Если грубо оценить только сдвиг heatmap:

```text
159 800 × 60 = 9 588 000 копирований int / сек
```

Плюс:

```text
1000 × 60 = 60 000 synthetic events / сек
```

Это всё ещё не benchmark. Это бухгалтерия намерений: сколько работы мы попросили выполнить.

И вот теперь становится понятно, почему дальше без измерений лучше не спорить. Можно сколько угодно подозревать `System.arraycopy`, `new Color`, `fillRect`, генератор событий или EDT. Но подозрения — это не profiling. Это просто разработчик смотрит на код и щурится.

## Итог

Третья версия всё ещё очень простая.

Но смысл данных уже другой:

```text
было:
random volume → heatmap

стало:
BookEvent → OrderBook → snapshot объёмов → heatmap
```

И визуально появилась новая информация:

- best bid;
- best ask;
- spread;
- примерная нагрузка events/sec.

Это уже похоже не просто на картинку, а на маленький симулятор рыночного состояния.

В следующей части я хочу сделать скучную, но важную вещь: добавить измерения. Потому что теперь у нас есть несколько подозреваемых, и пора перестать выбирать виновного по выражению лица.

Код проекта:

https://github.com/IavnFGV/swing-heat-map
