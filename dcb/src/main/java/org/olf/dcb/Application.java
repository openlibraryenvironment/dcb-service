package org.olf.dcb;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;

import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@TypeHint(value = { Instant[].class, ZonedDateTime[].class, URI[].class, URL[].class })
@OpenAPIDefinition(info = @Info(title = "DCB", description = "Direct Consortial Borrowing Service", version = "1.0.0"))
public class Application {

	public static void main(String[] args) {
		Micronaut.run(Application.class, args);
	}
}
