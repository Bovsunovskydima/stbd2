-- liquibase formatted sql

--changeset bovsunovsky:01-core-tables

CREATE TABLE branches
(
    id      SERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    city    VARCHAR(80)  NOT NULL,
    address VARCHAR(200) NOT NULL,
    CONSTRAINT uq_branches_name_city UNIQUE (name, city)
);

CREATE TABLE menu_sections
(
    id   SERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT uq_sections_name UNIQUE (name)
);

CREATE TABLE menu_items
(
    id         BIGSERIAL PRIMARY KEY,
    section_id INT            NOT NULL,
    title      VARCHAR(120)   NOT NULL,
    price      NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_menu_section FOREIGN KEY (section_id) REFERENCES menu_sections (id),
    CONSTRAINT uq_menu_title UNIQUE (section_id, title),
    CONSTRAINT ck_menu_price CHECK (price > 0)
);

CREATE TABLE staff
(
    id        BIGSERIAL PRIMARY KEY,
    branch_id INT          NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    position  VARCHAR(60)  NOT NULL,
    CONSTRAINT fk_staff_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
);

CREATE TABLE clients
(
    id        BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    phone     VARCHAR(20)  NOT NULL,
    CONSTRAINT uq_clients_phone UNIQUE (phone)
);

CREATE TABLE sales_orders
(
    id         BIGSERIAL PRIMARY KEY,
    branch_id  INT            NOT NULL,
    client_id  BIGINT,
    cashier_id BIGINT         NOT NULL,
    channel    VARCHAR(10)    NOT NULL,
    status     VARCHAR(12)    NOT NULL DEFAULT 'NEW',
    total      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP      NOT NULL DEFAULT localtimestamp,
    closed_at  TIMESTAMP,
    CONSTRAINT fk_sales_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_sales_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_sales_cashier FOREIGN KEY (cashier_id) REFERENCES staff (id),
    CONSTRAINT ck_sales_channel CHECK (channel IN ('HALL', 'TAKEAWAY', 'DELIVERY')),
    CONSTRAINT ck_sales_status CHECK (status IN ('NEW', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_sales_total CHECK (total >= 0)
);

CREATE TABLE sales_lines
(
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT         NOT NULL,
    menu_item_id  BIGINT         NOT NULL,
    quantity      INT            NOT NULL,
    price_at_sale NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_sales_lines_order FOREIGN KEY (order_id) REFERENCES sales_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_lines_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id),
    CONSTRAINT ck_sales_lines_qty CHECK (quantity > 0),
    CONSTRAINT ck_sales_lines_price CHECK (price_at_sale > 0)
);

--changeset bovsunovsky:01-core-indexes
CREATE INDEX ix_menu_section ON menu_items (section_id);
CREATE INDEX ix_staff_branch ON staff (branch_id);
CREATE INDEX ix_sales_branch ON sales_orders (branch_id);
CREATE INDEX ix_sales_client ON sales_orders (client_id);
CREATE INDEX ix_sales_lines_order ON sales_lines (order_id);
CREATE INDEX ix_sales_lines_item ON sales_lines (menu_item_id);
