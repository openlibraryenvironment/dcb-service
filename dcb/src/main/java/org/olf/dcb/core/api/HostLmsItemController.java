package org.olf.dcb.core.api;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.HttpResponse.serverError;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;
import static org.olf.dcb.item.availability.AvailabilityReport.emptyReport;

import java.time.Duration;
import java.util.UUID;

import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.item.availability.AvailabilityResponseView;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;
import org.olf.dcb.request.resolution.NoBibsForClusterRecordException;
import org.olf.dcb.security.RoleNames;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import io.micronaut.core.annotation.Nullable;

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
