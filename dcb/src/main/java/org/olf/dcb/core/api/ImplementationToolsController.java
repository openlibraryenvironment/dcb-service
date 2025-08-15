package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.interops.*;
import org.olf.dcb.core.svc.HouseKeepingService;

import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.olf.dcb.core.interaction.PingResponse;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Controller("/imps")
@Validated
@Secured({RoleNames.INTEROP_TESTER, RoleNames.ADMINISTRATOR})
@Tag(name = "Implementation Tools API")
public class ImplementationToolsController {

	private final InteropTestService interopTestService;
	private final HouseKeepingService houseKeepingService;

	public ImplementationToolsController(InteropTestService interopTestService, HouseKeepingService houseKeepingService) {
		this.interopTestService = interopTestService;
		this.houseKeepingService = houseKeepingService;
	}

  @Get(uri = "/audit", produces = MediaType.TEXT_PLAIN)
  public Mono<MutableHttpResponse<String>> dedupeMatchPoints() {
    return houseKeepingService
      .initiateAudit()
      .map(HttpResponse.accepted()::<String>body);
  }

  @Get(uri = "/ping", produces = APPLICATION_JSON)
  public Mono<PingResponse> ping(@NotNull @QueryValue String code) {
    return interopTestService.ping(code);
  }

	@Get(uri = "/interopTest", produces = APPLICATION_JSON)
	public Flux<InteropTestResult> interopTest(@NotNull @QueryValue String code,
			@QueryValue(defaultValue = "false") boolean forceCleanup) {
		return interopTestService.testIls(code, forceCleanup);
	}

	private static final String NO_STORE = "no-store";


	/**
	 * Find the first item matching the supplied conditions by scanning bibs (no repeats).
	 * Returns:
	 *  - 200 with Item if found
	 *  - 404 if none matched within the time budget
	 *  - 408 if the operation timed out
	 *  - 400 for validation/other client errors
	 */
	@Post(uri = "/items/find/{code}", consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
	public Mono<MutableHttpResponse<Object>> findItemPost(@PathVariable @NotNull String code,
		@QueryValue(defaultValue = "50") int pageSize,
		@QueryValue(defaultValue = "600") long timeoutSeconds,
		@Body @Nullable ItemQuery query) {

		int ps = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
		long ts = timeoutSeconds <= 0 ? 600 : Math.min(timeoutSeconds, 1200);
		var timeout = Duration.ofSeconds(ts);
		var q = query == null ? new ItemQuery() : query;

		return interopTestService.findFirstMatchingItemList(code, q, ps, timeout)
			.map(list -> HttpResponse.<Object>ok(list))
			.switchIfEmpty(Mono.defer(() -> Mono.just(
				HttpResponse.<Object>status(HttpStatus.NOT_FOUND)
					.body(new ApiError("NotFound", "No item matched the given conditions"))
			)))
			.onErrorResume(TimeoutException.class, e ->
				Mono.just(HttpResponse.<Object>status(HttpStatus.REQUEST_TIMEOUT)
					.body(new ApiError("Timeout", e.getMessage()))))
			.onErrorResume(e ->
				Mono.just(HttpResponse.<Object>badRequest(new ApiError(
					e.getClass().getSimpleName(),
					e.getMessage() != null ? e.getMessage() : "Unexpected error"))));
	}

	@Serdeable
	public record ItemQuery(
		@Nullable Boolean available,
		@Nullable Boolean requestable,
		@Nullable String  locationCode,
		@Nullable String  canonicalItemType,
		@Nullable String  itemTypeCode,
		@Nullable Integer minHoldCount,
		@Nullable Integer maxHoldCount,
		@Nullable Boolean notSuppressed,
		@Nullable Boolean notDeleted,
		@Nullable String  agencyCode,
		@Nullable Boolean  callNumberExists
	) {
		public ItemQuery() {
			this(null, null,
				null, null,
				null, null,
				null, null,
				null, null,
				null);
		}
	}

	@Serdeable
	public record ApiError(String type, String message) {}

	@Operation(
		summary = "Retrieve configuration for a host LMS system",
		description = "Fetches configuration data for a specific host LMS system based on the system code and configuration type"
	)
	@Get(uri = "/config/{systemCode}/{configType}", produces = APPLICATION_JSON)
	public Mono<InteropTestResult> getConfiguration(
		@Parameter(description = "Host LMS system code", required = true)
		@PathVariable
		@NotBlank(message = "System code cannot be blank")
		String systemCode,

		@Parameter(description = "Configuration type", required = true)
		@PathVariable
		String configType) {

		ConfigType validatedType = ConfigType.fromString(configType);

		if (validatedType == null) {
			return Mono.just(InteropTestResult.builder()
				.result("ERROR")
				.stage("Invalid configuration type: " + configType)
				.note("Valid configuration types are: " + Arrays.toString(ConfigType.values()))
				.build());
		}

		return interopTestService.retrieveConfiguration(systemCode, validatedType);
	}

	public Mono<InteropTestResult> createPatronTest() {
		return Mono.just(InteropTestResult.builder().build());
	}
}
