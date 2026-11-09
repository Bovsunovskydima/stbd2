-- liquibase formatted sql

--changeset bovsunovsky:02-fn-order-total splitStatements:false
CREATE FUNCTION fn_order_total(p_order BIGINT)
    RETURNS NUMERIC
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    v_total NUMERIC;
BEGIN
    SELECT coalesce(sum(quantity * price_at_sale), 0)
    INTO v_total
    FROM sales_lines
    WHERE order_id = p_order;

    RETURN v_total;
END
$$;

--changeset bovsunovsky:02-tg-order-total splitStatements:false
CREATE FUNCTION tg_order_total()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_order BIGINT := coalesce(NEW.order_id, OLD.order_id);
BEGIN
    UPDATE sales_orders
    SET total = fn_order_total(v_order)
    WHERE id = v_order;

    RETURN NULL;
END
$$;

CREATE TRIGGER tg_sales_lines_total
    AFTER INSERT OR UPDATE OR DELETE
    ON sales_lines
    FOR EACH ROW
EXECUTE FUNCTION tg_order_total();

--changeset bovsunovsky:02-views
CREATE VIEW v_orders AS
SELECT o.id,
       o.status,
       o.channel,
       o.total,
       o.created_at,
       o.closed_at,
       o.branch_id,
       b.name                     AS branch,
       b.city                     AS branch_city,
       o.cashier_id,
       s.full_name                AS cashier,
       o.client_id,
       c.full_name                AS client,
       c.phone                    AS client_phone,
       (SELECT count(*) FROM sales_lines l WHERE l.order_id = o.id) AS lines_count
FROM sales_orders o
         JOIN branches b ON b.id = o.branch_id
         JOIN staff s ON s.id = o.cashier_id
         LEFT JOIN clients c ON c.id = o.client_id;

CREATE VIEW v_order_lines AS
SELECT l.id   AS line_id,
       l.order_id,
       l.menu_item_id,
       m.title,
       ms.name AS section,
       l.quantity,
       l.price_at_sale,
       l.quantity * l.price_at_sale AS line_total
FROM sales_lines l
         JOIN menu_items m ON m.id = l.menu_item_id
         JOIN menu_sections ms ON ms.id = m.section_id;

--changeset bovsunovsky:02-sp-order-open splitStatements:false
CREATE PROCEDURE sp_order_open(p_branch INT, p_cashier BIGINT, p_client BIGINT, p_channel TEXT,
                               INOUT p_id BIGINT)
    LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO sales_orders (branch_id, cashier_id, client_id, channel)
    VALUES (p_branch, p_cashier, p_client, p_channel)
    RETURNING id INTO p_id;
END
$$;

--changeset bovsunovsky:02-sp-order-add-line splitStatements:false
CREATE PROCEDURE sp_order_add_line(p_order BIGINT, p_item BIGINT, p_qty INT, INOUT p_id BIGINT)
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_price NUMERIC;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sales_orders WHERE id = p_order AND status = 'NEW') THEN
        RAISE EXCEPTION 'Замовлення % не відкрите', p_order;
    END IF;

    SELECT price INTO v_price FROM menu_items WHERE id = p_item;
    IF v_price IS NULL THEN
        RAISE EXCEPTION 'Позицію меню % не знайдено', p_item;
    END IF;

    INSERT INTO sales_lines (order_id, menu_item_id, quantity, price_at_sale)
    VALUES (p_order, p_item, p_qty, v_price)
    RETURNING id INTO p_id;
END
$$;

--changeset bovsunovsky:02-sp-order-close splitStatements:false
CREATE PROCEDURE sp_order_close(p_order BIGINT)
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_total NUMERIC;
BEGIN
    SELECT total INTO v_total FROM sales_orders WHERE id = p_order AND status = 'NEW';
    IF v_total IS NULL THEN
        RAISE EXCEPTION 'Замовлення % не відкрите', p_order;
    END IF;

    IF v_total <= 0 THEN
        RAISE EXCEPTION 'У замовленні % немає позицій', p_order;
    END IF;

    UPDATE sales_orders
    SET status    = 'CLOSED',
        closed_at = localtimestamp
    WHERE id = p_order;
END
$$;
