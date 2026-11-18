package dev.slotline;

import java.sql.*;
import java.util.concurrent.*;

public final class Database {
    @FunctionalInterface public interface Work<T> { T apply(Connection connection) throws SQLException; }
    private final String url, user, password;
    private final Semaphore permits = new Semaphore(16);
    public Database(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
        try { Class.forName("org.postgresql.Driver"); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }
    public <T> T transaction(Work<T> work) {
        boolean acquired = false;
        try {
            if (!permits.tryAcquire(3, TimeUnit.SECONDS)) throw new Problem(503, "BUSY", "The booking service is busy. Retry shortly.");
            acquired = true;
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                connection.setAutoCommit(false);
                try {
                    T result = work.apply(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Problem(503, "INTERRUPTED", "Request interrupted. Retry with the same request ID.");
        } catch (SQLException e) {
            if ("23P01".equals(e.getSQLState())) throw Problem.conflict();
            if ("23505".equals(e.getSQLState())) throw new Problem(409, "CONFLICT", "A request or booking reference conflicts with existing data.");
            System.err.println("Database operation failed; SQL state=" + e.getSQLState());
            throw new Problem(503, "DATABASE_UNAVAILABLE", "The booking database is unavailable. Retry with the same request ID.");
        } finally { if (acquired) permits.release(); }
    }
}
