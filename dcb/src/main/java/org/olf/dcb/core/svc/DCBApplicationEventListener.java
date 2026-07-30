package org.olf.dcb.core.svc;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.Syslog;
import io.micronaut.context.annotation.Context;

@Context
@Singleton
public class DCBApplicationEventListener implements ApplicationEventListener<Object> {

	private final SyslogService syslogService;

	public DCBApplicationEventListener(SyslogService syslogService) {
		this.syslogService = syslogService;
	}

	@Override
	public void onApplicationEvent(Object event) {

		if ( event instanceof ServerStartupEvent ) {
			syslogService.log(
				Syslog.builder()
					.category("system")
	        .message("Startup")
					.detail("instance", syslogService.getSystemInstanceId())
					.build()
			)
			.block();
		}
		else if ( event instanceof ServerShutdownEvent ) {
			syslogService.log(
				Syslog.builder()
					.category("system")
	        .message("Shutdown")
					.detail("instance", syslogService.getSystemInstanceId())
		      .build()
			)
			.block();
		}
		else {
			// other event type
		}
	}

}
