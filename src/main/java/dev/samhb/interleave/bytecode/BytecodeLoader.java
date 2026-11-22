/**
 * Bounded, metadata-only admission and interpreted setup for the bounded bytecode heap profile.
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
     * Annotation protocol prefix; definitions are parsed as metadata, never reflectively invoked.
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
    /** Exact trusted implementations admitted through direct and closed dispatch alternatives. */
    private final Set<BytecodeMethod.Id> intrinsics=new HashSet<>();
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
    /** Input version last successfully checked; each frozen class read strictly increases total bytes. */
    private int validatedBytes=-1;
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
        validateHierarchy();
        pending.add(new Work(constructor,Role.SETUP));
        int knownClasses;
        do {
            knownClasses=classes.size();
            while (!pending.isEmpty()) {
                Work work=pending.removeFirst(); if (!visited.add(work)) continue;
                MethodModel method=method(work.method());
                if ((method.flags().flagsMask() & (ACC_NATIVE|ACC_ABSTRACT|ACC_BRIDGE))!=0 || method.code().isEmpty())
                    throw problem("UNSUPPORTED","Reachable native/abstract/bridge method",null);
                signature(method.methodTypeSymbol());
                for(var handler:method.code().orElseThrow().exceptionHandlers())if(handler.catchType().isPresent()) {
                    String type=handler.catchType().orElseThrow().asInternalName();read(type);
                    if(!hierarchy().assignable(type,"java/lang/Throwable"))throw problem("MALFORMED","Catch type must extend Throwable",null);
                }
                var decoded=methods.computeIfAbsent(work.method(),ignored -> new BytecodeMethod(work.method().owner(),method));
                BytecodeVerifier.validate(decoded,work.role());
                if (work.role()==Role.CHECK && (decoded.flags & ACC_SYNCHRONIZED)!=0)
                    throw problem("PURITY","Checks cannot synchronize",decoded.location(0));
                for (var op : decoded.ops.values()) validateReference(decoded,op,work.role());
            }
            if(classes.size()!=knownClasses){pending.addAll(visited);visited.clear();}
        } while(!pending.isEmpty());
        validateHierarchy();
        // Parsing is not verification. Never let the default reflection resolver participate.
        var verifier=ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(this::hierarchy));
        for (ClassModel type : classes.values()) {
            List<VerifyError> errors=verifier.verify(type);
            if (!errors.isEmpty()) throw problem("MALFORMED",errors.getFirst().getMessage(),null);
        }
        boolean implicitExposed=methods.values().stream().anyMatch(decoded->decoded.code.exceptionHandlers().stream()
            .anyMatch(handler->!decoded.metadataCleanup(decoded.target(handler.handler()))));
        for(var decoded:methods.values()) {
            BytecodeVerifier.monitors(decoded,hierarchy());BytecodeAccessVerifier.validate(decoded,hierarchy(),fields,implicitExposed);
        }
        checks.sort(Comparator.comparing((BytecodeProgram.Check c)->c.method().owner())
            .thenComparing(c->c.method().name()).thenComparing(c->c.method().descriptor()));
        var program=new BytecodeProgram(model,methods,fields,new ArrayList<>(actors.values()),checks,
            new ArrayList<>(classes.keySet()),limits,digest(),null,hierarchy(),intrinsics);
        var state=BytecodeState.beforeSetup(program); state.start(BytecodeState.SETUP,methods.get(constructor),List.of(BytecodeState.FIXTURE));
        while (state.executions.get(BytecodeState.SETUP).phase!=BytecodeState.Phase.TERMINATED) {
            state=OpcodeExecutor.step(program,state,BytecodeState.SETUP).state();
            var execution=state.executions.get(BytecodeState.SETUP);
            if (execution.phase==BytecodeState.Phase.FAILED || execution.phase==BytecodeState.Phase.CUTOFF)
                throw problem("SETUP",execution.fault!=null ? execution.fault.type() : execution.cutoff.reason(),
                    execution.fault!=null ? execution.fault.location() : execution.cutoff.location());
        }
        return new BytecodeProgram(model,methods,fields,program.actors,checks,program.classes,limits,program.artifactDigest(),state,hierarchy(),intrinsics);
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
            String owner=field.owner().asInternalName(); read(owner);classAccess(method.id.owner(),owner,location);
            var id=hierarchy().field(new BytecodeProgram.FieldId(owner,field.name().stringValue(),field.type().stringValue()));
            var declaration=fields.get(id);
            if (declaration==null) throw problem("LINKAGE","Missing field "+field.name(),location);
            access(method.id.owner(),id.owner(),declaration.flags(),location);
            boolean isStatic=instruction.opcode()==Opcode.GETSTATIC || instruction.opcode()==Opcode.PUTSTATIC;
            if (((declaration.flags() & ACC_STATIC)!=0)!=isStatic) throw problem("LINKAGE","Field staticness mismatch",location);
            boolean write=instruction.opcode()==Opcode.PUTFIELD || instruction.opcode()==Opcode.PUTSTATIC;
            if (write && (declaration.flags() & ACC_FINAL)!=0 && !(method.id.owner().equals(id.owner()) && method.id.name().equals("<init>") && !isStatic))
                throw problem("LINKAGE","Illegal final field write",location);
        }
        if (instruction instanceof NewObjectInstruction allocation) {
            String type=allocation.className().asInternalName();
            if(!type.equals("java/lang/Object")) {
                var target=read(type);
                if(type.startsWith("java/") && !BytecodePlatform.constructible(type))
                    throw problem("UNSUPPORTED","Platform allocation is outside the exception profile",location);
                classAccess(method.id.owner(),type,location);
                if((target.flags().flagsMask() & (ACC_INTERFACE|ACC_ABSTRACT))!=0)
                    throw problem("LINKAGE","Cannot instantiate abstract/interface type",location);
            }
        }
        if(instruction instanceof NewReferenceArrayInstruction array) typeAccess(array.componentType().asSymbol(),method.id.owner(),location);
        if(instruction instanceof NewMultiArrayInstruction array) typeAccess(array.arrayType().asSymbol(),method.id.owner(),location);
        if(instruction instanceof TypeCheckInstruction check) typeAccess(check.type().asSymbol(),method.id.owner(),location);
        if (instruction instanceof InvokeInstruction call) validateCall(method,call,role,location);
    }

    /** Resolves and checks the leaf class named directly by array/type-check bytecode.
     * @param type named component, array or cast type
     * @param caller accessing application class
     * @param location real reference position
     * @throws IOException for unavailable metadata
     * @throws BytecodeLoadException with LINKAGE for an inaccessible symbolic leaf type */
    private void typeAccess(ClassDesc type,String caller,BytecodeLocation location) throws IOException {
        fieldType(type);while(type.isArray())type=type.componentType();
        if(type.isClassOrInterface() && !type.equals(ConstantDescs.CD_Object))
            classAccess(caller,type.descriptorString().substring(1,type.descriptorString().length()-1),location);
    }

    /** Validates symbolic linkage and queues every closed dispatch alternative in its execution role.
     * @param caller calling interpreted method
     * @param call symbolic invocation
     * @param role setup, actor or observer
     * @param location real invocation BCI
     * @throws IOException for unavailable application metadata */
    private void validateCall(BytecodeMethod caller,InvokeInstruction call,Role role,BytecodeLocation location) throws IOException {
        String owner=call.owner().asInternalName(),name=call.name().stringValue(),descriptor=call.type().stringValue();
        if(owner.startsWith("["))throw problem("UNSUPPORTED","Array library operations are outside the current profile",location);
        if(owner.equals("java/lang/Object") && name.equals("<init>") && descriptor.equals("()V")
            && call.opcode()==Opcode.INVOKESPECIAL && !call.isInterface())return;
        read(owner);classAccess(caller.id.owner(),owner,location);signature(call.typeSymbol());validateHierarchy();var hierarchy=hierarchy();
        if(name.equals("initCause") && descriptor.startsWith("(Ljava/lang/Throwable;)") && hierarchy.assignable(owner,"java/lang/Throwable"))
            throw problem("UNSUPPORTED","Throwable.initCause may invoke unsupported target callbacks",location);
        if(((hierarchy().metadata(owner).flags().flagsMask() & ACC_INTERFACE)!=0)!=call.isInterface())
            throw problem("LINKAGE","Invocation owner kind mismatch",location);
        var resolved=hierarchy.method(owner,name,descriptor);
        if(resolved==null)throw problem("LINKAGE","Missing method "+owner+"."+name+descriptor,location);
        int flags=hierarchy.flags(resolved);boolean isStatic=call.opcode()==Opcode.INVOKESTATIC;
        if(((flags & ACC_STATIC)!=0)!=isStatic || name.equals("<clinit>")
            || name.equals("<init>") && call.opcode()!=Opcode.INVOKESPECIAL)
            throw problem("LINKAGE","Invocation staticness/name mismatch",location);
        access(caller.id.owner(),resolved.owner(),flags,location);
        if(isStatic || call.opcode()==Opcode.INVOKESPECIAL || (flags & ACC_PRIVATE)!=0) {
            if((flags & ACC_ABSTRACT)!=0)throw problem("LINKAGE","Abstract direct invocation",location);
            if(call.opcode()==Opcode.INVOKESPECIAL) {
                resolved=hierarchy.special(caller.id.owner(),owner,resolved);
                if(resolved==null || (hierarchy.flags(resolved) & (ACC_ABSTRACT|ACC_STATIC))!=0)
                    throw problem("LINKAGE","Invalid selected special target",location);
            }
            admitTarget(resolved,role,location);return;
        }
        if(BytecodePlatform.intrinsic(resolved))admitTarget(resolved,role,location);
        else if(resolved.owner().startsWith("java/") && !(resolved.owner().equals("java/lang/AutoCloseable") && name.equals("close") && descriptor.equals("()V")))
            throw problem("UNSUPPORTED","Unsupported platform operation "+resolved,location);
        // Fixed-point rescans include classes reached by later constructors, signatures or dispatch bodies.
        for(var entry:hierarchy.types.entrySet()) {
            String runtime=entry.getKey();
            if(!classes.containsKey(runtime) && !BytecodePlatform.constructible(runtime) && !runtime.equals("java/lang/String"))continue;
            if((entry.getValue().flags().flagsMask() & (ACC_INTERFACE|ACC_ABSTRACT))!=0 || !hierarchy.assignable(runtime,owner))continue;
            var target=hierarchy.dispatch(entry.getKey(),resolved);
            if(target==null || (hierarchy.flags(target) & (ACC_ABSTRACT|ACC_STATIC))!=0
                || call.opcode()==Opcode.INVOKEINTERFACE && (hierarchy.flags(target) & ACC_PUBLIC)==0)
                throw problem("LINKAGE","Missing concrete dispatch target for "+entry.getKey(),location);
            admitTarget(target,role,location);
        }
    }

    /** Admits an exact intrinsic or queues an interpreted application callback.
     * @param target resolved dispatch declaration
     * @param role transitive execution role
     * @param location causing application invoke
     * @throws BytecodeLoadException for unsupported platform execution or impure observers */
    private void admitTarget(BytecodeMethod.Id target,Role role,BytecodeLocation location) {
        if(BytecodePlatform.intrinsic(target)) {
            if(role==Role.CHECK && (!target.name().equals("getMessage") || target.owner().equals("java/lang/NullPointerException")))throw problem("PURITY","Checks cannot allocate or synchronize through Throwable operations",location);
            intrinsics.add(target);return;
        }
        if(target.owner().startsWith("java/"))throw problem("UNSUPPORTED","Unsupported platform operation "+target,location);
        pending.add(new Work(target,role));
    }

    /** Freezes the currently discovered hierarchy for deterministic member lookup.
     * @return metadata-only hierarchy */
    private BytecodeHierarchy hierarchy(){return new BytecodeHierarchy(classes,fields);}

    /** Rejects cyclic/wrong-kind inheritance, final superclasses and inherited annotated entries.
     * @throws BytecodeLoadException for malformed closed hierarchy */
    private void validateHierarchy() {
        // The existing bounded byte count versions the immutable, monotonically growing class set.
        // No extra size calls or independently maintained generation counter are needed.
        if(validatedBytes==total)return;
        var hierarchy=hierarchy();
        for(var entry:classes.entrySet()) {
            String name=entry.getKey();var type=entry.getValue();nestHost(name);
            var path=new HashSet<String>();String parent=name;
            while(parent!=null && !parent.equals("java/lang/Object")) {
                if(!path.add(parent))throw problem("LINKAGE","Cyclic superclass hierarchy",null);
                parent=hierarchy.parent(parent);
            }
            String superclass=hierarchy.parent(name);
            if(superclass!=null && !superclass.equals("java/lang/Object")){classAccess(name,superclass,null);permittedSubtype(name,superclass);}
            if(superclass!=null && !superclass.equals("java/lang/Object")
                && (hierarchy.metadata(superclass).flags().flagsMask() & (ACC_INTERFACE|ACC_FINAL))!=0)
                throw problem("LINKAGE","Invalid superclass kind/finality",null);
            for(var face:type.interfaces()) {
                String target=face.asInternalName();classAccess(name,target,null);permittedSubtype(name,target);
                if((hierarchy.metadata(target).flags().flagsMask() & ACC_INTERFACE)==0
                    || hierarchy.ancestors(target).contains(name))throw problem("LINKAGE","Invalid/cyclic interface hierarchy",null);
            }
            for(var method:type.methods()) {
                String methodName=method.methodName().stringValue();int flags=method.flags().flagsMask();
                if(methodName.startsWith("<"))continue;
                String signature=methodName+method.methodType().stringValue();
                if(signature.equals("fillInStackTrace()Ljava/lang/Throwable;") && hierarchy.assignable(name,"java/lang/Throwable"))
                    throw problem("UNSUPPORTED","Application Throwable fillInStackTrace override",null);
                if((flags & (ACC_PRIVATE|ACC_STATIC))==0 && Set.of("getClass()Ljava/lang/Class;","notify()V","notifyAll()V","wait()V","wait(J)V","wait(JI)V").contains(signature))
                    throw problem("LINKAGE","Cannot override trusted final Object member "+signature,null);
                for(String ancestor:hierarchy.ancestors(name)) {
                    if(ancestor.equals(name) || ancestor.equals("java/lang/Object"))continue;
                    var inherited=new BytecodeMethod.Id(ancestor,methodName,method.methodType().stringValue());int inheritedFlags=hierarchy.flags(inherited);
                    if(inheritedFlags<0 || (inheritedFlags & ACC_PRIVATE)!=0
                        || (hierarchy.metadata(ancestor).flags().flagsMask() & ACC_INTERFACE)!=0 && (inheritedFlags & ACC_STATIC)!=0
                        || (inheritedFlags & (ACC_PUBLIC|ACC_PROTECTED))==0
                        && !packageName(name).equals(packageName(ancestor)))continue;
                    // JVM overriding is distinct from Java source visibility rules: narrower raw
                    // declarations may be valid, but an instance override of a final member is not.
                    if((flags & (ACC_STATIC|ACC_PRIVATE))==0 && (inheritedFlags & (ACC_STATIC|ACC_FINAL))==ACC_FINAL)
                        throw problem("LINKAGE","Illegal final method override "+name+"."+methodName,null);
                }
            }
            if(name.equals(model))for(String ancestor:hierarchy.ancestors(model)) {
                if(ancestor.equals(model) || ancestor.equals("java/lang/Object"))continue;
                for(var method:hierarchy.metadata(ancestor).methods())if(!annotations(method,ANNOTATION+"Actor;").isEmpty()
                    || !annotations(method,ANNOTATION+"Check;").isEmpty())throw problem("DECLARATION","Inherited annotated entry point",null);
            }
        }
        validatedBytes=total;
    }

    /** Validates sealed edges in this single-directory, unnamed-module application profile.
     * @param child actual direct subtype
     * @param parent declaring superclass/interface
     * @throws BytecodeLoadException for an unlisted subtype or a nonpublic cross-package subtype */
    private void permittedSubtype(String child,String parent) {
        var permitted=hierarchy().metadata(parent).findAttribute(Attributes.permittedSubclasses());
        if(permitted.isPresent() && ((classes.get(child).flags().flagsMask() & ACC_PUBLIC)==0 && !packageName(child).equals(packageName(parent))
            || permitted.get().permittedSubclasses().stream().noneMatch(type->type.asInternalName().equals(child))))
            throw problem("LINKAGE","Subtype is not permitted by sealed parent "+parent,null);
    }

    /**
     * Reads one application class under directory handles, with bounded memory and no symlink traversal.
     * @param name internal class name
     * @return parsed class model
     * @throws IOException for unavailable input
     */
    private ClassModel read(String name) throws IOException {
        if (classes.containsKey(name)) return classes.get(name);
        if(BytecodePlatform.admitted(name) && !name.equals(model))return BytecodePlatform.metadata().get(name);
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
        if ((type.flags().flagsMask() & (ACC_ANNOTATION|ACC_ENUM|ACC_MODULE))!=0)
            throw problem("UNSUPPORTED","Unsupported application class kind",null);
        annotationPlacement(type,name.equals(model) ? Set.of("Model") : Set.of());
        classes.put(name,type); bytes.put(name,input);
        String parent=type.superclass().orElseThrow(()->problem("MALFORMED","Application class requires a superclass",null)).asInternalName();
        if (!parent.equals("java/lang/Object")) read(parent);
        for(var face:type.interfaces())read(face.asInternalName());
        var nest=type.findAttribute(Attributes.nestHost());
        if(nest.isPresent())read(nest.get().nestHost().asInternalName());
        for (FieldModel field : type.fields()) {
            visibility(field.flags().flagsMask(),name+"."+field.fieldName().stringValue());
            annotationPlacement(field,Set.of());
            String descriptor=field.fieldType().stringValue(); fieldType(field.fieldTypeSymbol());
            var id=new BytecodeProgram.FieldId(name,field.fieldName().stringValue(),descriptor);
            BytecodeState.Value initial=descriptor.equals("I") || descriptor.equals("Z") ? new BytecodeState.Int(0) : BytecodeState.NULL;
            var constant=field.findAttribute(Attributes.constantValue());
            if (constant.isPresent() && (field.flags().flagsMask() & ACC_STATIC)!=0) {
                Object value=constant.get().constant().constantValue();
                if(value instanceof Integer number)initial=new BytecodeState.Int(descriptor.equals("Z") ? number & 1 : number);
                else if(value instanceof String text && descriptor.equals("Ljava/lang/String;"))initial=new BytecodeState.Literal(text);
                else throw problem("UNSUPPORTED","Unsupported executable ConstantValue type",null);
            }
            if (fields.putIfAbsent(id,new BytecodeProgram.Field(id,field.flags().flagsMask(),initial))!=null)
                throw problem("MALFORMED","Duplicate field",null);
        }
        for (MethodModel method : type.methods()) {
            visibility(method.flags().flagsMask(),id(name,method).toString());
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
     * Rejects conflicting member visibility bits not checked by the JDK verifier.
     * @param flags raw field or method flags
     * @param declaration diagnostic member identity
     * @throws BytecodeLoadException with MALFORMED when more than one visibility bit is set
     */
    private static void visibility(int flags,String declaration) {
        if(Integer.bitCount(flags & (ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED))>1)
            throw problem("MALFORMED","Conflicting member visibility: "+declaration,null);
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
        if(type.isArray()) {
            while(type.isArray())type=type.componentType();
            if(type.isPrimitive())return;
            fieldType(type);return;
        }
        if (descriptor.equals("I") || descriptor.equals("Z")) return;
        if (!type.isClassOrInterface()) throw problem("UNSUPPORTED","Only int/boolean/reference computation is supported",null);
        String owner=descriptor.substring(1,descriptor.length()-1);
        if (!owner.equals("java/lang/Object")) read(owner);
    }

    /**
     * Checks all parameter and return types against the admitted computation profile.
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
     * Checks resolved member visibility without host linkage. Symbolic class visibility is checked
     * separately so inherited public members of nonpublic superclasses remain accessible.
     * @param caller accessing class
     * @param owner declaring class
     * @param flags member flags
     * @param location calling BCI
     * @throws BytecodeLoadException with LINKAGE for an inaccessible class or member
     */
    private void access(String caller,String owner,int flags,BytecodeLocation location) {
        boolean samePackage=packageName(caller).equals(packageName(owner));
        boolean allowed=(flags & ACC_PUBLIC)!=0 || caller.equals(owner)
            || (flags & ACC_PRIVATE)!=0 && nestHost(caller).equals(nestHost(owner))
            || (flags & ACC_PRIVATE)==0 && (samePackage || (flags & ACC_PROTECTED)!=0 && hierarchy().assignable(caller,owner));
        if(!allowed)
            throw problem("LINKAGE","Inaccessible member",location);
    }

    /** Checks symbolic class visibility independently of inherited member declaration visibility.
     * @param caller accessing class
     * @param owner symbolic class owner
     * @param location real reference position
     * @throws BytecodeLoadException with LINKAGE for an inaccessible symbolic type */
    private void classAccess(String caller,String owner,BytecodeLocation location) {
        if((hierarchy().metadata(owner).flags().flagsMask() & ACC_PUBLIC)==0 && !packageName(caller).equals(packageName(owner)))
            throw problem("LINKAGE","Inaccessible symbolic class",location);
    }

    /** Validates reciprocal same-package nest membership before granting private access.
     * @param member potential nest member
     * @return validated nest host
     * @throws BytecodeLoadException with LINKAGE for invalid or nonreciprocal nest metadata */
    private String nestHost(String member) {
        var type=classes.get(member);var host=type.findAttribute(Attributes.nestHost());
        if(host.isEmpty())return member;
        String name=host.get().nestHost().asInternalName();var root=classes.get(name);
        if(root==null || !packageName(member).equals(packageName(name)) || root.findAttribute(Attributes.nestHost()).isPresent()
            || root.findAttribute(Attributes.nestMembers()).stream().flatMap(a->a.nestMembers().stream())
                .noneMatch(c->c.asInternalName().equals(member)))throw problem("LINKAGE","Invalid nest membership",null);
        return name;
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
        String name=descriptor.descriptorString();name=name.substring(1,name.length()-1);
        var type=hierarchy().metadata(name);
        if((type.flags().flagsMask() & ACC_INTERFACE)!=0)return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
        return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(type.superclass().map(c->c.asSymbol()).orElse(null));
    }

    /**
     * Hashes the frozen class manifest and explicit intrinsic-profile version.
     * @return length-prefixed exact-byte manifest identity with the internal profile version
     */
    private String digest() {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            var sink=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest));
            sink.writeUTF(BytecodeProgram.PROFILE_VERSION); sink.writeInt(bytes.size());
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
