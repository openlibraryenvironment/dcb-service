package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.time.Duration;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.security.RoleNames;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Controller
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Host Lms Item")
public class HostLmsItemController {

  @Value("${dcb.live-availability.timeout:PT7S}")
  protected Duration timeout;

  public HostLmsItemController() {
  }

  @Get(uri = "/items/{ilsCode}/{itemId}", produces = APPLICATION_JSON)
  public Mono<HostLmsItem> getItem(String ilsCode, String itemId) {
    log.info("getItem {} {}", ilsCode,itemId);
		return Mono.just(HostLmsItem.builder().build());
  }

}
