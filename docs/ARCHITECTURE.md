# Архитектура SportCLUB

Сайт отдаёт Gateway. Его маршруты `lb://` используют Eureka для поиска бизнес-сервисов. Config Server читает локальные YAML из `config-repo`. Клиенты импортируют конфигурацию при запуске.

| Бизнес-сервис | Порт | Данные |
|---|---:|---|
| `members-service` | 8081 | Профили, роли, абонементы и учебные платежи в `sportclub_members` |
| `club-service` | 8082 | Расписание, тренеры, бронирования и уведомления в `sportclub_club` |
| `workouts-service` | 8083 | Программы, подходы и история в `sportclub_workouts` |
| `wellness-service` | 8084 | Вода, сон и замеры в `sportclub_wellness.public` |
| `nutrition-service` | 8085 | Продукты и порции в `sportclub_wellness.nutrition` |

Config Server работает на 8888, Eureka на 8761, Gateway на 8080. `service-foundation` — общая библиотека безопасности, диагностики и логирования. Всего при локальном запуске поднимаются восемь приложений. Flyway применяет миграции PostgreSQL. Тесты основных сервисов используют отдельные схемы в `sportclub_test`.

Для каждого бизнес-сервиса доступны Actuator и Swagger/OpenAPI. Gateway добавляет `X-Request-ID`, который виден в логах сервисов. Circuit Breaker у каждого маршрута возвращает контролируемый 503 при отказе. Пример проверки и запуска описан в [README](../README.md).
