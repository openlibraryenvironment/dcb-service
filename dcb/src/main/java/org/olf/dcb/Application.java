package org.olf.dcb;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;

import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import services.k_int.bootstrap.SystemInformationSource;

import reactor.blockhound.BlockHound;

@TypeHint(value = { Instant[].class, ZonedDateTime[].class, URI[].class, URL[].class })
@OpenAPIDefinition(info = @Info(title = "DCB", description = "Direct Consortial Borrowing Service", version = "1.0.0"))
public class Application {

  // Ian: Comment this out unless you know what you're up to
  // static {
  //   BlockHound.install();
  // }

	public static void main(String[] args) {
		Micronaut.run(Application.class, args);
	}
}
