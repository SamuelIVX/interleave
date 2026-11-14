/** Bounded annotation preflight before handing metadata to the JDK's recursive annotation reader. */
package dev.samhb.interleave.bytecode;

import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import static dev.samhb.interleave.bytecode.BytecodeLoader.problem;

/**
 * Checks container lengths and annotation nesting only. The JDK Class-File API remains responsible
 * for actual class parsing, constant-pool validity, annotation interpretation and bytecode verification.
 */
final class BytecodeInput {
    /** Maximum nested annotation/array element values; protocol annotations need at most one level. */
    static final int MAX_ANNOTATION_DEPTH=32;

    /** Prevents construction of the stateless input preflight. */
    private BytecodeInput() {}

    /**
     * Bounds recursion before any annotations are decoded by the JDK.
     *
     * @param bytes already byte-capped class input with validated version header
     *
     * @throws BytecodeLoadException for malformed containers or excess annotation nesting
     */
    static void validate(byte[] bytes) {
        try {
            var in=ByteBuffer.wrap(bytes); skip(in,8);
            String[] names=new String[u2(in)];
            for(int index=1;index<names.length;index++) {
                switch(u1(in)) {
                    case 1 -> {
                        int length=u2(in), offset=in.position()-2;skip(in,length);
                        // Decode modified UTF rather than raw ASCII: noncanonical encodings must not bypass preflight.
                        names[index]=new DataInputStream(new ByteArrayInputStream(bytes,offset,length+2)).readUTF();
                    }
                    case 3,4,9,10,11,12,17,18 -> skip(in,4);
                    case 5,6 -> {skip(in,8);index++;}
                    case 7,8,16,19,20 -> skip(in,2);
                    case 15 -> skip(in,3);
                    default -> throw problem("MALFORMED","Invalid constant-pool tag",null);
                }
            }
            skip(in,6); skip(in,u2(in)*2); members(in,names,false);members(in,names,true);
            attributes(in,names,false);
            if(in.hasRemaining())throw problem("MALFORMED","Trailing class bytes",null);
        } catch(IOException | BufferUnderflowException | IndexOutOfBoundsException failure) {
            throw problem("MALFORMED","Truncated class metadata",null);
        }
    }

    /**
     * Traverses field/method attribute tables without decoding target members.
     *
     * @param in bounded class cursor
     * @param names ASCII attribute names
     * @param codeAllowed whether these are methods
     */
    private static void members(ByteBuffer in,String[] names,boolean codeAllowed) {
        int count=u2(in);
        for(int index=0;index<count;index++){skip(in,6);attributes(in,names,codeAllowed);}
    }

    /**
     * Inspects standard annotation containers; Code permits exactly one nested attribute table.
     *
     * @param in bounded enclosing cursor
     * @param names ASCII attribute names
     * @param codeAllowed method attribute context
     */
    private static void attributes(ByteBuffer in,String[] names,boolean codeAllowed) {
        int count=u2(in);
        for(int index=0;index<count;index++) {
            int name=u2(in), length=in.getInt();
            if(length<0 || length>in.remaining())throw problem("MALFORMED","Invalid attribute length",null);
            var body=in.slice(in.position(),length);skip(in,length);
            String attribute=name<names.length ? names[name] : null;
            if(attribute==null)throw problem("MALFORMED","Invalid attribute name",null);
            switch(attribute) {
                case "RuntimeVisibleAnnotations","RuntimeInvisibleAnnotations" -> annotations(body);
                case "RuntimeVisibleParameterAnnotations","RuntimeInvisibleParameterAnnotations" -> {
                    int parameters=u1(body);for(int parameter=0;parameter<parameters;parameter++)annotations(body);
                }
                case "RuntimeVisibleTypeAnnotations","RuntimeInvisibleTypeAnnotations" -> {
                    int annotations=u2(body);
                    for(int annotation=0;annotation<annotations;annotation++) {
                        typeTarget(body);skip(body,u1(body)*2);annotation(body,0);
                    }
                }
                case "AnnotationDefault" -> value(body,0);
                case "Record" -> throw problem("UNSUPPORTED","Record metadata is outside the initial profile",null);
                case "Code" -> {
                    if(!codeAllowed)throw problem("MALFORMED","Code attribute outside a method",null);
                    skip(body,4);skip(body,body.getInt());skip(body,u2(body)*8);attributes(body,names,false);
                }
                default -> {continue;} // Other attributes are validated by the JDK, not reimplemented here.
            }
            if(body.hasRemaining())throw problem("MALFORMED","Trailing annotation/code metadata",null);
        }
    }

    /**
     * Skips the fixed/variable JVM type-annotation target before its type path and annotation value.
     *
     * @param in bounded type-annotation cursor
     */
    private static void typeTarget(ByteBuffer in) {
        switch(u1(in)) {
            case 0x00,0x01,0x16 -> skip(in,1);
            case 0x10,0x11,0x12,0x17,0x42,0x43,0x44,0x45,0x46 -> skip(in,2);
            case 0x13,0x14,0x15 -> { }
            case 0x40,0x41 -> skip(in,u2(in)*6);
            case 0x47,0x48,0x49,0x4a,0x4b -> skip(in,3);
            default -> throw problem("MALFORMED","Invalid type-annotation target",null);
        }
    }

    /** Reads a flat annotation list with bounded nested values.
     *
     * @param in bounded attribute cursor */
    private static void annotations(ByteBuffer in) {
        int count=u2(in);for(int index=0;index<count;index++)annotation(in,0);
    }

    /** Reads annotation header/element names without resolving their types.
     *
     * @param in bounded cursor
     * @param depth nesting depth */
    private static void annotation(ByteBuffer in,int depth) {
        if(depth>MAX_ANNOTATION_DEPTH)throw problem("LIMIT","Annotation nesting exceeds32",null);
        skip(in,2);int count=u2(in);
        for(int index=0;index<count;index++){skip(in,2);value(in,depth);}
    }

    /** Rejects excess nesting before descending; recursion is bounded by a small fixed profile limit.
     *
     * @param in bounded cursor
     * @param depth nesting depth */
    private static void value(ByteBuffer in,int depth) {
        if(depth>MAX_ANNOTATION_DEPTH)throw problem("LIMIT","Annotation nesting exceeds32",null);
        switch(u1(in)) {
            case 'B','C','D','F','I','J','S','Z','s','c' -> skip(in,2);
            case 'e' -> skip(in,4);
            case '@' -> annotation(in,depth+1);
            case '[' -> {int count=u2(in);for(int index=0;index<count;index++)value(in,depth+1);}
            default -> throw problem("MALFORMED","Invalid annotation value tag",null);
        }
    }

    /** Reads an unsigned byte, rejecting truncation through the bounded cursor.
     *
     * @param in bounded cursor
     * @return unsigned value */
    private static int u1(ByteBuffer in){return Byte.toUnsignedInt(in.get());}
    /** Reads an unsigned short, rejecting truncation through the bounded cursor.
     *
     * @param in bounded cursor
     * @return unsigned value */
    private static int u2(ByteBuffer in){return Short.toUnsignedInt(in.getShort());}
    /** Advances only within the enclosing class/attribute boundary.
     *
     * @param in bounded cursor
     * @param bytes nonnegative byte count */
    private static void skip(ByteBuffer in,int bytes) {
        if(bytes<0 || bytes>in.remaining())throw problem("MALFORMED","Invalid metadata extent",null);
        in.position(in.position()+bytes);
    }
}
