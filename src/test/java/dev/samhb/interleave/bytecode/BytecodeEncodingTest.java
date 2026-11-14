/** Independent decoding of the versioned canonical-state framing and an explicit initialized fixture. */
package dev.samhb.interleave.bytecode;

import java.io.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeEncodingTest {
    /** Literal schema expectations ensure even constant tags/counts cannot be omitted without detection.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture/decoding failures */
    @Test void initializedStateHasLosslessLengthDelimitedSchema(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { public int value=7; public Object lock=new Object(); public static final int CONSTANT=3;
              public Fixture() {} @Actor(id=0) public void actor(){value++;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        try(var in=new DataInputStream(new ByteArrayInputStream(program.initialState().canonicalBytes()))) {
            assertEquals(1,in.readInt()); assertEquals(3,in.readInt());
            reference(in,-2,0); text(in,"java/lang/Class"); objectHeader(in); assertEquals(0,in.readInt());
            reference(in,-1,0); text(in,"Fixture"); objectHeader(in); assertEquals(2,in.readInt());
            field(in,"lock","Ljava/lang/Object;"); reference(in,-1,1);
            field(in,"value","I"); integer(in,7);
            reference(in,-1,1); text(in,"java/lang/Object"); objectHeader(in); assertEquals(0,in.readInt());
            assertEquals(1,in.readInt()); field(in,"CONSTANT","I"); integer(in,3);
            assertEquals(2,in.readInt());
            assertEquals(-1,in.readInt()); assertEquals(6,in.readInt()); // setup TERMINATED
            assertEquals(13,in.readInt()); assertEquals(2,in.readInt()); assertEquals(6,in.readInt()); assertEquals(2L,in.readLong());
            assertFalse(in.readBoolean()); location(in,"<init>",21); assertEquals(0,in.readUnsignedByte()); assertFalse(in.readBoolean()); assertEquals(0,in.readInt());
            assertEquals(0,in.readInt()); assertEquals(0,in.readInt()); // actor RUNNING
            assertEquals(0,in.readInt()); assertEquals(0,in.readInt()); assertEquals(0,in.readInt()); assertEquals(0L,in.readLong());
            assertFalse(in.readBoolean()); assertFalse(in.readBoolean()); assertEquals(0,in.readUnsignedByte()); assertFalse(in.readBoolean());
            assertEquals(1,in.readInt()); location(in,"actor",0); assertTrue(in.readBoolean()); assertEquals(0,in.readUnsignedByte());
            assertEquals(1,in.readInt()); assertEquals(0,in.readInt()); reference(in,-1,0); assertEquals(0,in.readInt()); assertEquals(-1,in.read());
        }
    }


    /** Pending failure/cutoff payloads encode each distinct future-relevant location and resource component.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void pendingFaultAndCutoffComponentsAreLossless(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture {public Fixture(){} @Actor(id=0) public void actor(){} }");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var location=new BytecodeLocation("Fixture","actor","()V",0);
        var locations=java.util.List.of(location,new BytecodeLocation("Other","actor","()V",0),
            new BytecodeLocation("Fixture","other","()V",0),new BytecodeLocation("Fixture","actor","(I)V",0),
            new BytecodeLocation("Fixture","actor","()V",1));
        var keys=new java.util.HashSet<String>();
        for(var position:locations) {
            var state=program.initialState();var execution=state.executions.get(0);execution.phase=BytecodeState.Phase.EXCEPTION;
            execution.fault=new BytecodeState.Fault("java/lang/ArithmeticException",position,java.util.List.of(position));
            assertTrue(keys.add(java.util.Base64.getEncoder().encodeToString(state.canonicalBytes())));
            state.frame(0).stack.add(new BytecodeState.Thrown(execution.fault));
            assertTrue(keys.add(java.util.Base64.getEncoder().encodeToString(state.canonicalBytes())));
        }
        for(var fault:java.util.List.of(
            new BytecodeState.Fault("java/lang/NullPointerException",location,java.util.List.of(location)),
            new BytecodeState.Fault("java/lang/ArithmeticException",location,java.util.List.of()),
            new BytecodeState.Fault("java/lang/ArithmeticException",location,java.util.List.of(location,locations.get(1))))) {
            var state=program.initialState();state.executions.get(0).phase=BytecodeState.Phase.EXCEPTION;state.executions.get(0).fault=fault;
            assertTrue(keys.add(java.util.Base64.getEncoder().encodeToString(state.canonicalBytes())));
        }
        for(var cutoff:java.util.List.of(
            new BytecodeState.Cutoff("INSTRUCTION_FUEL",location,256,1,0),
            new BytecodeState.Cutoff("CALL_DEPTH",location,256,1,0),
            new BytecodeState.Cutoff("INSTRUCTION_FUEL",locations.get(1),256,1,0),
            new BytecodeState.Cutoff("INSTRUCTION_FUEL",location,255,1,0),
            new BytecodeState.Cutoff("INSTRUCTION_FUEL",location,256,2,0),
            new BytecodeState.Cutoff("INSTRUCTION_FUEL",location,256,2,1))) {
            var state=program.initialState();state.executions.get(0).phase=BytecodeState.Phase.CUTOFF_PENDING;state.executions.get(0).cutoff=cutoff;
            assertTrue(keys.add(java.util.Base64.getEncoder().encodeToString(state.canonicalBytes())));
        }
        assertEquals(19,keys.size()); // ten located/token variants, three fault variants, six cutoffs.
        var original=program.initialState();original.executions.get(0).phase=BytecodeState.Phase.EXCEPTION;
        original.executions.get(0).fault=new BytecodeState.Fault("java/lang/ArithmeticException",location,java.util.List.of(location));
        var relocated=original.copy();relocated.executions.get(0).fault=new BytecodeState.Fault(
            "java/lang/ArithmeticException",locations.get(1),java.util.List.of(location));
        assertFalse(java.util.Arrays.equals(original.canonicalBytes(),relocated.canonicalBytes()));
        var restacked=original.copy();restacked.executions.get(0).fault=new BytecodeState.Fault(
            "java/lang/ArithmeticException",location,java.util.List.of(locations.get(1)));
        assertFalse(java.util.Arrays.equals(original.canonicalBytes(),restacked.canonicalBytes()));
    }

    /** Checks the always-present sharing, initialization and monitor fields for a fresh setup object.
     *
     * @param in independent reader
     * @throws IOException for truncated data */
    private static void objectHeader(DataInputStream in) throws IOException {
        assertTrue(in.readBoolean()); assertTrue(in.readBoolean()); assertEquals(-2,in.readInt()); assertEquals(0,in.readInt());
    }
    /** Reads a fixture field's complete identity without the production encoder.
     *
     * @param in independent reader
     * @param name expected name
     * @param descriptor expected descriptor
     * @throws IOException for truncated data */
    private static void field(DataInputStream in,String name,String descriptor) throws IOException { text(in,"Fixture");text(in,name);text(in,descriptor); }
    /** Reads a tagged integer.
     *
     * @param in independent reader
     * @param value literal expected payload
     * @throws IOException for truncated data */
    private static void integer(DataInputStream in,int value) throws IOException { assertEquals(1,in.readUnsignedByte());assertEquals(value,in.readInt()); }
    /** Reads a full-width namespaced reference.
     *
     * @param in independent reader
     * @param owner expected namespace
     * @param sequence expected allocation order
     * @throws IOException for truncated data */
    private static void reference(DataInputStream in,int owner,long sequence) throws IOException {
        assertEquals(2,in.readUnsignedByte());assertEquals(owner,in.readInt());assertEquals(sequence,in.readLong());
    }
    /** Reads a present real method/BCI location.
     *
     * @param in independent reader
     * @param method expected name
     * @param bci expected instruction byte index
     * @throws IOException for truncated data */
    private static void location(DataInputStream in,String method,int bci) throws IOException {
        assertTrue(in.readBoolean());text(in,"Fixture");text(in,method);text(in,"()V");assertEquals(bci,in.readInt());
    }
    /** Reads bounded raw UTF-16 text, including its length boundary.
     *
     * @param in independent reader
     * @param expected literal expected text
     * @throws IOException for truncated data */
    private static void text(DataInputStream in,String expected) throws IOException {
        int length=in.readInt();assertEquals(expected.length(),length); var value=new StringBuilder();
        for(int i=0;i<length;i++) value.append(in.readChar());assertEquals(expected,value.toString());
    }
}
