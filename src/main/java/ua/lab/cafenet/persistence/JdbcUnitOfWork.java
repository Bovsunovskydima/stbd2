package ua.lab.cafenet.persistence;

import ua.lab.cafenet.persistence.repository.ReviewRepository;
import ua.lab.cafenet.persistence.repository.SalesRepository;

import java.sql.Connection;
import java.sql.SQLException;

final class JdbcUnitOfWork implements UnitOfWork {

    private final Connection connection;
    private final StatementRunner runner;

    private SalesRepository sales;
    private ReviewRepository reviews;

    private boolean finished;

    JdbcUnitOfWork(Connection connection) {
        this.connection = connection;
        this.runner = new StatementRunner(connection);
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            shut();
            throw new StorageException("Не вдалося відкрити одиницю роботи", e);
        }
    }

    @Override
    public SalesRepository sales() {
        if (sales == null) {
            sales = new SalesRepository(runner);
        }
        return sales;
    }

    @Override
    public ReviewRepository reviews() {
        if (reviews == null) {
            reviews = new ReviewRepository(runner);
        }
        return reviews;
    }

    @Override
    public void commit() {
        try {
            connection.commit();
            finished = true;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося підтвердити транзакцію", e);
        }
    }

    @Override
    public void rollback() {
        try {
            connection.rollback();
            finished = true;
        } catch (SQLException e) {
            throw new StorageException("Не вдалося відкотити транзакцію", e);
        }
    }

    @Override
    public void close() {
        if (!finished) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
        }
        shut();
    }

    private void shut() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
