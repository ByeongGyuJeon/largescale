package com.largescale.csv;

import com.largescale.model.EventRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class EventCsvReaderTest {
    @TempDir Path dir;
    private Path csv(String content) throws Exception { return Files.writeString(dir.resolve("input.csv"), content); }
    private static final String HEADER = "id,user_id,event,value,payload\n";
    @Test void preservesQuotedPayloadAndNulls() throws Exception {
        var rows = new ArrayList<EventRow>();
        long count = new EventCsvReader().read(csv(HEADER + "1,2,조회,1.25,\"쉼표, 줄바꿈\n따옴표 \"\"예시\"\"\"\n2,2,login,\\N,\\N\n"), ',', (r,n)->rows.add(r));
        assertEquals(2, count);assertEquals("쉼표, 줄바꿈\n따옴표 \"예시\"", rows.get(0).payload());
        assertEquals("1.250000",rows.get(0).value().toPlainString());assertNull(rows.get(1).value());assertNull(rows.get(1).payload());
    }
    @Test void acceptsHeaderOnly() throws Exception { assertEquals(0,new EventCsvReader().read(csv(HEADER),',',(r,n)->fail())); }
    @Test void rejectsWrongHeader() throws Exception { assertThrows(Exception.class,()->new EventCsvReader().read(csv("id,user_id,event,payload,value\n"),',',(r,n)->fail())); }
    @Test void rejectsDecimalRounding() throws Exception { var e=assertThrows(Exception.class,()->new EventCsvReader().read(csv(HEADER+"1,2,e,1.0000001,x\n"),',',(r,n)->fail()));assertTrue(e.getMessage().contains("record 1")); }
    @Test void rejectsMissingField() throws Exception { assertThrows(Exception.class,()->new EventCsvReader().read(csv(HEADER+"1,2,e,1\n"),',',(r,n)->fail())); }
    @Test void supportsTabSeparatedInput() throws Exception { assertEquals(1,new EventCsvReader().read(csv("id\tuser_id\tevent\tvalue\tpayload\n1\t2\te\t0\tx\n"),'\t',(r,n)->assertEquals("x",r.payload()))); }
}
