Write-Host "Запуск локальной инфраструктуры..." -ForegroundColor Cyan
docker compose up -d --wait
docker compose ps