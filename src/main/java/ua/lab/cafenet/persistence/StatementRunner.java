package ua.lab.cafenet.persistence;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StatementRunner {

    private final Connection connection;

    StatementRunner(Connection connection) {
        this.connection = connection;
    }

    public <T> List<T> list(String sql, RowMapper<T> mapper, Object... args) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            fill(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.read(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw convert(sql, e);
        }
    }

    public <T> Optional<T> single(String sql, RowMapper<T> mapper, Object... args) {
        List<T> rows = list(sql, mapper, args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public <T> T scalar(String sql, RowMapper<T> mapper, Object... args) {
        return single(sql, mapper, args)
                .orElseThrow(() -> new StorageException("Запит не повернув даних: " + sql));
    }

    public void exec(String sql, Object... args) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            fill(statement, args);
            statement.execute();
        } catch (SQLException e) {
            throw convert(sql, e);
        }
    }

    public long execId(String sql, Object... args) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            fill(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new StorageException("Процедура не повернула ідентифікатор: " + sql);
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw convert(sql, e);
        }
    }

    public Array array(String elementType, Object[] values) {
        try {
            return connection.createArrayOf(elementType, values);
        } catch (SQLException e) {
            throw new StorageException("Не вдалося створити масив " + elementType, e);
        }
    }

    private void fill(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                statement.setNull(i + 1, Types.OTHER);
            } else {
                statement.setObject(i + 1, args[i]);
            }
        }
    }

    private RuntimeException convert(String sql, SQLException e) {
        String state = e.getSQLState();
        if (state != null && (state.startsWith("23") || state.startsWith("P0"))) {
            return new RuleViolationException(firstLine(e), e);
        }
        return new StorageException("Помилка звернення до бази: " + sql, e);
    }

    private String firstLine(SQLException e) {
        String text = e.getMessage();
        if (text == null) {
            return "Порушено обмеження бази даних";
        }
        int cut = text.indexOf('\n');
        String line = cut > 0 ? text.substring(0, cut).trim() : text.trim();
        return line.startsWith("ERROR: ") ? line.substring(7).trim() : line;
    }
}
