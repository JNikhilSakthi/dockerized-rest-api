-- Seed data for local/demo runs. Uses INSERT IGNORE so it is safe to re-run on every
-- application startup (spring.sql.init.mode=always) without producing duplicate rows,
-- relying on the unique constraints defined on categories.name and products.sku.

INSERT IGNORE INTO categories (name, description)
VALUES
    ('Electronics', 'Gadgets, devices and accessories'),
    ('Books', 'Fiction, non-fiction and technical books'),
    ('Home & Kitchen', 'Appliances and household essentials');

INSERT IGNORE INTO products (name, sku, description, price, quantity, status, category_id, created_at, updated_at)
SELECT 'Wireless Mouse', 'ELEC-001', 'Ergonomic 2.4GHz wireless mouse', 19.99, 150, 'ACTIVE',
       c.id, NOW(), NOW()
FROM categories c WHERE c.name = 'Electronics';

INSERT IGNORE INTO products (name, sku, description, price, quantity, status, category_id, created_at, updated_at)
SELECT 'USB-C Hub', 'ELEC-002', '7-in-1 USB-C hub with HDMI and card reader', 34.50, 80, 'ACTIVE',
       c.id, NOW(), NOW()
FROM categories c WHERE c.name = 'Electronics';

INSERT IGNORE INTO products (name, sku, description, price, quantity, status, category_id, created_at, updated_at)
SELECT 'Clean Code', 'BOOK-001', 'A Handbook of Agile Software Craftsmanship', 32.00, 40, 'ACTIVE',
       c.id, NOW(), NOW()
FROM categories c WHERE c.name = 'Books';

INSERT IGNORE INTO products (name, sku, description, price, quantity, status, category_id, created_at, updated_at)
SELECT 'French Press', 'HOME-001', '1L stainless steel coffee press', 27.75, 60, 'ACTIVE',
       c.id, NOW(), NOW()
FROM categories c WHERE c.name = 'Home & Kitchen';
