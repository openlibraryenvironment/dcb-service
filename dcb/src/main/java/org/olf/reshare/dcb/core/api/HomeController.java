package org.olf.reshare.dcb.core.api;

import static io.micronaut.http.MediaType.TEXT_PLAIN;
import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;

import java.security.Principal;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Testing purposes
 */

@Tag(name = "Management API")
@Secured(IS_AUTHENTICATED)
@Controller
public class HomeController {

	@Produces(TEXT_PLAIN)
	@Get
	public String index(Principal principal) {
		return principal.getName();
	}
}
