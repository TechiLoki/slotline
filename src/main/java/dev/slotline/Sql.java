package dev.slotline;

import org.postgresql.util.PGobject;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class Sql {
    private Sql() {}
    public static PreparedStatement prepare(Connection connection, String sql, Object... args) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof Instant instant) value = instant.atOffset(ZoneOffset.UTC);
            statement.setObject(i + 1, value);
        }
        return statement;
    }
    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = prepare(c, sql, args)) { return s.executeUpdate(); }
    }
    public static List<Map<String, Object>> rows(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = prepare(c, sql, args); ResultSet rs = statement.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    Object value = rs.getObject(i);
                    if (value instanceof Timestamp t) value = t.toInstant();
                    else if (value instanceof java.sql.Date d) value = d.toLocalDate().toString();
                    else if (value instanceof Time t) value = t.toLocalTime().toString();
                    else if (value instanceof PGobject p && (p.getType().equals("jsonb") || p.getType().equals("json"))) value = Json.read(p.getValue());
                    row.put(meta.getColumnLabel(i), value);
                }
                rows.add(row);
            }
            return rows;
        }
    }
    public static Map<String, Object> one(Connection c, String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = rows(c, sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public static Instant instant(Map<String, Object> row, String key) { return (Instant) row.get(key); }
    public static int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    public static UUID id(Map<String, Object> row, String key) { return (UUID) row.get(key); }
}
