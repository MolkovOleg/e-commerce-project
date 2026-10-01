.PHONY: help dev-up dev-down dev-restart dev-clean dev-logs test build

help:
	@echo "Доступные команды платформы e-commerce:"
	@echo " make dev-up		- Поднять всю инфраструктуру (Postgres, Mongo, Redis, Kafka)"
	@echo " make dev-down		- Остановить инфраструктуру"
	@echo " make dev-clean		- Остановить инфраструктуру и очистить тома (Volumes)"
	@echo " make dev-logs		- Смотреть логи всех контейнеров в реальном времени"
	@echo " make build		- Собрать все Java-модули через Gradle"
	@echo " make test		- Запустить все тесты через Gradle"

dev-up:
	docker compose up -d
	@echo "Ожидание готовности инфраструктуры..."
	docker compose ps

dev-down:
	@echo "Остановка инфраструктуры"
	docker compose down

dev-clean:
	docker compose down -v
	@echo "Все тома и базы данных очищены"

dev-restart: dev-down dev-up

build:
	./gradlew build

test:
	./gradlew test
