/**
 * Bounded, metadata-only admission and interpreted setup for the internal first bytecode profile.
 */
package dev.samhb.interleave.bytecode;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.security.*;
import java.util.*;
import static java.lang.classfile.ClassFile.*;

/**
 * Loads one closed application without compiling, linking or executing target code on the host JVM.
 * The classpath root and its entries must remain immutable throughout loading. Concurrent replacement
 * is outside this profile: secure directory handles prevent path escape, but are not a filesystem snapshot.
 */
final class BytecodeLoader {
    /**
     * Maximum bytes allocated for one class read.
     */
    static final int MAX_CLASS_BYTES=1_048_576;
    /**
     * Aggregate frozen application input bound.
     */
    static final int MAX_TOTAL_BYTES=8_388_608;
    /**
     * Closed application class bound.
     */
    static final int MAX_CLASSES=64;
    /**
     * Annotation protocol prefix; definitions are test-only until94C.
     */
    private static final String ANNOTATION="Ldev/samhb/interleave/bytecode/";
    /**
     * Loaded class models, each backed by private frozen bytes.
     */
    private final Map<String,ClassModel> classes=new TreeMap<>();
    /**
     * Exact bytes for identity, never returned to callers.
     */
    private final Map<String,byte[]> bytes=new TreeMap<>();
    /**
     * Resolved field declarations.
     */
    private final Map<BytecodeProgram.FieldId,BytecodeProgram.Field> fields=new HashMap<>();
    /**
     * Reachable interpreted methods.
     */
    private final Map<BytecodeMethod.Id,BytecodeMethod> methods=new HashMap<>();
    /**
     * Complete declaration index, built once per class so repeated references do not rescan metadata.
     */
    private final Map<BytecodeMethod.Id,MethodModel> declarations=new HashMap<>();
    /**
     * Canonical classpath root, held through secure directory operations during reads.
     */
    private final Path root;
    /**
     * Selected fixture internal name.
     */
    private final String model;
    /**
     * Input byte accounting.
     */
    private int total;
    /**
     * Pending role-specific call-graph validation.
     */
    private final ArrayDeque<Work> pending=new ArrayDeque<>();
    /**
     * Visited role/method products prevent recursion in the loader itself.
     */
    private final Set<Work> visited=new HashSet<>();
    /**
     * Execution ownership roles.
     */
    enum Role {
        /**
         * Interpreted fixture construction with separate allowances.
         */ SETUP,
        /**
         * Actor code, including transitively called helpers.
         */ ACTOR,
        /**
         * Read-only observer call graph.
         */ CHECK
    }
    /**
     * Tracks a method and execution role as one call-graph validation obligation.
     * @param method target method
     * @param role execution context for allocation/purity checks
     */
    private record Work(BytecodeMethod.Id method, Role role) {}

    /**
     * Creates one bounded loader instance.
     * @param directory input class directory
     * @param model binary fixture name
     * @throws IOException for invalid/unavailable directories
     */
    private BytecodeLoader(Path directory,String model) throws IOException {
        this.root=directory.toRealPath(); this.model=internal(model);
        if (!Files.isDirectory(root)) throw problem("INPUT","Classpath must be a directory",null);
    }

    /**
     * SECURITY: parses untrusted bytes with explicit metadata hierarchy resolution and no host fallback.
     * @param directory sole application classpath directory; root and entries immutable until this call returns
     * @param model binary fixture name
     * @param limits immutable execution limits
     * @return frozen validated artifact with interpreted setup
     * @throws BytecodeLoadException for malformed/unsupported input, limits or setup failure
     */
    static BytecodeProgram load(Path directory,String model,BytecodeLimits limits) {
        if (directory==null || model==null || limits==null) throw problem("INPUT","Null loader argument",null);
        try { return new BytecodeLoader(directory,model).load(limits); }
        catch (BytecodeLoadException diagnostic) { throw diagnostic; }
        catch (IOException failure) { throw problem("IO",failure.getMessage(),null); }
        catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
            throw problem("MALFORMED",failure.getMessage(),null);
        }
    }

    /**
     * Validates roots and the complete reachable call graph, then executes setup internally.
     * @param limits resource profile
     * @return immutable initialized artifact
     * @throws IOException for bounded input failures
     */
    private BytecodeProgram load(BytecodeLimits limits) throws IOException {
        ClassModel fixture=read(model);
        if ((fixture.flags().flagsMask() & (ACC_ABSTRACT|ACC_INTERFACE|ACC_ANNOTATION|ACC_ENUM))!=0)
            throw problem("DECLARATION","Model must be a concrete class",null);
        var modelAnnotations=annotations(fixture,ANNOTATION+"Model;");
        if (modelAnnotations.size()!=1 || !modelAnnotations.getFirst().elements().isEmpty())
            throw problem("DECLARATION","Expected one parameterless @Model",null);
        var actors=new TreeMap<Integer,BytecodeMethod.Id>(); var checks=new ArrayList<BytecodeProgram.Check>();
        for (MethodModel method : fixture.methods()) {
            var actor=annotations(method,ANNOTATION+"Actor;"); var check=annotations(method,ANNOTATION+"Check;");
            if (actor.size()+check.size()>1) throw problem("DECLARATION","Duplicate/mixed method roles",null);
            var id=id(model,method);
            if (!actor.isEmpty()) {
                entry(method,"()V"); var elements=actor.getFirst().elements();
                if (elements.size()!=1 || !elements.getFirst().name().equalsString("id")
                    || !(elements.getFirst().value() instanceof AnnotationValue.OfInt number))
                    throw problem("DECLARATION","Actor requires an integer id",null);
                int owner=number.intValue();
                if (owner<0 || owner>=8 || actors.putIfAbsent(owner,id)!=null)
                    throw problem("DECLARATION","Invalid/duplicate actor id",null);
                pending.add(new Work(id,Role.ACTOR));
            }
            if (!check.isEmpty()) {
                entry(method,"()Z"); boolean always=false;
                var elements=check.getFirst().elements();
                if (!elements.isEmpty()) {
                    if (elements.size()!=1 || !elements.getFirst().name().equalsString("when")
                        || !(elements.getFirst().value() instanceof AnnotationValue.OfEnum when)
                        || !when.className().equalsString(ANNOTATION+"Check$When;")
                        || !(when.constantName().equalsString("ALWAYS") || when.constantName().equalsString("FINAL")))
                        throw problem("DECLARATION","Invalid check timing",null);
                    always=when.constantName().equalsString("ALWAYS");
                }
                checks.add(new BytecodeProgram.Check(id,always)); pending.add(new Work(id,Role.CHECK));
            }
        }
        if (actors.isEmpty() || actors.lastKey()!=actors.size()-1) throw problem("DECLARATION","Actor IDs must be dense from zero",null);
        var constructor=new BytecodeMethod.Id(model,"<init>","()V");
        MethodModel setup=method(constructor);
        if ((setup.flags().flagsMask() & ACC_PUBLIC)==0) throw problem("DECLARATION","Fixture requires public ()V constructor",null);
        pending.add(new Work(constructor,Role.SETUP));
        while (!pending.isEmpty()) {
            Work work=pending.removeFirst(); if (!visited.add(work)) continue;
            MethodModel method=method(work.method());
            if ((method.flags().flagsMask() & (ACC_NATIVE|ACC_ABSTRACT|ACC_BRIDGE))!=0 || method.code().isEmpty())
                throw problem("UNSUPPORTED","Reachable native/abstract/bridge method",null);
            signature(method.methodTypeSymbol());
            var decoded=methods.computeIfAbsent(work.method(),ignored -> new BytecodeMethod(work.method().owner(),method));
            BytecodeVerifier.validate(decoded,work.role());
            if (work.role()==Role.CHECK && (decoded.flags & ACC_SYNCHRONIZED)!=0)
                throw problem("PURITY","Checks cannot synchronize",decoded.location(0));
            for (var op : decoded.ops.values()) validateReference(decoded,op,work.role());
        }
        // Parsing is not verification. Never let the default reflection resolver participate.
        var verifier=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(this::hierarchy));
        for (ClassModel type : classes.values()) {
            List<VerifyError> errors=verifier.verify(type);
            if (!errors.isEmpty()) throw problem("MALFORMED",errors.getFirst().getMessage(),null);
        }
        checks.sort(Comparator.comparing((BytecodeProgram.Check c)->c.method().owner())
            .thenComparing(c->c.method().name()).thenComparing(c->c.method().descriptor()));
        var program=new BytecodeProgram(model,methods,fields,new ArrayList<>(actors.values()),checks,
            new ArrayList<>(classes.keySet()),limits,digest(),null);
        var state=BytecodeState.beforeSetup(program); state.start(BytecodeState.SETUP,methods.get(constructor),List.of(BytecodeState.FIXTURE));
        while (state.executions.get(BytecodeState.SETUP).phase!=BytecodeState.Phase.TERMINATED) {
            state=OpcodeExecutor.step(program,state,BytecodeState.SETUP).state();
            var execution=state.executions.get(BytecodeState.SETUP);
            if (execution.phase==BytecodeState.Phase.FAILED || execution.phase==BytecodeState.Phase.CUTOFF)
                throw problem("SETUP",execution.fault!=null ? execution.fault.type() : execution.cutoff.reason(),
                    execution.fault!=null ? execution.fault.location() : execution.cutoff.location());
        }
        return new BytecodeProgram(model,methods,fields,program.actors,checks,program.classes,limits,program.artifactDigest(),state);
    }

    /**
     * Resolves every executable member reference before exploration.
     * @param method calling method
     * @param op actual instruction
     * @param role invocation context
     * @throws IOException for newly reached application metadata
     */
    private void validateReference(BytecodeMethod method,BytecodeMethod.Op op,Role role) throws IOException {
        Instruction instruction=op.instruction(); var location=method.location(op.bci());
        if (instruction instanceof FieldInstruction field) {
            String owner=field.owner().asInternalName(); read(owner);
            var id=new BytecodeProgram.FieldId(owner,field.name().stringValue(),field.type().stringValue());
            var declaration=fields.get(id);
            if (declaration==null) throw problem("LINKAGE","Missing field "+id,location);
            access(method.id.owner(),owner,declaration.flags(),location);
            boolean isStatic=instruction.opcode()==Opcode.GETSTATIC || instruction.opcode()==Opcode.PUTSTATIC;
            if (((declaration.flags() & ACC_STATIC)!=0)!=isStatic) throw problem("LINKAGE","Field staticness mismatch",location);
            boolean write=instruction.opcode()==Opcode.PUTFIELD || instruction.opcode()==Opcode.PUTSTATIC;
            if (write && (declaration.flags() & ACC_FINAL)!=0 && !(method.id.owner().equals(owner) && method.id.name().equals("<init>") && !isStatic))
                throw problem("LINKAGE","Illegal final field write",location);
        }
        if (instruction instanceof NewObjectInstruction allocation) {
            if (!allocation.className().asInternalName().equals("java/lang/Object"))
                throw problem("UNSUPPORTED","Only setup Object monitor allocation is supported",location);
        }
        if (instruction instanceof InvokeInstruction call) {
            String owner=call.owner().asInternalName(), name=call.name().stringValue(), descriptor=call.type().stringValue();
            if (owner.equals("java/lang/Object") && name.equals("<init>") && descriptor.equals("()V")
                && call.opcode()==Opcode.INVOKESPECIAL && !call.isInterface()) return;
            if (call.opcode()!=Opcode.INVOKESTATIC || call.isInterface())
                throw problem("UNSUPPORTED","Only known static application helpers are supported",location);
            read(owner); var id=new BytecodeMethod.Id(owner,name,descriptor); MethodModel callee=method(id);
            if ((callee.flags().flagsMask() & ACC_STATIC)==0) throw problem("LINKAGE","Call staticness mismatch",location);
            access(method.id.owner(),owner,callee.flags().flagsMask(),location); pending.add(new Work(id,role));
        }
    }

    /**
     * Reads one application class under directory handles, with bounded memory and no symlink traversal.
     * @param name internal class name
     * @return parsed class model
     * @throws IOException for unavailable input
     */
    private ClassModel read(String name) throws IOException {
        if (classes.containsKey(name)) return classes.get(name);
        internal(name.replace('/','.'));
        if (name.startsWith("java/") || name.startsWith("jdk/") || name.startsWith("sun/"))
            throw problem("UNSUPPORTED","Unknown platform metadata: "+name,null);
        if (classes.size()>=MAX_CLASSES) throw problem("LIMIT","More than64 application classes",null);
        byte[] input;
        try (var directory=Files.newDirectoryStream(root)) {
            if (!(directory instanceof SecureDirectoryStream<Path> secure))
                throw problem("UNSUPPORTED","Classpath provider lacks secure directory access",null);
            input=read(secure,(name+".class").split("/"),0,Math.min(MAX_CLASS_BYTES,MAX_TOTAL_BYTES-total));
        } catch (NoSuchFileException missing) {
            throw problem("METADATA","Missing application class "+name,null);
        }
        if (input.length>MAX_CLASS_BYTES || input.length>MAX_TOTAL_BYTES-total)
            throw problem("LIMIT","Class/aggregate input byte limit exceeded",null);
        total+=input.length;
        if (input.length<8 || input[0]!=(byte)0xca || input[1]!=(byte)0xfe || input[2]!=(byte)0xba || input[3]!=(byte)0xbe)
            throw problem("MALFORMED","Invalid/truncated class header",null);
        int major=(Byte.toUnsignedInt(input[6])<<8)|Byte.toUnsignedInt(input[7]);
        if (major<61 || major>70 || input[4]!=0 || input[5]!=0)
            throw problem("UNSUPPORTED","Expected non-preview Java17–26 class file",null);
        BytecodeInput.validate(input);
        ClassModel type=ClassFile.of().parse(input);
        if (!type.thisClass().asInternalName().equals(name)) throw problem("MALFORMED","Class name/path mismatch",null);
        if ((type.flags().flagsMask() & (ACC_INTERFACE|ACC_ANNOTATION|ACC_ENUM|ACC_MODULE))!=0)
            throw problem("UNSUPPORTED","Unsupported application class kind",null);
        annotationPlacement(type,name.equals(model) ? Set.of("Model") : Set.of());
        classes.put(name,type); bytes.put(name,input);
        String parent=type.superclass().orElseThrow(()->problem("MALFORMED","Application class requires a superclass",null)).asInternalName();
        if (!parent.equals("java/lang/Object")) {
            read(parent); throw problem("UNSUPPORTED","Application inheritance starts in95",null);
        }
        if (!type.interfaces().isEmpty()) throw problem("UNSUPPORTED","Application interfaces start in95",null);
        for (FieldModel field : type.fields()) {
            annotationPlacement(field,Set.of());
            String descriptor=field.fieldType().stringValue(); fieldType(field.fieldTypeSymbol());
            var id=new BytecodeProgram.FieldId(name,field.fieldName().stringValue(),descriptor);
            BytecodeState.Value initial=descriptor.equals("I") || descriptor.equals("Z") ? new BytecodeState.Int(0) : BytecodeState.NULL;
            var constant=field.findAttribute(Attributes.constantValue());
            if (constant.isPresent() && (field.flags().flagsMask() & ACC_STATIC)!=0) {
                if (!(constant.get().constant().constantValue() instanceof Integer value))
                    throw problem("UNSUPPORTED","Unsupported executable ConstantValue type",null);
                initial=new BytecodeState.Int(descriptor.equals("Z") ? value & 1 : value);
            }
            if (fields.putIfAbsent(id,new BytecodeProgram.Field(id,field.flags().flagsMask(),initial))!=null)
                throw problem("MALFORMED","Duplicate field",null);
        }
        for (MethodModel method : type.methods()) {
            if(declarations.putIfAbsent(id(name,method),method)!=null)
                throw problem("MALFORMED","Duplicate method identity",null);
            annotationPlacement(method,name.equals(model) ? Set.of("Actor","Check") : Set.of());
            if (method.methodName().equalsString("<clinit>")) {
                if (method.code().isEmpty()) throw problem("MALFORMED","Initializer missing code",null);
                var code=new BytecodeMethod(name,method);
                if (code.ops.size()!=1 || code.ops.firstEntry().getValue().instruction().opcode()!=Opcode.RETURN || !code.code.exceptionHandlers().isEmpty())
                    throw problem("UNSUPPORTED","Executable application <clinit>",code.location(0));
            }
        }
        return type;
    }

    /**
     * Resolves one nested path using no-follow directory/file handles.
     * @param directory pinned parent handle
     * @param parts validated relative components
     * @param index current component
     * @param maximum remaining bounded read allowance
     * @return at most maximum+1 bytes
     * @throws IOException for a missing/symlink/invalid entry
     * @throws BytecodeLoadException when a leaf is not a regular file
     */
    private static byte[] read(SecureDirectoryStream<Path> directory,String[] parts,int index,int maximum) throws IOException {
        if (index<parts.length-1) {
            try (var child=directory.newDirectoryStream(Path.of(parts[index]),LinkOption.NOFOLLOW_LINKS)) {
                return read(child,parts,index+1,maximum);
            }
        }
        Path leaf=Path.of(parts[index]);
        // shortcut: immutable inputs exclude replacement between this check and open; revisit for mutable classpaths.
        var attributes=directory.getFileAttributeView(leaf,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if (!attributes.readAttributes().isRegularFile())
            throw problem("INPUT","Class input must be a regular file",null);
        try (var channel=directory.newByteChannel(leaf,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS));
             var stream=Channels.newInputStream(channel)) { return stream.readNBytes(maximum+1); }
    }

    /**
     * Resolves signature-only application classes iteratively, with explicit trusted Object metadata.
     * @param type declared field/argument/return type
     * @throws IOException for missing referenced application bytes
     */
    private void fieldType(ClassDesc type) throws IOException {
        String descriptor=type.descriptorString();
        if (descriptor.equals("I") || descriptor.equals("Z")) return;
        if (!type.isClassOrInterface()) throw problem("UNSUPPORTED","Only int/boolean/reference types in94A",null);
        String owner=descriptor.substring(1,descriptor.length()-1);
        if (!owner.equals("java/lang/Object")) read(owner);
    }

    /**
     * Checks all parameter and return types against the first profile.
     * @param descriptor method type
     * @throws IOException for missing signature classes
     */
    private void signature(MethodTypeDesc descriptor) throws IOException {
        for (ClassDesc parameter : descriptor.parameterList()) fieldType(parameter);
        if (!descriptor.returnType().equals(ConstantDescs.CD_void)) fieldType(descriptor.returnType());
    }

    /**
     * Resolves a declared application method by its full overload identity.
     * @param id complete method identity
     * @return declared metadata
     * @throws IOException for missing class
     */
    private MethodModel method(BytecodeMethod.Id id) throws IOException {
        read(id.owner());
        var declaration=declarations.get(id);
        if(declaration==null)throw problem("LINKAGE","Missing method "+id,null);
        return declaration;
    }

    /**
     * Enforces public, non-static, concrete, non-native ordinary methods with the exact
     * no-argument actor void or check boolean descriptor. Special methods cannot be entries.
     * @param method annotated entry
     * @param descriptor required shape
     * @throws BytecodeLoadException with DECLARATION when the annotated method has an invalid shape
     */
    private static void entry(MethodModel method,String descriptor) {
        int flags=method.flags().flagsMask();
        if ((flags & ACC_PUBLIC)==0 || (flags & (ACC_STATIC|ACC_NATIVE|ACC_ABSTRACT))!=0
            || !method.methodType().equalsString(descriptor) || method.methodName().stringValue().startsWith("<"))
            throw problem("DECLARATION","Invalid actor/check method shape",null);
    }

    /**
     * Checks application class and member visibility without host linkage. Public members require
     * an accessible class; private access requires the declaring class; package/protected access
     * requires the same package in this profile. Nestmate and subclass access arrive in95.
     * @param caller accessing class
     * @param owner declaring class
     * @param flags member flags
     * @param location calling BCI
     * @throws BytecodeLoadException with LINKAGE for an inaccessible class or member
     */
    private void access(String caller,String owner,int flags,BytecodeLocation location) {
        int classFlags=classes.get(owner).flags().flagsMask();
        boolean samePackage=packageName(caller).equals(packageName(owner));
        if (((classFlags & ACC_PUBLIC)==0 && !samePackage) || ((flags & ACC_PUBLIC)==0
            && ((flags & ACC_PRIVATE)!=0 ? !caller.equals(owner) : !samePackage)))
            throw problem("LINKAGE","Inaccessible member (nestmate access starts in95)",location);
    }

    /**
     * Extracts an internal name's package for access checks.
     * @param name internal name
     * @return package portion
     */
    private static String packageName(String name) { int slash=name.lastIndexOf('/'); return slash<0 ? "" : name.substring(0,slash); }

    /**
     * Builds an immutable overload identity from class-file metadata.
     * @param owner declared class
     * @param method metadata
     * @return complete identity
     */
    private static BytecodeMethod.Id id(String owner,MethodModel method) {
        return new BytecodeMethod.Id(owner,method.methodName().stringValue(),method.methodType().stringValue());
    }

    /**
     * Reads only the declared CLASS-retained annotation protocol.
     * @param element attributed declaration
     * @param descriptor annotation protocol
     * @return matching CLASS-retained annotations
     */
    private static List<Annotation> annotations(AttributedElement element,String descriptor) {
        var annotations=new ArrayList<Annotation>();
        for (var attribute : element.attributes()) {
            if (attribute instanceof RuntimeInvisibleAnnotationsAttribute invisible) {
                invisible.annotations().stream().filter(a->a.className().equalsString(descriptor)).forEach(annotations::add);
            }
            if (attribute instanceof RuntimeVisibleAnnotationsAttribute visible
                && visible.annotations().stream().anyMatch(a->a.className().equalsString(descriptor)))
                throw problem("DECLARATION","Protocol annotations require CLASS retention",null);
        }
        return annotations;
    }

    /**
     * Rejects protocol roles on the wrong declaration, without loading annotation classes.
     * @param element class/field/method metadata
     * @param allowed roles owned by that declaration
     */
    private static void annotationPlacement(AttributedElement element,Set<String> allowed) {
        for (String role : List.of("Model","Actor","Check")) {
            if (!annotations(element,ANNOTATION+role+";").isEmpty() && !allowed.contains(role))
                throw problem("DECLARATION","Annotation role on wrong declaration: "+role,null);
        }
    }

    /**
     * Metadata-only hierarchy; unknown names never fall through to a reflection resolver.
     * @param descriptor queried class type
     * @return explicit trusted/application hierarchy
     */
    private ClassHierarchyResolver.ClassHierarchyInfo hierarchy(ClassDesc descriptor) {
        String name=descriptor.descriptorString(); name=name.substring(1,name.length()-1);
        String parent=switch(name) {
            case "java/lang/Object" -> null;
            case "java/lang/Throwable" -> "java/lang/Object";
            case "java/lang/Exception" -> "java/lang/Throwable";
            case "java/lang/RuntimeException" -> "java/lang/Exception";
            case "java/lang/NullPointerException", "java/lang/ArithmeticException", "java/lang/IllegalMonitorStateException" -> "java/lang/RuntimeException";
            default -> {
                var model=classes.get(name);
                if (model==null) throw problem("METADATA","Unknown hierarchy type "+name,null);
                yield model.superclass().orElseThrow().asInternalName();
            }
        };
        return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(parent==null ? null : ClassDesc.of(parent.replace('/','.')));
    }

    /**
     * Hashes the frozen class manifest and explicit intrinsic-profile version.
     * @return length-prefixed exact-byte manifest identity with the internal profile version
     */
    private String digest() {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            var sink=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest));
            sink.writeUTF("BYTECODE-94A-1"); sink.writeInt(bytes.size());
            for (var entry : bytes.entrySet()) {
                byte[] name=entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                sink.writeInt(name.length); sink.write(name); sink.writeInt(entry.getValue().length); sink.write(entry.getValue());
            }
            sink.flush(); return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * Validates a bounded binary name before resolving any classpath entry.
     * @param binary binary name
     * @return validated internal name
     */
    private static String internal(String binary) {
        String[] parts=binary.split("\\.",-1);
        if (binary.length()>4096 || parts.length>256) throw problem("INPUT","Invalid binary name",null);
        for (String part : parts) {
            if (part.isEmpty()) throw problem("INPUT","Invalid binary name",null);
            for (int offset=0; offset<part.length();) {
                int cp=part.codePointAt(offset);
                if (offset==0 ? !Character.isJavaIdentifierStart(cp) : !Character.isJavaIdentifierPart(cp))
                    throw problem("INPUT","Invalid binary name",null);
                offset+=cp>0xffff ? 2 : 1;
            }
        }
        return binary.replace('.','/');
    }

    /**
     * Creates a categorized load diagnostic at an optional actual bytecode position.
     * @param category diagnostic family
     * @param text explanation
     * @param location actual position
     * @return loading diagnostic
     */
    static BytecodeLoadException problem(String category,String text,BytecodeLocation location) {
        return new BytecodeLoadException(category,text,location);
    }
}
