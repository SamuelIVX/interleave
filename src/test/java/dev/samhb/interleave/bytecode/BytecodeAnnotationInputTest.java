/** Hostile annotation metadata is checked before the JDK's recursive element-value reader. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeAnnotationInputTest {
    /** Deep arrays below the byte cap must reject with a diagnostic, never leak StackOverflowError.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void boundsAnnotationNestingBeforeRecursiveParsing(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture {public Fixture(){} @Actor(id=0) public void actor(){} }");
        Path file=classes.resolve("Fixture.class"); var parser=ClassFile.of();
        Files.write(file,parser.transformClass(parser.parse(Files.readAllBytes(file)),(out,element)-> {
            if(element instanceof RuntimeInvisibleAnnotationsAttribute)out.with(new NestedAnnotations(20000));else out.with(element);
        }));
        assertTrue(Files.size(file)<BytecodeLoader.MAX_CLASS_BYTES);
        assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,
            ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }


    /** The exact nesting boundary and normal unrelated metadata remain admissible.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void acceptsBoundedUnrelatedAnnotationMetadata(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture {public Fixture(){} @Actor(id=0) public void actor(){} }");
        Path file=classes.resolve("Fixture.class");var parser=ClassFile.of();var original=parser.parse(Files.readAllBytes(file));
        for(int depth:new int[]{0,1,32,33}) {
            Files.write(file,parser.transformClass(original,(out,element)-> {
                if(element instanceof RuntimeInvisibleAnnotationsAttribute)out.with(new NestedAnnotations(depth));else out.with(element);
            }));
            if(depth<=32)assertEquals("Fixture",BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()).model);
            else assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,
                ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        }
    }

    /** Parameter, type-use, nested and constant annotations are inert supported metadata.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void acceptsOrdinaryAnnotationContainers(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            import java.lang.annotation.*;
            @Model @Info(values={1,2}, nests={@Nest(name="a"),@Nest(name="b")}, kind=RetentionPolicy.CLASS,
                type=Object.class, flag=true, ch='x', number=4L, decimal=1.5, single=2.5f, small=3, narrow=4)
            public class Fixture {
              @Mark Object lock;int value;public Fixture(){}
              @Actor(id=0)public void actor(){value=helper(1);@Mark Object local=lock;}
              static int helper(@Param("x") int x){return x;}
            }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE_USE) @interface Mark {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface Param{String value();}
            @interface Nest{String name();}
            @interface Info{int[] values();Nest[] nests();RetentionPolicy kind();Class<?> type();boolean flag();char ch();
              long number();double decimal();float single();byte small();short narrow();}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(new BytecodeState.Int(1),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("value"));
    }


    /** Modified-UTF aliases of an attribute name cannot bypass the nesting guard.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void encodedAttributeNamesCannotBypassPreflight(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture {public Fixture(){} @Actor(id=0) public void actor(){} }");
        Path file=classes.resolve("Fixture.class");var parser=ClassFile.of();
        byte[] input=parser.transformClass(parser.parse(Files.readAllBytes(file)),(out,element)-> {
            if(element instanceof RuntimeInvisibleAnnotationsAttribute)out.with(new NestedAnnotations(20000));else out.with(element);
        });
        byte[] name="RuntimeInvisibleAnnotations".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int offset=-1;
        for(int index=2;index<input.length-name.length;index++) {
            if(java.util.Arrays.equals(name,0,name.length,input,index,index+name.length)){offset=index;break;}
        }
        assertTrue(offset>0);byte[] aliased=new byte[input.length+1];
        System.arraycopy(input,0,aliased,0,offset);aliased[offset-1]=(byte)(name.length+1);
        aliased[offset]=(byte)0xc1;aliased[offset+1]=(byte)0x92;
        System.arraycopy(input,offset+1,aliased,offset+2,input.length-offset-1);Files.write(file,aliased);
        var diagnostic=assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        assertTrue(java.util.Set.of("LIMIT","MALFORMED").contains(diagnostic.category),diagnostic.getMessage());
    }


    /** Record components also carry recursively decoded metadata, even on an otherwise supported class.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsInjectedRecordBeforeComponentAnnotationParsing(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture {public Fixture(){} @Actor(id=0) public void actor(){} }");
        Path file=classes.resolve("Fixture.class");var parser=ClassFile.of();
        Files.write(file,parser.transformClass(parser.parse(Files.readAllBytes(file)),
            ClassTransform.endHandler(out->out.with(new RawRecord()))));
        assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,
            ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Raw unsupported Record metadata with a hostile nested annotation component. */
    private static final class RawRecord extends CustomAttribute<RawRecord> {
        /** Metadata writer used only to generate the rejection fixture. */
        private static final AttributeMapper<RawRecord> MAPPER=new AttributeMapper<>() {
            /**
     * @return standard Record attribute name */
            public String name(){return "Record";}
            /** Unused: production JDK parsing reads the emitted bytes.
             *
     * @param enclosing unused
     * @param reader unused
     * @param offset unused
     * @return no value */
            public RawRecord readAttribute(AttributedElement enclosing,ClassReader reader,int offset){throw new UnsupportedOperationException();}
            /** Writes one component with a nested annotation attribute.
             *
     * @param out classfile sink
     * @param value raw fixture */
            public void writeAttribute(BufWriter out,RawRecord value){
                out.writeIndex(value.attributeName());out.writeInt(60029);out.writeU2(1);
                out.writeIndex(out.constantPool().utf8Entry("value"));out.writeIndex(out.constantPool().utf8Entry("I"));out.writeU2(1);
                NestedAnnotations.MAPPER.writeAttribute(out,new NestedAnnotations(20000));
            }
            /**
     * @return constant-pool-dependent attribute stability */
            public AttributeStability stability(){return AttributeStability.CP_REFS;}
        };
        /** Creates the test-owned raw attribute. */
        private RawRecord(){super(MAPPER);}
    }

    /** Raw writer avoids recursively constructing the hostile payload in the test itself. */
    private static final class NestedAnnotations extends CustomAttribute<NestedAnnotations> {
        /** Number of one-element array wrappers. */
        private final int depth;
        /** Writes a class-level annotation with one arbitrarily nested array element. */
        private static final AttributeMapper<NestedAnnotations> MAPPER=new AttributeMapper<>() {
            /**
     * @return standard annotation attribute name */
            public String name(){return "RuntimeInvisibleAnnotations";}
            /** Never used: the real production JDK reader reads the emitted bytes.
             *
     * @param enclosing unused
     * @param reader unused
     * @param offset unused
     * @return no value */
            public NestedAnnotations readAttribute(AttributedElement enclosing,ClassReader reader,int offset){throw new UnsupportedOperationException();}
            /** Emits two annotations, preserving the valid model role alongside hostile unrelated metadata.
             *
     * @param out classfile sink
     * @param value nesting request */
            public void writeAttribute(BufWriter out,NestedAnnotations value){
                out.writeIndex(value.attributeName());out.writeInt(15+value.depth*3);
                out.writeU2(2);
                out.writeIndex(out.constantPool().utf8Entry("Ldev/samhb/interleave/bytecode/Model;"));out.writeU2(0);
                out.writeIndex(out.constantPool().utf8Entry("LUnknown;"));out.writeU2(1);
                out.writeIndex(out.constantPool().utf8Entry("value"));
                for(int i=0;i<value.depth;i++){out.writeU1('[');out.writeU2(1);}
                out.writeU1('s');out.writeIndex(out.constantPool().utf8Entry("end"));
            }
            /**
     * @return constant-pool-dependent attribute stability */
            public AttributeStability stability(){return AttributeStability.CP_REFS;}
        };
        /**
     * @param depth nesting request */
        private NestedAnnotations(int depth){super(MAPPER);this.depth=depth;}
    }
}
