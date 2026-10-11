Write-Host "Остановка инфраструктуры..." -ForegroundColor Yellow
docker compose --env-file .secrets/dev/.env down