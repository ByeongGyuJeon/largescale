package com.largescale.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AppConfig(Path csvPath, char delimiter, String dbUrl, int batchSize) {
    public AppConfig {
        if (batchSize <= 0) throw new IllegalArgumentException("batch.size must be positive");
    }
    public static AppConfig load(Path file) throws IOException {
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(file)) { p.load(reader); }
        char delimiter = switch (p.getProperty("csv.delimiter", "comma")) {
            case "comma" -> ',';
            case "tab" -> '\t';
            default -> throw new IllegalArgumentException("csv.delimiter must be comma or tab");
        };
        Path csv = Path.of(p.getProperty("csv.path", "data/sample.csv"));
        if (!Files.isRegularFile(csv)) throw new IllegalArgumentException("CSV file not found: " + csv);
        String url = p.getProperty("db.url", "");
        if (!url.startsWith("jdbc:mysql:")) throw new IllegalArgumentException("db.url must be a MySQL JDBC URL");
        int batchSize = Integer.parseInt(p.getProperty("batch.size", "1000"));
        return new AppConfig(csv, delimiter, url, batchSize);
    }
}
