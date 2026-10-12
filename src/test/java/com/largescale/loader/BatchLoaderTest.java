package com.largescale.loader;

import com.largescale.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BatchLoaderTest {
    @TempDir Path dir;
    private AppConfig config(int rows, int size, String last) throws Exception {
        var csv = new StringBuilder("id,user_id,event,value,payload\n");
        for (int i=1;i<=rows;i++) csv.append(i).append(",2,e,1,x\n");
        csv.append(last);
        return new AppConfig(Files.writeString(dir.resolve("input.csv"),csv),',',"jdbc:mysql://test/largescale",size);
    }
    @Test void commitsFullAndPartialBatches() throws Exception {
        var db = new FakeDb();var result = new BatchLoader(url->db.connection()).load(config(5,2,""));
        assertEquals(List.of(2,2,1),db.batchSizes);assertEquals(3,db.commits);assertEquals(5,result.committedRows());
        assertFalse(db.autoCommit);assertTrue(db.statementClosed);assertTrue(db.connectionClosed);
    }
    @Test void emptyInputDoesNotExecuteBatch() throws Exception {
        var db=new FakeDb();assertEquals(0,new BatchLoader(url->db.connection()).load(config(0,2,"")).committedRows());
        assertEquals(0,db.commits);assertTrue(db.batchSizes.isEmpty());
    }
    @Test void exactBoundaryDoesNotAddEmptyBatch() throws Exception {
        var db=new FakeDb();new BatchLoader(url->db.connection()).load(config(4,2,""));assertEquals(List.of(2,2),db.batchSizes);
    }
    @Test void parseFailureKeepsPriorCommitsAndRollsBackPending() throws Exception {
        var db=new FakeDb();var e=assertThrows(Exception.class,()->new BatchLoader(url->db.connection()).load(config(3,2,"4,2,e,invalid,x\n")));
        assertTrue(e.getMessage().contains("committed rows=2"));assertEquals(1,db.commits);assertEquals(1,db.rollbacks);
    }
    @Test void batchFailureIsNotCountedAsCommitted() throws Exception {
        var db=new FakeDb();db.failExecution=2;
        var e=assertThrows(Exception.class,()->new BatchLoader(url->db.connection()).load(config(4,2,"")));
        assertTrue(e.getMessage().contains("committed rows=2"));assertEquals(1,db.rollbacks);assertEquals(1,db.commits);
    }
    @Test void commitFailureDoesNotCountUnconfirmedRows() throws Exception {
        var db=new FakeDb();db.failCommit=true;
        var e=assertThrows(Exception.class,()->new BatchLoader(url->db.connection()).load(config(2,2,"")));
        assertTrue(e.getMessage().contains("committed rows=0"));assertEquals(1,db.rollbacks);
    }
    @Test void acceptsSuccessWithoutIndividualCountsAndBindsNulls() throws Exception {
        var db=new FakeDb();db.updateCount=Statement.SUCCESS_NO_INFO;
        var result=new BatchLoader(url->db.connection()).load(config(0,2,"1,2,e,\\N,\\N\n"));
        assertEquals(1,result.committedRows());assertEquals(List.of(4,5),db.nullParameters);
    }
    @Test void rejectsFailedUpdateCount() throws Exception {
        var db=new FakeDb();db.updateCount=Statement.EXECUTE_FAILED;
        assertThrows(Exception.class,()->new BatchLoader(url->db.connection()).load(config(1,2,"")));
        assertEquals(0,db.commits);assertEquals(1,db.rollbacks);
    }
    @Test void rejectsNonPositiveBatchSize() { assertThrows(IllegalArgumentException.class,()->new AppConfig(dir,',',"jdbc:mysql://test",0)); }

    // JDBC test double records transaction boundaries; it does not simulate a MySQL server.
    private static class FakeDb {
        int pending,commits,rollbacks,failExecution,updateCount=1;
        boolean autoCommit=true,failCommit,statementClosed,connectionClosed;
        List<Integer> batchSizes=new ArrayList<>(),nullParameters=new ArrayList<>();
        PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(proxy,method,args)->{
            return switch(method.getName()) {
                case "addBatch" -> {pending++;yield null;}
                case "executeBatch" -> {batchSizes.add(pending);if(batchSizes.size()==failExecution)throw new SQLException("injected execution failure");int[] counts=new int[pending];Arrays.fill(counts,updateCount);yield counts;}
                case "clearBatch" -> {pending=0;yield null;}
                case "setNull" -> {nullParameters.add((Integer)args[0]);yield null;}
                case "setLong","setString","setBigDecimal" -> null;
                case "close" -> {statementClosed=true;yield null;}
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
        Connection connection(){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->switch(method.getName()){
            case "setAutoCommit" -> {autoCommit=(Boolean)args[0];yield null;}
            case "prepareStatement" -> statement;
            case "commit" -> {if(failCommit)throw new SQLException("injected commit failure");commits++;yield null;}
            case "rollback" -> {rollbacks++;yield null;}
            case "close" -> {connectionClosed=true;yield null;}
            default -> throw new UnsupportedOperationException(method.getName());
        });}
    }
}
