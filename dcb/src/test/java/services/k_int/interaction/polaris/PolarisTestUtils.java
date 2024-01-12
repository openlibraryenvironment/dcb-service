package services.k_int.interaction.polaris;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.function.Function;

import org.mockserver.client.ForwardChainExpectation;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpHeaderValues;

public interface PolarisTestUtils {
	Logger log = LoggerFactory.getLogger(PolarisTestUtils.class);

	static final int WEIGHT_PRIORITY_LOW = -50;
	static final int WEIGHT_PRIORITY_HIGH = 50;
	
	public static MockPolarisPAPIHost mockFor( MockServerClient mock, String hostname ) {
		return new MockPolarisPAPIHost(mock, hostname);
	}
	
	public static HttpResponse okJson (Object jsonObj) {
		
		return response()
			.withStatusCode(200)
			.withBody(
					json(jsonObj, MediaType.APPLICATION_JSON));
	}
	
	public static class MockPolarisPAPIHost {
		
		final Timer revokers = new Timer("test-token-revoker", true);
		
		final MockServerClient mock;
		final String hostname;
		
		public MockPolarisPAPIHost(MockServerClient mock, String hostname) {
			
			String theHost;
			try {
				URI uri = URI.create(hostname);
				theHost = uri.getHost();
			} catch (IllegalArgumentException e) {
				theHost = hostname;
			}

			this.mock = mock;
			this.hostname = theHost != null ? theHost : hostname;
			//setBaseAssunmptions();
		}
		
		public ForwardChainExpectation whenRequest(Function<HttpRequest,HttpRequest> requestModifier) {
			return mock.when(requestModifier.apply(getRequestDefaults()));
		}
		
		private HttpRequest getRequestDefaults() {
			return request()
				.withHeader("Accept", "application/json")
				.withHeader("host", hostname);
		}
		
		private HttpResponse defaultSuccess() {
			return response()
					.withStatusCode(200)
					.withContentType(MediaType.APPLICATION_JSON);
		}
		
		public MockPolarisPAPIHost setValidCredentials( String basicAuthHash ) {
			
			// Default TTL 3600
			// Default token random string.
			return setValidCredentials( basicAuthHash, UUID.randomUUID().toString(), 3600);
		}
		
		private final List<Expectation> authExp = Collections.synchronizedList(new ArrayList<>(2));
		private boolean addExp(Expectation... expectations) {
			return authExp.addAll(Arrays.asList(expectations));
		}
		
		private TimerTask revoker;
		
		public MockPolarisPAPIHost setValidCredentials( final String basicAuthHash, final String returnToken, final long validitySeconds ) {
			if (revoker != null) {
				try {
					revoker.cancel();
				}
				catch (IllegalStateException ex) {
					log.debug("Cancelling revocation timer failed", ex);
				}
				finally {
					revoker = null;
				}
			}
			
			final String tokenBearerHeader = "Bearer " + returnToken;
			
			// Remove any existing expectations that we are to replace.
			authExp.stream()
				.filter( authExp::remove ) // Filter here used as gate for failure to send clear.
				.map( Expectation::getId )
				.forEach( mock::clear );
				
			// Add hte new endpoints.
			addExp(
				mock.when(
					getRequestDefaults()
						.withHeader("Authorization", HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC + ' ' + basicAuthHash)
						.withMethod("POST")
						.withPath("/iii/sierra-api/v6/token"),
	
			      Times.unlimited(),
			      TimeToLive.unlimited(),
			      WEIGHT_PRIORITY_HIGH)
				
				.respond(
					defaultSuccess()
						.withBody(
							json(String.format("{"
								 + " \"access_token\": \"%s\"," 
								 + " \"token_type\": \"Bearer\","
								 + " \"expires_in\": %d"
								 + "}", returnToken, validitySeconds)
							))
				));
			
			// Always deny tokens values not matching the issued one
			addExp(
				mock.when(
					request()
						.withHeader(string("Authorization"), not(tokenBearerHeader)) // Odd expression meaning missing header
					  .withHeader("host", hostname),
	
		      Times.unlimited(),
		      TimeToLive.unlimited(),
		      WEIGHT_PRIORITY_HIGH)
				
						.respond(
							response()
			          .withStatusCode(HttpStatusCode.UNAUTHORIZED_401.code())));
			
			// Also schedule a task that revokes the key by adding a second rule after the TTL
			revoker = new TimerTask() {
				@Override
				public void run() {
					addExp(
						mock.when(
							request()
								.withHeader("Authorization", tokenBearerHeader) // Odd expression meaning missing header
							  .withHeader("host", hostname),
			
				      Times.unlimited(),
				      TimeToLive.unlimited(),
				      WEIGHT_PRIORITY_HIGH)
								.respond(
									response()
					          .withStatusCode(HttpStatusCode.UNAUTHORIZED_401.code())
					          .withBody(
												json("{ \"message\": \"Token reovked at " + LocalDateTime.now().toString() + "\" }"))));
				}
			};
			
			revokers.schedule(revoker, (validitySeconds * 1000));
			
			return this;
		}
		
		public MockPolarisPAPIHost setValidCredentials( @NonNull String username, @NonNull String password, @NonNull String returnToken, long validitySeconds ) {
			return setValidCredentials(
				Base64.getEncoder().encodeToString(new StringBuilder()
					.append(username)
					.append(':')
					.append(password)
					.toString()
					.getBytes()),
				
				returnToken,
				validitySeconds);
		}
	}

}
