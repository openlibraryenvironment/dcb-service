package org.olf.dcb;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@TypeHint(value = { Instant[].class, ZonedDateTime[].class, URI[].class, URL[].class })
@OpenAPIDefinition(info = @Info(title = "DCB", description = "Direct Consortial Borrowing Service", version = "1.0.0"))
public class Application {
	
	private static ApplicationContext currentContext = null;
	
	public static ApplicationContext getCurrentContext() {
		if (currentContext == null) throw new IllegalStateException("No current, running application context");
		
		return currentContext;
	}

	public static void main(String[] args) {
		currentContext = Micronaut.run(Application.class, args);
	}
}
