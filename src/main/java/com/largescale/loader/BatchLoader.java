package com.largescale.loader;

import com.largescale.config.AppConfig;
import com.largescale.csv.EventCsvReader;
import com.largescale.db.ConnectionFactory;
import com.largescale.model.EventRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public final class BatchLoader implements CsvLoader {
    private static final String SQL = "INSERT INTO events (id, user_id, event, value, payload) VALUES (?, ?, ?, ?, ?)";
    @FunctionalInterface interface ConnectionProvider { Connection open(String url) throws SQLException; }
    private final ConnectionProvider connections;
    public BatchLoader() { this(url -> new ConnectionFactory(url).open()); }
    BatchLoader(ConnectionProvider connections) { this.connections = connections; }

    @Override public LoadResult load(AppConfig config) throws Exception {
        // Connection setup is excluded; streaming read, validation, batch INSERT and commit are included.
        try (var connection = connections.open(config.dbUrl())) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(SQL)) {
                long[] committed = {0};
                int[] pending = {0};
                long started = System.nanoTime();
                try {
                    new EventCsvReader().read(config.csvPath(), config.delimiter(), (row, number) -> {
                        bind(statement, row);
                        statement.addBatch();
                        pending[0]++;
                        if (pending[0] == config.batchSize()) {
                            flush(connection, statement, pending[0]);
                            committed[0] += pending[0];
                            pending[0] = 0;
                        }
                    });
                    if (pending[0] > 0) {
                        flush(connection, statement, pending[0]);
                        committed[0] += pending[0];
                    }
                } catch (Exception failure) {
                    try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                    throw new IllegalStateException("Batch load failed; confirmed committed rows=" + committed[0]
                            + ". Current transaction rollback attempted. A connection loss during commit can leave the batch outcome unknown; verify DB before rerunning.", failure);
                }
                return new LoadResult(committed[0], (System.nanoTime() - started) / 1_000_000_000.0);
            }
        }
    }

    private static void bind(PreparedStatement statement, EventRow row) throws SQLException {
        statement.setLong(1, row.id());
        statement.setLong(2, row.userId());
        statement.setString(3, row.event());
        if (row.value() == null) statement.setNull(4, Types.DECIMAL); else statement.setBigDecimal(4, row.value());
        if (row.payload() == null) statement.setNull(5, Types.LONGVARCHAR); else statement.setString(5, row.payload());
    }

    private static void flush(Connection connection, PreparedStatement statement, int expected) throws SQLException {
        int[] counts = statement.executeBatch();
        if (counts.length != expected) throw new SQLException("Unexpected batch result length");
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) throw new SQLException("Unexpected batch update count");
        }
        statement.clearBatch();
        connection.commit();
    }
}
