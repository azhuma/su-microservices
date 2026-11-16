# Ручные тест-кейсы для занятия 09

Предусловие: WireMock запущен на порту 9090, приложение — с профилем demo на порту 8080 по [инструкции](README.md). В профиле demo используется отдельная БД в памяти; после перезапуска кеш пуст. Курсы синтетические: 1 USD = 500 KZT. Интернет во время занятия не нужен. API-01…07 не обращаются к источнику; API-08 использует WireMock или уже загруженный из него кеш.

Общие шаги для каждого кейса:

1. Открыть соответствующий запрос импортированной коллекции в Postman.
2. Проверить POST, URL `http://localhost:8080/api/v1/conversions`, заголовок `Content-Type: application/json` и тело ниже.
3. Нажать **Send**.
4. Вручную сравнить HTTP-статус, заголовки ответа и JSON с ожиданием; записать фактический результат.

Для всех успешных ответов: `Content-Type: application/json`, ровно семь полей from, to, amount, rate, convertedAmount, rateDate, fetchedOn. Валюты — строки, amount/rate/convertedAmount — JSON-числа; даты — строки YYYY-MM-DD либо null для одинаковых валют. Конечные нули в отображении чисел не влияют на сравнение значений.
Для ошибок не сравнивать точный текст detail: он диагностический и может изменяться.

## API-01. Одинаковые валюты и округление

```json
{
  "from": "USD",
  "to": "USD",
  "amount": 12.345
}
```

**Ожидание:** HTTP 200. from=USD, to=USD, amount=12.345, rate=1, convertedAmount=12.35; обе даты null.

## API-02. Регистр, пробелы и цифровой код

```json
{
  "from": " usd ",
  "to": "840",
  "amount": 12.345
}
```

**Ожидание:** HTTP 200. from=USD, to=USD, rate=1, convertedAmount=12.35; обе даты null. Результат совпадает с API-01.

## API-03. Нулевая сумма

```json
{
  "from": "USD",
  "to": "USD",
  "amount": 0
}
```

**Ожидание:** HTTP 200. rate=1, convertedAmount=0; обе даты null. Ноль разрешён контрактом.

## API-04. Отсутствует обязательное поле

```json
{
  "from": "USD",
  "to": "USD"
}
```

**Ожидание:** HTTP 400. Content-Type: application/problem+json; status=400, code=INVALID_REQUEST, непустой detail; convertedAmount отсутствует.

## API-05. Отрицательная сумма

```json
{
  "from": "USD",
  "to": "USD",
  "amount": -0.01
}
```

**Ожидание:** HTTP 400. Content-Type: application/problem+json; status=400, code=INVALID_REQUEST, непустой detail; convertedAmount отсутствует.

## API-06. Неизвестная валюта

```json
{
  "from": "ZZZ",
  "to": "USD",
  "amount": 1
}
```

**Ожидание:** HTTP 400. Content-Type: application/problem+json; status=400, code=INVALID_CURRENCY, непустой detail; convertedAmount отсутствует.

## API-07. Повреждённый JSON

```json
{"from":"USD",
```

**Ожидание:** HTTP 400. Content-Type: application/problem+json; status=400, code=INVALID_REQUEST, непустой detail; convertedAmount отсутствует.

## API-08. Конвертация по курсу WireMock

```json
{
  "from": "USD",
  "to": "KZT",
  "amount": 100
}
```

**Ожидание:** HTTP 200. from=USD, to=KZT, amount=100, rate=500, convertedAmount=50000, rateDate=2026-01-01, fetchedOn — сегодня в Asia/Almaty.

Дата публикации зафиксирована в моке: 1 января 2026 года. Приложение допускает более раннюю дату публикации; свежесть кеша определяется fetchedOn, датой загрузки. На этом наборе расчёт точен: 100 × 500 = 50000, без зависимости от меняющихся реальных курсов.

Если получен 503 PROVIDER_UNAVAILABLE, проверьте WireMock на 9090 и профиль demo. Это не Pass позитивного кейса. Если ответ содержит другой курс, убедитесь, что приложение запущено с профилем demo; перезапустите его для очистки учебного кеша, не трогая файловую БД.

## Лист результатов

Записать дату, версию приложения/commit и URL. Затем заполнить строки вручную; Pass означает совпадение фактического и ожидаемого результатов, в том числе ожидаемой ошибки 400.

| ID | Ожидаемый HTTP | Фактический HTTP / важные поля | Итог Pass / Fail / Blocked |
| --- | --- | --- | --- |
| API-01 | 200 | | |
| API-02 | 200 | | |
| API-03 | 200 | | |
| API-04 | 400 | | |
| API-05 | 400 | | |
| API-06 | 400 | | |
| API-07 | 400 | | |
| API-08 | 200 | | |

Для Fail приложить запрос, ответ, ожидаемый результат и шаги воспроизведения.
