CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    cancel_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_total_amount_non_negative CHECK (total_amount >= 0)
);

-- Составной индекс со встроенной сортировкой при кейсах просмотра заказов пользователями
CREATE INDEX idx_orders_user_id_created_at ON orders(user_id, created_at DESC);
-- Индекс по статусам заказов, которые встречаются реже (NEW, RESERVED, PAID, SHIPPED)
CREATE INDEX idx_orders_active_status ON orders(status) WHERE status NOT IN ('COMPLETED', 'CANCELLED');

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    stock_id VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price_non_negative CHECK (price >= 0)
);

-- Базовый индекс по поиску по order_id
CREATE INDEX idx_order_items_order_id ON order_items(order_id);

CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

-- Частичный индекс для фонового Outbox-воркера
CREATE INDEX idx_outbox_pending_created_at ON outbox_messages(created_at ASC) WHERE status = 'PENDING';