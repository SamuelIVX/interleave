/** Inclusive input caps and declaration-placement negatives, using compiler/Class-File API fixtures. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.ClassDesc;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeInputBoundaryTest {
    /** Exact1MiB classes and8MiB manifests load; one additional byte rejects before parsing.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void inclusiveClassAndAggregateByteCaps(@TempDir Path directory) throws Exception {
        StringBuilder source=new StringBuilder("@Model public class Fixture {public Fixture() {} @Actor(id=0) public void actor(){H0.call();}} ");
        for(int i=0;i<8;i++) source.append("class H").append(i).append(" {static void call(){")
            .append(i<7 ? "H"+(i+1)+".call();" : "").append("}} ");
        Path classes=BytecodeFixtures.compile(directory,26,source.toString());
        for(String name:List.of("Fixture","H0","H1","H2","H3","H4","H5")) pad(classes.resolve(name+".class"),1_048_576);
        // Seven full classes plus two smaller ones: make the eighth consume exactly the remaining total.
        int last=(int)Files.size(classes.resolve("H7.class"))+64;
        pad(classes.resolve("H7.class"),last);
        pad(classes.resolve("H6.class"),1_048_576-last);
        long total=0;try(var inputs=Files.list(classes)){for(Path input:inputs.toList())if(input.toString().endsWith(".class"))total+=Files.size(input);}
        assertEquals(8_388_608L,total);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()); assertEquals(9,program.classes.size());
        // Increasing the small final class by one crosses aggregate but not per-class bounds.
        pad(classes.resolve("H7.class"),last+1);
        assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        Path one=BytecodeFixtures.compile(directory.resolve("one"),17,"@Model public class Fixture { public Fixture() {} @Actor(id=0) public void actor() {} }");
        pad(one.resolve("Fixture.class"),1_048_576);
        assertDoesNotThrow(()->BytecodeLoader.load(one,"Fixture",BytecodeLimits.defaults()));
        pad(one.resolve("Fixture.class"),1_048_576+1);
        assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(one,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Closed signature-only classes count toward the64-class limit.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void classCountAndUnknownMetadata(@TempDir Path directory) throws Exception {
        for(int count:new int[]{63,64}) {
            StringBuilder source=new StringBuilder("@Model public class Fixture {public Fixture() {} @Actor(id=0) public void actor() {} ");
            for(int i=0;i<count;i++) source.append("H").append(i).append(" value").append(i).append(";");
            source.append("} "); for(int i=0;i<count;i++) source.append("class H").append(i).append(" {} ");
            Path classes=BytecodeFixtures.compile(directory.resolve("count"+count),26,source.toString());
            if(count==63) assertEquals(64,BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()).classes.size());
            else assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        }
        Path missing=BytecodeFixtures.compile(directory.resolve("missing"),26,"@Model public class Fixture {H value; public Fixture() {} @Actor(id=0) public void actor() {} } class H {}");
        Files.delete(missing.resolve("H.class"));
        assertEquals("METADATA",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(missing,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Forged annotation placement and interface metadata must not bypass protocol validation.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void annotationRolesHaveExactDeclarationOwners(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture {public Fixture() {} @Actor(id=0) public void actor() {} }");
        var parser=ClassFile.of(); byte[] original=Files.readAllBytes(classes.resolve("Fixture.class"));
        byte[] forged=parser.transformClass(parser.parse(original),ClassTransform.endHandler(builder->builder.with(
            RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("dev.samhb.interleave.bytecode.Actor"),AnnotationElement.ofInt("id",0))))));
        Files.write(classes.resolve("Fixture.class"),forged);
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        // Single attribute with both roles avoids relying on duplicate-attribute detection.
        forged=parser.transformClass(parser.parse(original),(builder,element)-> {
            if(element instanceof RuntimeInvisibleAnnotationsAttribute annotation)
                builder.with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("dev.samhb.interleave.bytecode.Model")),
                    Annotation.of(ClassDesc.of("dev.samhb.interleave.bytecode.Actor"),AnnotationElement.ofInt("id",0))));
            else builder.with(element);
        });
        Files.write(classes.resolve("Fixture.class"),forged);
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        Path interfaces=BytecodeFixtures.compile(directory.resolve("interface"),26,
            "@Model public class Fixture { Marker value; public Fixture() {} @Actor(id=0) public void actor() {} } interface Marker {}");
        assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(interfaces,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Pads with a bounded, nonexecuting standard debug attribute, retaining a valid class.
     *
     * @param path compiled class
     * @param size desired exact file size
     * @throws Exception for fixture failures */
    private static void pad(Path path,int size) throws Exception {
        var parser=ClassFile.of(); var model=parser.parse(Files.readAllBytes(path));
        ClassTransform remove=(builder,element)-> {if(!(element instanceof SourceDebugExtensionAttribute)) builder.with(element);};
        var base=parser.parse(parser.transformClass(model,remove));
        byte[] empty=parser.transformClass(base,ClassTransform.endHandler(builder->builder.with(SourceDebugExtensionAttribute.of(new byte[0]))));
        int padding=size-empty.length; assertTrue(padding>=0,"Padding must not shrink live metadata");
        byte[] contents=new byte[padding]; Arrays.fill(contents,(byte)' ');
        byte[] result=parser.transformClass(base,ClassTransform.endHandler(builder->builder.with(SourceDebugExtensionAttribute.of(contents))));
        assertEquals(size,result.length); Files.write(path,result);
    }
}
