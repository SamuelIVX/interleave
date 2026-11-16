/** Conservative transitive sharing for the setup-only heap, with actual traversal instrumentation. */
package dev.samhb.interleave.bytecode;

import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Never establishes actor-exclusive heap ownership in94B; unknown and historical sharing stay shared. */
final class BytecodeSharing {
    /** Prevents construction of the stateless sharing traversal. */
    private BytecodeSharing() {}

    /** Actual sharedness work, outside the model's canonical state and resource counters.
     * @param traversals refresh operations
     * @param objects distinct heap objects processed across refreshes
     * @param edges nonnull reference-field edges visited from roots
     * @param promotions false-to-true historical sharing changes
     */
    record Work(long traversals,long objects,long edges,long promotions) {
        /** Empty work for transitions which do not change reference reachability. */
        static final Work NONE=new Work(0,0,0,0);
        /** Combines actual work without changing target state.
         * @param other additional traversal work
         * @return summed counters */
        Work plus(Work other){return new Work(traversals+other.traversals,objects+other.objects,edges+other.edges,promotions+other.promotions);}
    }

    /** Traverses fixture/static/actor roots and conservatively promotes unresolved ownership.
     * The initial profile has setup-only objects; no heap access is batched as actor-local.
     * @param state owned scratch, never an unselected committed parent
     * @return actual object/edge work and irreversible promotions
     */
    static Work refresh(BytecodeState state) {
        var queue=new ArrayDeque<Ref>();queue.add(FIXTURE);
        for(var value:state.statics.values())enqueue(queue,value);
        for(var entry:state.executions.entrySet()) {
            if(entry.getKey()<0)continue;
            var execution=entry.getValue();enqueue(queue,execution.returned);
            for(var frame:execution.frames) {
                for(var value:frame.locals.values())enqueue(queue,value);
                for(var value:frame.stack)enqueue(queue,value);
                enqueue(queue,frame.methodMonitor);
            }
        }
        var seen=new HashSet<Ref>();long objects=0,edges=0,promotions=0;
        while(!queue.isEmpty()) {
            var reference=queue.removeFirst();if(!seen.add(reference))continue;
            var object=state.heap.get(reference);if(object==null)continue;
            objects++;if(!object.shared){object.shared=true;promotions++;}
            for(var value:object.fields.values()) {
                if(value instanceof Ref target && !target.equals(NULL)){edges++;queue.addLast(target);}
            }
        }
        // shortcut:94B cannot allocate actor-local objects; unresolved setup ownership remains shared until95 analysis.
        for(var entry:state.heap.entrySet()) {
            if(seen.add(entry.getKey())) {
                objects++;if(!entry.getValue().shared){entry.getValue().shared=true;promotions++;}
            }
        }
        return new Work(1,objects,edges,promotions);
    }

    /** Adds actual reference roots, excluding null and non-reference VM metadata.
     * @param queue pending roots
     * @param value target slot or pending value */
    private static void enqueue(ArrayDeque<Ref> queue,Value value) {
        if(value instanceof Ref reference && !reference.equals(NULL))queue.addLast(reference);
    }
}
