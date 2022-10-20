package org.olf.reshare.dcb;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/dcb")
public class DcbController {

  @Get(uri = "/", produces = "text/plain")
  public String index () {
    return "Example Response";
  }
}