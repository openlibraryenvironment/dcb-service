package org.olf.reshare.dcb.utils;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Hooks;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ReactorDebugEventListener implements ApplicationEventListener<StartupEvent> {

	private final Environment environment;
	private static final String REACTOR_DEBUG_VAR = "REACTOR_DEBUG";

        private static Logger log = LoggerFactory.getLogger(ReactorDebugEventListener.class);

	public ReactorDebugEventListener(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		if ( environment.getProperty(REACTOR_DEBUG_VAR, String.class).orElse("false").equalsIgnoreCase("true") ) {
			log.info("Switching on operator debug mode");
			Hooks.onOperatorDebug();
		}
		else {
			log.info("operator debug not enabled");
		}
	}
}
