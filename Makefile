.PHONY: help dev-up dev-down dev-restart dev-clean dev-logs test build k3d-up k3d-down inject-secrets

help:
	@echo "Доступные команды платформы e-commerce:"
	@echo " make dev-up		- Поднять всю инфраструктуру (Postgres, Mongo, Redis, Kafka)"
	@echo " make dev-down		- Остановить инфраструктуру"
	@echo " make dev-clean		- Остановить инфраструктуру и очистить тома (Volumes)"
	@echo " make dev-logs		- Смотреть логи всех контейнеров в реальном времени"
	@echo " make build		- Собрать все Java-модули через Gradle"
	@echo " make test		- Запустить все тесты через Gradle"
	@echo " make k3d-up		- Создать локальный Kubernetes-кластер в k3d"
	@echo " make k3d-down		- Удалить локальный Kubernetes-кластер в k3d"
	@echo " make inject-secrets	- Внедрить dev-секреты в k3d кластер"


dev-up:
	@echo "Сборка JAR-файлов сервисов..."
	./gradlew bootJar -x test
	@echo "Запуск инфраструктуры с ожиданием готовности (healthcheck)..."
	docker compose up -d --wait
	@echo "Все сервисы инфраструктуры успешно запущены и готовы к работе"
	docker compose ps

dev-down:
	@echo "Остановка инфраструктуры..."
	docker compose down

dev-clean:
	docker compose down -v
	@echo "Все тома и базы данных очищены"

dev-restart: dev-down dev-up

dev-logs:
	docker compose logs -f

build:
	./gradlew build

test:
	./gradlew test

k3d-up:
	@echo "Создание локального k3d кластера..."
	k3d cluster create ecommerce-cluster --agents 2 --port 8080:80@loadbalancer --port 8443:443@loadbalancer || true

k3d-down:
	@echo "Удаление k3d кластера..."
	k3d cluster delete ecommerce-cluster

inject-secrets:
	@echo "Внедрение секретов в k3d из .secrets/dev/.env..."
	kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -
	kubectl create secret generic ecommerce-secrets --from-env-file=.secrets/dev/.env -n ecommerce --dry-run=client -o yaml | kubectl apply -f -
