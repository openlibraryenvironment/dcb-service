package org.olf.dcb.request.workflow;

import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * A workflow action can be executed in different contexts. Executing the action should return the context, possibly modified.
 * If the result of the execution results in  a state change, we should emit an event so that the state model can be updated appropriately.
 */
public interface WorkflowAction {

        /**
         *
         */
        public Mono<Map<String,Object>> execute(Map<String,Object> context);
}

