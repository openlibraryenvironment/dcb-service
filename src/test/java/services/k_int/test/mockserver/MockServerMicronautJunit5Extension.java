package services.k_int.test.mockserver;

import static io.micronaut.http.client.DefaultHttpClientConfiguration.PREFIX;

import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockServerMicronautJunit5Extension extends MockServerExtension {

	private static final Set<String> MN_PROPS = Set.of("micronaut.http.client.proxy-type",
			"micronaut.http.client.proxy-address", "micronaut.http.client.proxy-port");

	private static final Map<String, String> STASHED = new HashMap<>();
	private static final String SOCKS_TYPE = Proxy.Type.SOCKS.name();

	private static Logger log = LoggerFactory.getLogger(MockServerMicronautJunit5Extension.class);

	private static void setMicronautProxySettings(ClientAndServer clientAndServer) {
		log.debug("Setting micronaut client proxy settings.");

		log.debug("Setting system property \"{}\" to \"{}\"", PREFIX + ".proxy-type", SOCKS_TYPE);
		System.setProperty(PREFIX + ".proxy-type", SOCKS_TYPE);

		final String proxyUrl = String.format("localhost:%d", clientAndServer.getPort());
		if (proxyUrl != null) {

			log.debug("Setting system property \"{}\" to \"{}\"", PREFIX + ".proxy-address", proxyUrl);
			System.setProperty(PREFIX + ".proxy-address", proxyUrl);
		}
	}

	private static void restoreStashed() {
		log.debug("Restoring MN client settings.");
		// Restore.
		MN_PROPS.forEach(key -> {
			var val = STASHED.get(key);
			if (val == null) {
				System.clearProperty(key);
				log.debug("Clearing system property \"{}\"", key);
				return;
			}

			log.debug("Setting system property \"{}\" to \"{}\"", key, val);
			System.setProperty(key, val);
		});
	}

	private static void stashCurrentProps() {

		log.debug("Stashing current MN client settings.");
		// Current MN system props.
		MN_PROPS.forEach(key -> {
			STASHED.put(key, System.getProperty(key));
		});
	}

	@Override
	public void beforeAll(ExtensionContext context) {
		super.beforeAll(context);
		stashCurrentProps();
		setMicronautProxySettings(this.clientAndServer);
	}

	@Override
	public void afterAll(ExtensionContext extensionContext) {
		super.afterAll(extensionContext);

		restoreStashed();
	}

}
