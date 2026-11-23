/** Frozen metadata-only member resolution and reference assignability for the closed heap profile. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.ClassModel;
import java.util.*;
import static java.lang.classfile.ClassFile.*;
import static dev.samhb.interleave.bytecode.BytecodeLoader.problem;

/** Resolves application types without loading target classes or consulting the host classpath. */
final class BytecodeHierarchy {
    /** Frozen class declarations, including interfaces. */
    final Map<String,ClassModel> types;
    /** Complete field declarations. */
    private final Map<BytecodeProgram.FieldId,BytecodeProgram.Field> fields;

    /** Pure, bounded derived metadata shared safely by independent runs of this frozen hierarchy. */
    private final Map<String,Set<String>> ancestorCache=new java.util.concurrent.ConcurrentHashMap<>();

    /** Freezes the closed metadata used by loading and execution.
     * @param types application metadata
     * @param fields complete field declarations */
    BytecodeHierarchy(Map<String,ClassModel> types,Map<BytecodeProgram.FieldId,BytecodeProgram.Field> fields) {
        var complete=new HashMap<>(BytecodePlatform.metadata());complete.putAll(types);
        this.types=Map.copyOf(complete);this.fields=Map.copyOf(fields);
    }

    /** Returns the direct superclass, or null for the trusted Object root.
     * @param type internal application name
     * @return superclass internal name */
    String parent(String type) {
        if(type.equals("java/lang/Object"))return null;
        return metadata(type).superclass().map(c->c.asInternalName()).orElse(null);
    }

    /** Requires explicit application metadata.
     * @param type internal name
     * @return frozen declaration
     * @throws BytecodeLoadException with METADATA when the application type is absent */
    ClassModel metadata(String type) {
        var value=types.get(type);
        if(value==null)throw problem("METADATA","Unknown hierarchy type "+type,null);
        return value;
    }

    /** Tests JVM reference assignability, retaining exact array component/rank information.
     * @param actual runtime internal name or array descriptor
     * @param expected requested internal name or array descriptor
     * @return whether the reference can inhabit the requested type */
    boolean assignable(String actual,String expected) {
        if(actual.equals(expected) || expected.equals("java/lang/Object"))return true;
        if(actual.startsWith("[")) {
            if(expected.equals("java/lang/Cloneable") || expected.equals("java/io/Serializable"))return true;
            if(!expected.startsWith("["))return false;
            String a=actual.substring(1),b=expected.substring(1);
            if(primitive(a) || primitive(b))return a.equals(b);
            return assignable(referenceType(a),referenceType(b));
        }
        if(expected.startsWith("[") || actual.equals("java/lang/Object"))return false;
        return ancestors(actual).contains(expected);
    }

    /** Returns whether a component descriptor denotes a primitive scalar.
     * @param descriptor component descriptor
     * @return primitive scalar */
    private static boolean primitive(String descriptor){return descriptor.length()==1;}

    /** Converts a reference descriptor to an internal name or preserves an array descriptor.
     * @param descriptor reference descriptor
     * @return runtime type name */
    static String referenceType(String descriptor) {
        return descriptor.startsWith("[") ? descriptor : descriptor.substring(1,descriptor.length()-1);
    }

    /** Reuses immutable supertype closures; cycles remain checked separately during loading.
     * @param type application name
     * @return all reachable ancestors including self */
    Set<String> ancestors(String type) {return ancestorCache.computeIfAbsent(type,this::collectAncestors);}

    /** Computes one bounded closure without recursively mutating the metadata cache.
     * @param type requested class or interface
     * @return immutable ancestors including self
     * @throws BytecodeLoadException for missing hierarchy metadata */
    private Set<String> collectAncestors(String type) {
        var seen=new HashSet<String>();var pending=new ArrayDeque<String>();pending.add(type);
        while(!pending.isEmpty()) {
            String name=pending.removeFirst();if(!seen.add(name) || name.equals("java/lang/Object"))continue;
            var declaration=metadata(name);declaration.superclass().ifPresent(c->pending.add(c.asInternalName()));
            declaration.interfaces().forEach(c->pending.add(c.asInternalName()));
        }
        return Collections.unmodifiableSet(seen);
    }

    /** Resolves a symbolic field in JVM class/interface/superclass order.
     * @param id symbolic field identity
     * @return declaring field identity */
    BytecodeProgram.FieldId field(BytecodeProgram.FieldId id) {
        return field(id,new HashSet<>());
    }

    /** Searches a bounded field hierarchy without recursive cycles.
     * @param id current symbolic owner/name/descriptor
     * @param seen visited owners
     * @return declaring identity, or null for no declaration */
    private BytecodeProgram.FieldId field(BytecodeProgram.FieldId id,Set<String> seen) {
        if(!seen.add(id.owner()) || id.owner().equals("java/lang/Object"))return null;
        if(fields.containsKey(id))return id;
        var type=metadata(id.owner());
        for(var face:type.interfaces()) {
            var found=field(new BytecodeProgram.FieldId(face.asInternalName(),id.name(),id.descriptor()),seen);
            if(found!=null)return found;
        }
        String parent=parent(id.owner());
        return parent==null ? null : field(new BytecodeProgram.FieldId(parent,id.name(),id.descriptor()),seen);
    }

    /** Finds a declared method's flags without admitting or executing its code.
     * @param id complete declared identity
     * @return flags or -1 if absent */
    int flags(BytecodeMethod.Id id) {
        if(id.owner().equals("java/lang/Object"))return -1;
        return metadata(id.owner()).methods().stream().filter(m->m.methodName().equalsString(id.name())
            && m.methodType().equalsString(id.descriptor())).mapToInt(m->m.flags().flagsMask()).findFirst().orElse(-1);
    }

    /** Resolves class methods then maximally specific interface declarations.
     * @param owner lookup class/interface
     * @param name method name
     * @param descriptor JVM method descriptor
     * @return declaration or null when no method is declared or inherited
     * @throws BytecodeLoadException with METADATA for absent type metadata or LINKAGE for a
     * superclass cycle or conflicting maximally specific defaults */
    BytecodeMethod.Id method(String owner,String name,String descriptor) {
        var seen=new HashSet<String>();
        for(String next=owner;next!=null && !next.equals("java/lang/Object");next=parent(next)) {
            if(!seen.add(next))throw problem("LINKAGE","Cyclic superclass hierarchy",null);
            var id=new BytecodeMethod.Id(next,name,descriptor);
            if(flags(id)>=0)return id;
            if(name.equals("<init>"))return null;
        }
        return interfaceMethod(owner,name,descriptor);
    }

    /** Selects only maximally specific interface declarations, without restarting a class lookup.
     * @param owner runtime class or interface
     * @param name method name
     * @param descriptor JVM descriptor
     * @return default/abstract interface declaration, or null when none exists
     * @throws BytecodeLoadException with METADATA for absent type metadata or LINKAGE for
     * conflicting maximally specific defaults */
    private BytecodeMethod.Id interfaceMethod(String owner,String name,String descriptor) {
        var candidates=new ArrayList<BytecodeMethod.Id>();
        for(String next:ancestors(owner)) {
            if(next.equals("java/lang/Object") || (metadata(next).flags().flagsMask() & ACC_INTERFACE)==0)continue;
            var id=new BytecodeMethod.Id(next,name,descriptor);int flags=flags(id);
            if(flags>=0 && (flags & (ACC_STATIC|ACC_PRIVATE))==0)candidates.add(id);
        }
        var maximal=candidates.stream().filter(a->candidates.stream().noneMatch(b->!a.equals(b)
            && assignable(b.owner(),a.owner()))).toList();
        var defaults=maximal.stream().filter(a->(flags(a)&ACC_ABSTRACT)==0).toList();
        if(defaults.size()>1)throw problem("LINKAGE","Conflicting interface defaults "+defaults,null);
        return defaults.isEmpty() ? maximal.stream().sorted(Comparator.comparing(BytecodeMethod.Id::owner)).findFirst().orElse(null) : defaults.getFirst();
    }

    /** Selects a virtual target only when a declaration overrides the resolved member.
     * Package-private methods in another package cannot be captured by same-named child methods.
     * @param actual runtime class
     * @param resolved symbolic resolved declaration
     * @return selected concrete or abstract declaration */
    BytecodeMethod.Id dispatch(String actual,BytecodeMethod.Id resolved) {
        for(String type=actual;type!=null && !type.equals("java/lang/Object");type=parent(type)) {
            var candidate=new BytecodeMethod.Id(type,resolved.name(),resolved.descriptor());int flags=flags(candidate);
            if(flags>=0 && (flags & (ACC_PRIVATE|ACC_STATIC))==0 && overrides(candidate,resolved))return candidate;
        }
        return interfaceMethod(actual,resolved.name(),resolved.descriptor());
    }

    /** Tests overriding including transitive widening of package-private declarations.
     * @param candidate descendant declaration
     * @param base resolved ancestor declaration
     * @return JVM overriding relationship */
    private boolean overrides(BytecodeMethod.Id candidate,BytecodeMethod.Id base) {
        if(candidate.equals(base))return true;
        int access=flags(base);
        if((access & (ACC_PUBLIC|ACC_PROTECTED))!=0)return true;
        // Each superclass is inspected once, from base to candidate. A declaration can widen
        // an accessible package-private member; a same-named inaccessible member cannot do so.
        var chain=new ArrayList<String>();
        for(String type=candidate.owner();type!=null && !type.equals("java/lang/Object") && !type.equals(base.owner());type=parent(type))chain.add(type);
        var inherited=new ArrayList<BytecodeMethod.Id>();inherited.add(base);
        for(String type:chain.reversed()) {
            var declared=new BytecodeMethod.Id(type,base.name(),base.descriptor());int flags=flags(declared);
            if(flags<0 || (flags & (ACC_STATIC|ACC_PRIVATE))!=0)continue;
            if(inherited.stream().anyMatch(prior->(flags(prior) & (ACC_PUBLIC|ACC_PROTECTED))!=0
                || packageName(type).equals(packageName(prior.owner()))))inherited.add(declared);
        }
        return inherited.contains(candidate);
    }

    /** Selects a validated super invocation under ACC_SUPER semantics.
     * @param caller calling class
     * @param symbolic symbolic owner
     * @param resolved resolved member
     * @return selected instance declaration, or null when no target exists
     * @throws BytecodeLoadException with METADATA for absent type metadata or LINKAGE for an
     * invalid special owner or conflicting defaults; caller supplies a validated acyclic hierarchy */
    BytecodeMethod.Id special(String caller,String symbolic,BytecodeMethod.Id resolved) {
        if(resolved.name().equals("<init>") || (flags(resolved)&ACC_PRIVATE)!=0)return resolved;
        if((metadata(symbolic).flags().flagsMask() & ACC_INTERFACE)!=0) {
            if(metadata(caller).interfaces().stream().noneMatch(c->c.asInternalName().equals(symbolic)))
                throw problem("LINKAGE","Special interface call requires a direct superinterface",null);
            return resolved;
        }
        if(!caller.equals(symbolic) && !assignable(caller,symbolic))throw problem("LINKAGE","Special call requires current class or superclass",null);
        if(caller.equals(symbolic))return resolved;
        String start=parent(caller);
        for(String next=start;next!=null && !next.equals("java/lang/Object");next=parent(next)) {
            var candidate=new BytecodeMethod.Id(next,resolved.name(),resolved.descriptor());int flags=flags(candidate);
            if(flags>=0 && (flags & ACC_STATIC)==0)return candidate;
        }
        return interfaceMethod(start,resolved.name(),resolved.descriptor());
    }

    /** Extracts an internal class package for JVM inheritance rules.
     * @param name internal class name
     * @return package prefix */
    private static String packageName(String name){int slash=name.lastIndexOf('/');return slash<0 ? "" : name.substring(0,slash);}

    /** Returns all inherited instance fields, preserving declaring identities for hidden fields.
     * @param type concrete runtime class
     * @return deterministic field declarations */
    List<BytecodeProgram.Field> instanceFields(String type) {
        var ancestors=ancestors(type);
        return fields.values().stream().filter(f->(f.flags() & ACC_STATIC)==0 && ancestors.contains(f.id().owner()))
            .sorted(Comparator.comparing(BytecodeProgram.Field::id)).toList();
    }
}
