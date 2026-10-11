.PHONY: help dev-up dev-infra dev-down dev-restart dev-clean dev-logs test build k3d-up k3d-down k3d-deploy inject-secrets

help:
	@echo "Доступные команды платформы e-commerce:"
	@echo " make dev-up		- Собрать JAR и запустить весь проект"
	@echo " make dev-infra		- Запустить только инфраструктуру (Postgres, Mongo, Redis, Kafka)"
	@echo " make dev-down		- Остановить весь проект"
	@echo " make dev-clean		- Остановить проект и очистить тома (Volumes)"
	@echo " make dev-logs		- Смотреть логи всех контейнеров в реальном времени"
	@echo " make build		- Собрать все Java-модули через Gradle"
	@echo " make test		- Запустить все тесты через Gradle"
	@echo " make k3d-up		- Создать локальный Kubernetes-кластер в k3d"
	@echo " make k3d-down		- Удалить локальный Kubernetes-кластер в k3d"
	@echo " make inject-secrets	- Внедрить dev-секреты в k3d кластер"


dev-up:
	@echo "Сборка JAR-файлов сервисов через Gradle..."
	./gradlew bootJar -x test
	@echo "Запуск всего проекта с ожиданием готовности (healthcheck)..."
	docker compose --env-file .secrets/dev/.env up -d --build --wait
	@echo "Все сервисы успешно запущены и готовы к работе"
	docker compose --env-file .secrets/dev/.env ps

dev-infra:
	@echo "Запуск только инфраструктуру (Postgres, Mongo, Redis, Kafka) с ожиданием готовности..."
	docker compose --env-file .secrets/dev/.env up -d --wait postgres mongo redis redpanda-kafka redpanda-kafka-console
	@echo "Инфраструктура готова!"
	docker compose --env-file .secrets/dev/.env ps

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

k3d-deploy:
	@echo "Деплой сервисов в k3d через Helm..."
	for svc in auth-service user-service product-service api-gateway inventory-service order-service notification-service; do \
    	helm upgrade --install ecommerce-$$svc helm/ecommerce-common -f helm/environments/$$svc.yaml -n ecommerce; \
    done

inject-secrets:
	@echo "Внедрение секретов в k3d из .secrets/dev/.env..."
	kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -
	kubectl create secret generic ecommerce-secrets --from-env-file=.secrets/dev/.env -n ecommerce --dry-run=client -o yaml | kubectl apply -f -
