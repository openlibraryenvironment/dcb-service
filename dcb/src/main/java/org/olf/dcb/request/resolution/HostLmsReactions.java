package org.olf.dcb.request.resolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.micronaut.context.event.ApplicationEventListener;
import jakarta.inject.Singleton;
import org.olf.dcb.tracking.model.StateChange;

/**
 * This class gathers together the code which detects that an object in a remote system has
 * changed state, and attempts to trigger the appropriate local workflow for dealing with that
 * scenario.
 */
@Singleton
public class HostLmsReactions implements ApplicationEventListener<StateChange> {

        private static final Logger log = LoggerFactory.getLogger(HostLmsReactions.class);


        @Override
        public void onApplicationEvent(StateChange stateChangeEvent) {
                log.debug("onApplicationEvent {}",stateChangeEvent);
        }
        
}

