package com.largescale.csv;

import com.largescale.model.EventRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.DuplicateHeaderMode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class EventCsvReader {
    private static final List<String> HEADER = List.of("id", "user_id", "event", "value", "payload");
    @FunctionalInterface public interface RowConsumer { void accept(EventRow row, long recordNumber) throws Exception; }

    public long read(Path path, char delimiter, RowConsumer consumer) throws Exception {
        var format = CSVFormat.RFC4180.builder().setDelimiter(delimiter).setHeader()
                .setSkipHeaderRecord(true).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .setNullString("\\N").get();
        long count = 0;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            if (!parser.getHeaderNames().equals(HEADER)) throw new IOException("Expected header: " + HEADER);
            for (var record : parser) {
                long number = record.getRecordNumber();
                try {
                    if (record.size() != HEADER.size()) throw new IllegalArgumentException("Expected 5 fields");
                    String event = record.get("event");
                    if (event == null || event.isEmpty() || event.codePointCount(0, event.length()) > 100)
                        throw new IllegalArgumentException("event must contain 1..100 characters");
                    String text = record.get("value");
                    BigDecimal value = text == null ? null : new BigDecimal(text).setScale(6, RoundingMode.UNNECESSARY);
                    if (value != null && value.precision() > 20) throw new IllegalArgumentException("value exceeds DECIMAL(20,6)");
                    String payload = record.get("payload");
                    if (payload != null && payload.getBytes(StandardCharsets.UTF_8).length > 65535)
                        throw new IllegalArgumentException("payload exceeds TEXT byte limit");
                    var row = new EventRow(Long.parseLong(record.get("id")), Long.parseLong(record.get("user_id")), event, value, payload);
                    consumer.accept(row, number);
                    count++;
                } catch (Exception e) {
                    throw new IOException("Failed at data record " + number + " (header excluded)", e);
                }
            }
        }
        return count;
    }
}
