# Практика 9: ручное тестирование API с WireMock и Postman

На занятии вручную запускаем WireMock и приложение, затем отправляем восемь запросов по одному кнопкой **Send**. WireMock заменяет внешний источник валют и возвращает фиксированные учебные курсы. Статус, заголовки и тело ответа проверяем вручную по [тест-кейсам](test-cases.md). В коллекции прямые URL, без окружений и JavaScript.

Схема: **Postman → приложение :8080 → WireMock :9090**. Postman проверяет приложение; только приложение обращается к моку за курсами.

## 1. Подготовить WireMock до занятия

Нужны Java 21, Maven, настольный Postman и WireMock Standalone 3.13.2. Python и Docker не нужны. Первую сборку и загрузку jar выполните заранее, пока есть интернет.

Из корня проекта:

```bash
cd /Users/akylbek/IdeaProjects/su/su-microservices
mvn verify
curl --fail --location --output target/wiremock-standalone-3.13.2.jar \
  https://repo.maven.apache.org/maven2/org/wiremock/wiremock-standalone/3.13.2/wiremock-standalone-3.13.2.jar
```

Это разовая ручная загрузка инструмента. Jar находится в игнорируемой папке target и не добавляется в Git или зависимости приложения. После `mvn clean` скачайте его заново.

## 2. Запустить мок вручную

В первом терминале из корня проекта выполните:

```bash
java -jar target/wiremock-standalone-3.13.2.jar \
  --port 9090 --bind-address 127.0.0.1 \
  --root-dir docs/api-testing/wiremock
```

Оставьте терминал открытым. WireMock загружает [правило GET /v6/latest/USD](wiremock/mappings/rates.json) из папки mappings. Проверить мок можно в браузере:

```text
http://localhost:9090/v6/latest/USD
```

Ожидается JSON:

```json
{
  "result": "success",
  "base_code": "USD",
  "time_last_update_unix": 1767225600,
  "rates": {"USD": 1, "KZT": 500, "EUR": 0.8}
}
```

Это синтетические курсы: 1 USD = 500 KZT, 1 EUR = 625 KZT. Timestamp соответствует 1 января 2026 года UTC; дата публикации специально фиксирована. Контракт приложения допускает такую дату, актуальность кеша определяется датой загрузки fetchedOn.

WireMock поддерживает отдельный запуск через jar, параметры порта/каталога и правила в JSON. Источники: [Standalone](https://wiremock.org/docs/standalone/java-jar/), [Stubbing](https://wiremock.org/docs/stubbing/), [загрузка](https://wiremock.org/docs/download-and-installation/).

## 3. Запустить приложение с профилем demo

Во втором терминале:

```bash
cd /Users/akylbek/IdeaProjects/su/su-microservices
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Либо в IntelliJ IDEA откройте конфигурацию запуска `CurrencyApplication` через **Run → Edit Configurations** и добавьте в **Program arguments**:

```text
--spring.profiles.active=demo
```

Выберите JDK 21, рабочий каталог — корень проекта; запустите main. Используйте один способ запуска приложения, чтобы второй экземпляр не занял тот же порт.

Дождитесь `Started CurrencyApplication`. В стартовом логе должен быть активен профиль demo. Он задаёт:

| Настройка | Значение |
| --- | --- |
| API приложения | `http://localhost:8080` |
| Источник курсов | `http://localhost:9090/v6/latest/USD` |
| БД | H2 в памяти `currency_demo`, пустая после перезапуска JVM |

[Профиль demo](../../src/main/resources/application-demo.yml) отделяет учебный кеш от обычной файловой БД `data/currency`. Это позволяет избежать случайного использования ранее загруженных реальных курсов. Профиль действует только при явном включении demo.

Во время занятия интернет не нужен, если зависимости и WireMock уже скачаны. Оба терминала должны продолжать работать.

## 4. Открыть коллекцию в Postman

1. Нажмите **Import**.
2. Выберите только [currency-api.postman_collection.json](currency-api.postman_collection.json).
3. Откройте коллекцию **QA 09 — Currency API — ручная проверка**. Если осталась прежняя версия, удалите её вручную и импортируйте обновлённую.
4. Выберите **No environment**. Настраивать переменные не нужно.
5. Откройте API-01: POST, URL `http://localhost:8080/api/v1/conversions`.
6. В **Body** уже выбрано raw → JSON, в **Headers** указан `Content-Type: application/json`. Токен не нужен.
7. Нажмите **Send** и вручную сравните ответ с ожиданием из описания запроса и тест-кейсов.

Автоматических assertions нет: Test Results может быть пустой. Collection Runner не нужен.

## 5. Показать тесты: 20–25 минут

Начните с контракта: POST принимает from, to и amount, возвращает курс и результат. Успешный расчёт даёт 200; неверный ввод — 400. Ноль допустим. Подробнее — в [спецификации](../specification.md).

| Порядок | Запрос | Что показать |
| --- | --- | --- |
| 1 | API-01: USD → USD, 12.345 | 200; rate=1, convertedAmount=12.35; обе даты null |
| 2 | API-02: `" usd "` → `"840"` | 200; валюты нормализованы в USD, результат тот же |
| 3 | API-03: amount=0 | 200, convertedAmount=0. Нижняя допустимая граница |
| 4 | API-04: нет amount | 400 INVALID_REQUEST. Обязательность поля |
| 5 | API-05: amount=-0.01 | 400 INVALID_REQUEST. Недопустимое значение |
| 6 | API-06: from=ZZZ | 400 INVALID_CURRENCY. Проверка ISO-кода |
| 7 | API-07: повреждённый JSON | 400 INVALID_REQUEST. Синтаксическая ошибка не должна давать 500 |
| 8 | API-08: 100 USD → KZT | 200; rate=500, convertedAmount=50000, rateDate=2026-01-01, fetchedOn — сегодня |

Перед Send предложите студентам назвать ожидаемый HTTP-код и результат. После отправки покажите статус, Headers → Content-Type и Body → JSON. У успеха должны быть from, to, amount, rate, convertedAmount, rateDate, fetchedOn. У ошибки проверьте status, code, непустой detail и отсутствие convertedAmount.

В API-08 ожидается:

```json
{
  "from": "USD",
  "to": "KZT",
  "amount": 100,
  "rate": 500,
  "convertedAmount": 50000,
  "rateDate": "2026-01-01",
  "fetchedOn": "<сегодня в Asia/Almaty>"
}
```

Вместо пояснения fetchedOn будет строка YYYY-MM-DD. JSON может отображать конечные нули, например 500.0000000000 и 50000.00 — значения те же. У API-01…03 обе даты null: одинаковым валютам не нужен источник курсов. У API-04…07 ошибки обнаруживаются до обращения к источнику.

Наличие HTTP 200 само по себе не подтверждает верную сумму. Ожидаемый 400 в негативном кейсе означает Pass, если тело ошибки тоже соответствует контракту.

Дополнительно можно открыть в браузере `http://localhost:9090/__admin/requests`: это журнал запросов WireMock. После API-08 там появится GET /v6/latest/USD. Повторная конвертация использует сегодняшний кеш. Предварительная проверка мока в браузере тоже попадает в журнал, поэтому не считайте все записи вызовами приложения.

## 6. Записать результат и остановить процессы

Заполните [лист результатов](test-cases.md#лист-результатов): ID, фактический HTTP-код, важные поля, Pass/Fail/Blocked. Для Fail сохраните запрос и ответ.

Остановите приложение через Ctrl+C во втором терминале или Stop в IDE, затем WireMock через Ctrl+C в первом терминале. При следующем запуске приложения с demo учебный кеш снова пуст.

Если Postman пишет Connection refused, проверьте запуск приложения на 8080. При 503 PROVIDER_UNAVAILABLE проверьте WireMock на 9090 и профиль demo. При другом курсе в API-08 убедитесь, что активен demo; после изменения мока перезапустите WireMock и приложение для очистки учебного кеша. При 415 проверьте Content-Type и Body → raw → JSON. Занятые порты нужно освободить, остановив прежние экземпляры.
