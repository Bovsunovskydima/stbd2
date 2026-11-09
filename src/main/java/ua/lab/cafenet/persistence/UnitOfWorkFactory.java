package ua.lab.cafenet.persistence;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class UnitOfWorkFactory {

    private final DataSource dataSource;

    public UnitOfWorkFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UnitOfWork open() {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new StorageException("Немає зєднання з базою", e);
        }
        return new JdbcUnitOfWork(connection);
    }

    public <T> T inTransaction(Function<UnitOfWork, T> body) {
        try (UnitOfWork work = open()) {
            try {
                T result = body.apply(work);
                work.commit();
                return result;
            } catch (RuntimeException e) {
                work.rollback();
                throw e;
            }
        }
    }

    public void run(Consumer<UnitOfWork> body) {
        inTransaction(work -> {
            body.accept(work);
            return null;
        });
    }
}
