package org.olf.dcb.security.discovery;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.nimbusds.jose.util.JSONObjectUtils;

import io.micronaut.core.annotation.Nullable;

/** A {@link DiscoveryConfig} built in code, with the interface's defaults for everything else. */
record TestDiscoveryConfig(String audience, Duration maxAssertionLifetime, @Nullable String trustedServicesJson)
	implements DiscoveryConfig {

	static TestDiscoveryConfig trusting(Duration maxAssertionLifetime, List<Map<String, Object>> services) {
		return new TestDiscoveryConfig("dcb", maxAssertionLifetime, json(services));
	}

	/** One entry of {@code trusted-services-json}; a null value leaves the field out. */
	static Map<String, Object> service(@Nullable String serviceId, @Nullable String issuer,
		@Nullable String clientId, @Nullable Map<String, Object> jwks) {

		final var fields = new LinkedHashMap<String, Object>();
		if (serviceId != null) fields.put("service-id", serviceId);
		if (issuer != null) fields.put("issuer", issuer);
		if (clientId != null) fields.put("client-id", clientId);
		if (jwks != null) fields.put("jwks", jwks);
		return fields;
	}

	static String json(List<Map<String, Object>> services) {
		return services.stream().map(JSONObjectUtils::toJSONString).collect(Collectors.joining(",", "[", "]"));
	}

	@Override public String getAudience() { return audience; }
	@Override public Duration getMaxAssertionLifetime() { return maxAssertionLifetime; }
	@Override public int getMaxLoans() { return 25; }
	@Override public int getLoanLookupConcurrency() { return 2; }
	@Override public Duration getLoanLookupTimeout() { return Duration.ofSeconds(8); }
	@Override public int getMaxAccountRows() { return 25; }
	@Override public Duration getAccountLookupTimeout() { return Duration.ofSeconds(8); }
	@Override public Duration getPatronResolveTimeout() { return Duration.ofSeconds(8); }
	@Override public int getPatronResolvesPerMinute() { return 600; }
	@Override public String getTrustedServicesJson() { return trustedServicesJson; }
}
