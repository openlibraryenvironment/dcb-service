package org.olf.reshare.dcb.controller;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import java.security.Principal;

import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;
import static io.micronaut.http.MediaType.TEXT_PLAIN;

/**
 * 
 * Testing purposes
 *
 */
@Secured(IS_AUTHENTICATED)
@Controller
public class HomeController {

	@Produces(TEXT_PLAIN)
	@Get
	public String index(Principal principal) {
		return principal.getName();
	}
}