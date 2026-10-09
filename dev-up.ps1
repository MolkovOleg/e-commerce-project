Write-Host "Запуск локальной инфраструктуры..." -ForegroundColor Cyan
docker compose --env-file .secrets/dev/.env up -d --build --wait
docker compose ps