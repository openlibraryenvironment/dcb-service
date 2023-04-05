package org.olf.reshare.dcb.utils;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Hooks;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

@Singleton
public class ReactorDebugEventListener implements ApplicationEventListener<StartupEvent> {

	private final Environment environment;
	private static final String REACTOR_DEBUG_VAR = "REACTOR_DEBUG";

	public ReactorDebugEventListener(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		if ( environment.containsProperty(REACTOR_DEBUG_VAR) &&
                     environment.getProperty(REACTOR_DEBUG_VAR, String.class).equals("true") ) {
			Hooks.onOperatorDebug();
		}
	}
}
