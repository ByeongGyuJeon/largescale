package com.largescale;

import com.largescale.config.AppConfig;
import com.largescale.csv.EventCsvReader;
import com.largescale.loader.BatchLoader;
import java.nio.file.Path;
import java.util.Locale;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help")) {
            System.out.println("Usage: java -jar target/largescale-0.1.0-SNAPSHOT.jar <validate|load> [config-file]");
            return;
        }
        try {
            if (args.length > 2 || !(args[0].equals("validate") || args[0].equals("load")))
                throw new IllegalArgumentException("Command must be validate or load; multithread mode is future work");
            var config = AppConfig.load(Path.of(args.length == 2 ? args[1] : "config/application.properties"));
            if (args[0].equals("validate")) {
                long count = new EventCsvReader().read(config.csvPath(), config.delimiter(), (row, number) -> {});
                System.out.println("CSV validation passed; rows=" + count + " (DB untouched)");
            } else {
                var result = new BatchLoader().load(config);
                System.out.printf(Locale.ROOT, "mode=batch batchSize=%d committed=%d elapsedSeconds=%.3f rowsPerSecond=%.1f%n",
                        config.batchSize(), result.committedRows(), result.elapsedSeconds(), result.rowsPerSecond());
            }
        } catch (Exception e) {
            // Report exception classes and our messages only; driver messages may contain input values.
            System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e.getCause() != null) System.err.println("Cause: " + e.getCause().getClass().getSimpleName()
                    + (e.getCause() instanceof java.io.IOException ? ": " + e.getCause().getMessage() : ""));
            System.exit(1);
        }
    }
}
