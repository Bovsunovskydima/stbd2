package ua.lab.cafenet.persistence;

import ua.lab.cafenet.persistence.repository.ReviewRepository;
import ua.lab.cafenet.persistence.repository.SalesRepository;

public interface UnitOfWork extends AutoCloseable {

    SalesRepository sales();

    ReviewRepository reviews();

    void commit();

    void rollback();

    @Override
    void close();
}
