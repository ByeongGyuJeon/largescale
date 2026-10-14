package com.largescale.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record AppConfig(Path csvPath, char delimiter, String dbUrl, int batchSize) {
    public AppConfig {
        if (batchSize <= 0) throw new IllegalArgumentException("batch.size must be positive");
    }
    public static AppConfig load(Path file) throws IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try (var reader = Files.newBufferedReader(file)) {
            document = new Yaml(new SafeConstructor(options)).load(reader);
        }
        Map<?, ?> root = mapping(document, "root");
        Map<?, ?> csv = section(root, "csv");
        Map<?, ?> db = section(root, "db");
        Map<?, ?> batch = section(root, "batch");
        char delimiter = switch (text(csv, "delimiter", "comma")) {
            case "comma" -> ',';
            case "tab" -> '\t';
            default -> throw new IllegalArgumentException("csv.delimiter must be comma or tab");
        };
        Path csvPath = Path.of(text(csv, "path", "data/sample.csv"));
        if (!Files.isRegularFile(csvPath)) throw new IllegalArgumentException("CSV file not found: " + csvPath);
        String url = text(db, "url", "");
        if (!url.startsWith("jdbc:mysql:")) throw new IllegalArgumentException("db.url must be a MySQL JDBC URL");
        Object size = batch.containsKey("size") ? batch.get("size") : 1000;
        int batchSize;
        if (size instanceof Integer value) batchSize = value;
        else if (size instanceof String value) batchSize = Integer.parseInt(value);
        else throw new IllegalArgumentException("batch.size must be an integer");
        return new AppConfig(csvPath, delimiter, url, batchSize);
    }
    private static Map<?, ?> mapping(Object value, String name) {
        if (value instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException(name + " must be a YAML mapping");
    }
    private static Map<?, ?> section(Map<?, ?> root, String name) {
        return root.containsKey(name) ? mapping(root.get(name), name) : Map.of();
    }
    private static String text(Map<?, ?> map, String key, String fallback) {
        if (!map.containsKey(key)) return fallback;
        if (map.get(key) instanceof String value) return value;
        throw new IllegalArgumentException(key + " must be a YAML string");
    }
}
