package org.olf.reshare.dcb;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;

import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;
import static io.micronaut.http.MediaType.TEXT_PLAIN;

@Secured(IS_AUTHENTICATED)
@Controller("/dcb")
public class DcbController {

  @Get(uri = "/", produces = TEXT_PLAIN)
  public String index () {
    return "Example Response";
  }
}