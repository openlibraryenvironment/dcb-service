package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import io.micronaut.http.annotation.PathVariable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotBlank;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.interops.*;
import org.olf.dcb.core.svc.HouseKeepingService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.olf.dcb.core.interaction.PingResponse;

import jakarta.validation.constraints.NotNull;

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

	// Define the enum
	public enum ConfigType {
		LOCATIONS("locations");

		private final String value;

		ConfigType(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static ConfigType fromString(String value) {
			for (ConfigType type : ConfigType.values()) {
				if (type.value.equalsIgnoreCase(value)) {
					return type;
				}
			}
			throw new IllegalArgumentException("Invalid config type: " + value);
		}
	}

	@Operation(
		summary = "Retrieve configuration for a host LMS system",
		description = "Fetches configuration data for a specific host LMS system based on the system code and configuration type"
	)
	@Get(uri = "/systems/{systemCode}/config/{configType}", produces = APPLICATION_JSON)
	public Mono<InteropTestResult> getConfiguration(
		@Parameter(description = "Host LMS system code", required = true)
		@PathVariable
		@NotBlank(message = "System code cannot be blank")
		String systemCode,

		@Parameter(description = "Configuration type", required = true)
		@PathVariable
		String configType) {

		ConfigType validatedType = ConfigType.fromString(configType);

		return interopTestService.retrieveConfiguration(systemCode, validatedType.getValue());
	}

	public Mono<InteropTestResult> createPatronTest() {
		return Mono.just(InteropTestResult.builder().build());
	}
}
