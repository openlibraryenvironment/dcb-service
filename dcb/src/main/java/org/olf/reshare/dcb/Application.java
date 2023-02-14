package org.olf.reshare.dcb;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;


@OpenAPIDefinition(info = @Info(title = "Reshare - DCB", description = "Direct Consortial Borrowing Service", version = "1.0.0"))
public class Application {

	public static void main(String[] args) {
		Micronaut.run(Application.class, args);

	}
}
