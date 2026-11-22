/** Conservative transitive sharedness with irreversible history and measured traversal work. */
package dev.samhb.interleave.bytecode;

import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Proves exclusivity from actor roots; publication promotes entire reachable graphs atomically. */
final class BytecodeSharing {
    /** Prevents construction of this stateless traversal. */
    private BytecodeSharing() {}

    /** Actual sharing work outside canonical state/resource allowances.
     * @param traversals refresh operations
     * @param objects object visits across root traversals
     * @param edges reference edges visited
     * @param promotions irreversible false-to-true changes */
    record Work(long traversals,long objects,long edges,long promotions) {
        /** Empty sharing work. */
        static final Work NONE=new Work(0,0,0,0);
        /** Adds instrumentation without modifying target state.
         * @param other additional work
         * @return combined measurements */
        Work plus(Work other){return new Work(traversals+other.traversals,objects+other.objects,edges+other.edges,promotions+other.promotions);}
    }

    /** Recomputes reachability after reference writes/allocations, retaining historical promotions.
     * Shared roots include historically shared objects even after their last public alias disappears.
     * A fresh actor allocation is exclusively on that actor's stack; the executor establishes its
     * initial locality. Unreachable objects cannot regain an alias without passing through a traced
     * heap edge. Thus losing a last local root cannot create a new cross-actor path between refreshes.
     * @param state selected owned successor
     * @return actual traversal/edge/promotion counts */
    static Work refresh(BytecodeState state) {
        var roots=new TreeMap<Integer,List<Ref>>();var shared=new ArrayList<Ref>();roots.put(SETUP,shared);
        shared.add(FIXTURE);state.statics.values().forEach(value->root(shared,value));
        state.heap.forEach((reference,object)->{if(object.shared)shared.add(reference);});
        state.executions.forEach((owner,execution)->{
            if(owner<0)return;var actor=new ArrayList<Ref>();roots.put(owner,actor);root(actor,execution.returned);root(actor,execution.throwable);
            if(execution.faultPayload!=null){root(actor,execution.faultPayload.message());root(actor,execution.faultPayload.cause());}
            if(execution.intrinsic!=null) {
                root(actor,execution.intrinsic.receiver());execution.intrinsic.arguments().forEach(value->root(actor,value));root(actor,execution.intrinsic.result());
            }
            for(var frame:execution.frames) {
                frame.locals.values().forEach(value->root(actor,value));frame.stack.forEach(value->root(actor,value));root(actor,frame.methodMonitor);root(actor,frame.constructorReceiver);
            }
        });
        var reached=new HashMap<Ref,Set<Integer>>();long objects=0,edges=0,promotions=0;
        for(var entry:roots.entrySet()) {
            var pending=new ArrayDeque<>(entry.getValue());var seen=new HashSet<Ref>();
            while(!pending.isEmpty()) {
                Ref reference=pending.removeFirst();if(!seen.add(reference))continue;
                var object=state.heap.get(reference);if(object==null)continue;
                objects++;reached.computeIfAbsent(reference,ignored->new HashSet<>()).add(entry.getKey());
                for(Value value:object.fields.values())if(value instanceof Ref target && !target.equals(NULL)){edges++;pending.add(target);}
                if(object.elements!=null)for(Value value:object.elements)if(value instanceof Ref target && !target.equals(NULL)){edges++;pending.add(target);}
            }
        }
        var promoted=new ArrayDeque<Ref>();
        for(var entry:state.heap.entrySet()) {
            var owners=reached.get(entry.getKey());
            boolean exclusive=owners!=null && owners.size()==1 && !owners.contains(SETUP);
            if(!exclusive && !entry.getValue().shared){
                entry.getValue().shared=true;promotions++;
                // Reached shared/multi-actor descendants were already classified transitively above.
                if(owners==null)promoted.add(entry.getKey());
            }
        }
        // Unknown ownership can promote an unreachable parent whose child still has an actor root.
        // Close those promotions before any observer sees a shared-to-private edge.
        while(!promoted.isEmpty()) {
            var object=state.heap.get(promoted.removeFirst());objects++;
            var children=new ArrayList<Value>(object.fields.values());if(object.elements!=null)children.addAll(object.elements);
            for(Value value:children)if(value instanceof Ref reference && !reference.equals(NULL)) {
                edges++;var child=state.heap.get(reference);
                if(child!=null && !child.shared){child.shared=true;promotions++;promoted.add(reference);}
            }
        }
        return new Work(1,objects,edges,promotions);
    }

    /** Adds only nonnull target reference roots.
     * @param roots root list
     * @param value interpreted slot */
    private static void root(List<Ref> roots,Value value) {
        if(value instanceof Ref reference && !reference.equals(NULL))roots.add(reference);
    }
}
