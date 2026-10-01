rootProject.name = "e-commerse-project"

// Модуль с общими файлами микросервисов
include("common-lib")

// Сами сервисы
include("api-gateway")
include("auth-service")
include("user-service")
include("product-service")
include("inventory-service")
include("order-service")
include("notification-service")
