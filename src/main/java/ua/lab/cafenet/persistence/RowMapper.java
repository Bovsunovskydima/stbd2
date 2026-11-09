package ua.lab.cafenet.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface RowMapper<T> {

    T read(ResultSet rs) throws SQLException;
}
