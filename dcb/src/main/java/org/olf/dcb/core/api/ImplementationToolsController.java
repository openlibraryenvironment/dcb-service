package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.security.RoleNames;
import org.olf.dcb.interops.*;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

import jakarta.validation.constraints.NotNull;

@Controller("/imps")
@Validated
@Secured(RoleNames.INTEROP_TESTER)
@Tag(name = "Implementation Tools API")
public class ImplementationToolsController {

	private final InteropTestService interopTestService;

	public ImplementationToolsController(InteropTestService interopTestService) {
		this.interopTestService = interopTestService;
	}

  @Get(uri = "/interopTest", produces = APPLICATION_JSON)
  public Flux<InteropTestResult> interopTest(@NotNull @QueryValue String code) {
    return interopTestService.testIls(code);
  }


	public Mono<InteropTestResult> createPatronTest() {
		return Mono.just(InteropTestResult.builder().build());
	}
}
