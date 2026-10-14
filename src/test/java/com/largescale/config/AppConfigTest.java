package com.largescale.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    @TempDir Path dir;
    private Path yaml(String csvExtra, String extra) throws Exception {
        Path csv = Files.writeString(dir.resolve("input.csv"), "id,user_id,event,value,payload\n");
        return Files.writeString(dir.resolve("config.yaml"), """
                csv:
                  path: "%s"
                %s
                db:
                  url: "jdbc:mysql://localhost/test"
                %s
                """.formatted(csv.toString().replace(File.separatorChar, '/'), csvExtra, extra));
    }
    @Test void readsNestedYaml() throws Exception {
        var c = AppConfig.load(yaml("  delimiter: comma", "batch:\n  size: 17"));
        assertEquals(17, c.batchSize());assertEquals(',', c.delimiter());
        assertEquals("jdbc:mysql://localhost/test", c.dbUrl());assertTrue(Files.isRegularFile(c.csvPath()));
    }
    @Test void defaultsBatchAndSupportsTab() throws Exception {
        var c = AppConfig.load(yaml("  delimiter: tab", ""));
        assertEquals(1000, c.batchSize());assertEquals('\t', c.delimiter());
    }
    @Test void rejectsFractionalBatchSize() throws Exception {
        var file=yaml("", "batch:\n  size: 1.5");assertThrows(IllegalArgumentException.class, ()->AppConfig.load(file));
    }
    @Test void rejectsNonPositiveBatchSize() throws Exception {
        var file=yaml("", "batch:\n  size: 0");assertThrows(IllegalArgumentException.class, ()->AppConfig.load(file));
    }
    @Test void rejectsDuplicateKeys() throws Exception {
        var file=yaml("", "batch:\n  size: 1\n  size: 2");assertThrows(RuntimeException.class, ()->AppConfig.load(file));
    }
    @Test void rejectsScalarSection() throws Exception {
        var file=yaml("", "batch: invalid");assertThrows(IllegalArgumentException.class, ()->AppConfig.load(file));
    }
}
