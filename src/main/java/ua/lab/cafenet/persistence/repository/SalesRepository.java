package ua.lab.cafenet.persistence.repository;

import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.persistence.StatementRunner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class SalesRepository {

    private static final String ORDER_COLUMNS = """
            id, status, channel, total, created_at, closed_at,
            branch_id, branch, branch_city, cashier_id, cashier,
            client_id, client, client_phone, lines_count
            """;

    private final StatementRunner runner;

    public SalesRepository(StatementRunner runner) {
        this.runner = runner;
    }

    public Optional<Rows.Order> find(long id) {
        return runner.single("SELECT " + ORDER_COLUMNS + " FROM v_orders WHERE id = CAST(? AS BIGINT)",
                SalesRepository::order, id);
    }

    public List<Rows.OrderLine> lines(long orderId) {
        return runner.list("""
                SELECT line_id, order_id, menu_item_id, title, section, quantity, price_at_sale, line_total
                FROM v_order_lines
                WHERE order_id = CAST(? AS BIGINT)
                ORDER BY line_id
                """, SalesRepository::line, orderId);
    }

    public long open(int branchId, long cashierId, Long clientId, String channel) {
        return runner.execId("""
                CALL sp_order_open(CAST(? AS INT), CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS TEXT), NULL)
                """, branchId, cashierId, clientId, channel);
    }

    public long addLine(long orderId, long menuItemId, int quantity) {
        return runner.execId("""
                CALL sp_order_add_line(CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS INT), NULL)
                """, orderId, menuItemId, quantity);
    }

    public void close(long orderId) {
        runner.exec("CALL sp_order_close(CAST(? AS BIGINT))", orderId);
    }

    private static Rows.Order order(ResultSet rs) throws SQLException {
        return new Rows.Order(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getString("channel"),
                rs.getBigDecimal("total"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("closed_at", LocalDateTime.class),
                rs.getInt("branch_id"),
                rs.getString("branch"),
                rs.getString("branch_city"),
                rs.getLong("cashier_id"),
                rs.getString("cashier"),
                rs.getObject("client_id", Long.class),
                rs.getString("client"),
                rs.getString("client_phone"),
                rs.getInt("lines_count"));
    }

    private static Rows.OrderLine line(ResultSet rs) throws SQLException {
        return new Rows.OrderLine(
                rs.getLong("line_id"),
                rs.getLong("order_id"),
                rs.getLong("menu_item_id"),
                rs.getString("title"),
                rs.getString("section"),
                rs.getInt("quantity"),
                rs.getBigDecimal("price_at_sale"),
                rs.getBigDecimal("line_total"));
    }
}
