package com.largescale.loader;

import com.largescale.config.AppConfig;
import com.largescale.csv.EventCsvReader;
import com.largescale.db.ConnectionFactory;
import java.sql.Types;

public final class RowByRowLoader implements CsvLoader {
    private static final String SQL = "INSERT INTO events (id, user_id, event, value, payload) VALUES (?, ?, ?, ?, ?)";
    @Override public LoadResult load(AppConfig config) throws Exception {
        // Connection setup is excluded; CSV read, validation and INSERT are included.
        try (var connection = new ConnectionFactory(config.dbUrl()).open()) {
            connection.setAutoCommit(true);
            try (var statement = connection.prepareStatement(SQL)) {
                long[] committed = {0};
                long started = System.nanoTime();
                try {
                    new EventCsvReader().read(config.csvPath(), config.delimiter(), (row, number) -> {
                        statement.setLong(1, row.id());
                        statement.setLong(2, row.userId());
                        statement.setString(3, row.event());
                        if (row.value() == null) statement.setNull(4, Types.DECIMAL); else statement.setBigDecimal(4, row.value());
                        if (row.payload() == null) statement.setNull(5, Types.LONGVARCHAR); else statement.setString(5, row.payload());
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("Unexpected affected row count");
                        committed[0]++;
                    });
                } catch (Exception e) {
                    throw new IllegalStateException("Load failed; confirmed committed rows=" + committed[0]
                            + ". A connection loss can leave the last INSERT commit unknown; verify DB before rerunning.", e);
                }
                return new LoadResult(committed[0], (System.nanoTime() - started) / 1_000_000_000.0);
            }
        }
    }
}
