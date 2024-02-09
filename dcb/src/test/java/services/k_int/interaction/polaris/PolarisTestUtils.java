package services.k_int.interaction.polaris;

import static org.mockserver.model.HttpRequest.request;

import java.net.URI;
import java.util.function.Function;

import org.mockserver.client.ForwardChainExpectation;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;

import lombok.extern.slf4j.Slf4j;

public interface PolarisTestUtils {
	static MockPolarisPAPIHost mockFor(MockServerClient mock, String hostname) {
		return new MockPolarisPAPIHost(mock, hostname);
	}

	@Slf4j
	class MockPolarisPAPIHost {
		private final MockServerClient mock;
		private final String hostname;

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
		}

		public ForwardChainExpectation whenRequest(Function<HttpRequest,HttpRequest> requestModifier) {
			return mock.when(requestModifier.apply(request()
				.withHeader("Accept", "application/json")
				.withHeader("host", hostname)));
		}
	}
}
