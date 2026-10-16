package org.olf.dcb.observability;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.security.OpenToAllPrincipals;
import org.olf.dcb.test.DcbTest;
import org.slf4j.Logger;
import org.slf4j.MDC;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.annotation.Secured;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
@Property(name = "spec.name", value = "RequestIdFilterTests")
class RequestIdFilterTests {

	private static final String PROBE = "/test/request-id";

	@Inject
	@Client("/")
	HttpClient client;

	@Test
	void echoesTheCallersId() {
		final var response = client.toBlocking()
			.exchange(HttpRequest.GET(PROBE).header(RequestId.HEADER, "ui-7f3a21"), String.class);

		assertThat(response.getHeaders().get(RequestId.HEADER), is("ui-7f3a21"));
	}

	@Test
	void generatesAnIdWhenNoneIsSent() {
		final var response = client.toBlocking().exchange(HttpRequest.GET(PROBE), String.class);

		assertThat(response.getHeaders().get(RequestId.HEADER), matchesRegex("[0-9a-f]{12}"));
	}

	@Test
	void replacesAnIdThatCouldForgeALogLine() {
		assertThat(RequestId.resolve("aaa\nWARN fake line"), not(containsString("\n")));
		assertThat(RequestId.resolve("has a space"), matchesRegex("[0-9a-f]{12}"));
		assertThat(RequestId.resolve("x".repeat(65)), matchesRegex("[0-9a-f]{12}"));
	}

	@Test
	void theIdIsInTheLogContextAfterTheRequestChangesThread() {
		final var body = client.toBlocking()
			.retrieve(HttpRequest.GET(PROBE).header(RequestId.HEADER, "hop-1"), Map.class);

		assertThat(body.get("mdc"), is("hop-1"));
		assertThat(body.get("sameThread"), is(false));
	}

	@Test
	void theJsonAppenderDeploymentsSelectCarriesTheId() throws Exception {
		// STDOUT (JSON) has no pattern to edit, so read what the shipped logback.xml emits.
		final var context = new LoggerContext();
		context.putProperty("DCB_LOG_APPENDERS", "STDOUT");
		final var configurator = new JoranConfigurator();
		configurator.setContext(context);
		// The file, not the resource: the test classpath's own logback.xml shadows it.
		configurator.doConfigure(Path.of("src/main/resources/logback.xml").toFile());

		final var stdout = (AsyncAppender) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("STDOUT");
		final var json = (OutputStreamAppender<ILoggingEvent>) stdout.getAppender("JSON_SYNC");

		try {
			final var event = new LoggingEvent("test", context.getLogger("test"), Level.INFO, "message", null, null);
			event.setMDCPropertyMap(Map.of(RequestId.MDC_KEY, "json-1"));

			assertThat(new String(json.getEncoder().encode(event), StandardCharsets.UTF_8),
				containsString("\"requestId\":\"json-1\""));
		} finally {
			context.stop();
		}
	}

	@Controller
	@Requires(property = "spec.name", value = "RequestIdFilterTests")
	@Secured(IS_ANONYMOUS)
	static class Probe {

		@Get(PROBE)
		@OpenToAllPrincipals(justification =
			"Test-only: exists only under spec.name RequestIdFilterTests and returns the caller's own header")
		Mono<Map<String, Object>> probe() {
			final var before = Thread.currentThread();

			return Mono.delay(Duration.ofMillis(5)).map(tick -> Map.of(
				"mdc", String.valueOf(MDC.get(RequestId.MDC_KEY)),
				"sameThread", Thread.currentThread() == before));
		}
	}
}
