package org.olf.reshare.dcb.ingest;

import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Singleton
// Only create bean when configuration property
// endpoints.health.url.enabled equals true,
// and HealthEndpoint bean to expose /health endpoint is available.
@Requires(beans = HealthEndpoint.class)
public class IngestHealthIndicator implements HealthIndicator {

	private Map<String, Map<String,Object>> currentStatus = new HashMap();

	public IngestHealthIndicator() {
	}

	@Override
	public Publisher<HealthResult> getResult() {
		final int statusCode = 200;
		final boolean statusOk = true;
		final HealthStatus healthStatus = statusOk ? HealthStatus.UP : HealthStatus.DOWN;

		// We use the builder API of HealthResult to create
		// the health result with a details section containing
		// the checked URL and the health status.
		return Mono.just(
			HealthResult.builder("ingest", healthStatus)
				.details(currentStatus)
				.build());
	}

	private Map<String,Object> getProviderInfo(String code) {
		Map<String,Object> provider_info = currentStatus.get(code);
		if ( provider_info == null ) {
			provider_info = new HashMap<String,Object>();
			currentStatus.put(code, provider_info);
		}
		return provider_info;
	}
	public void notifyIngestStatus(String code, String status) {
		Map<String,Object> provider_info = getProviderInfo(code);
		provider_info.put("status",status);
		provider_info.put("lastUpdate",new java.util.Date().toString());
	}

	public void notifyIngestStatus(String code,
																 String status,
																 int offset,
																 int pageCounter) {
		Map<String,Object> provider_info = getProviderInfo(code);
		provider_info.put("status",status);
		provider_info.put("offset",Integer.valueOf(offset));
		provider_info.put("offset",Integer.valueOf(pageCounter));
		provider_info.put("lastUpdate",new java.util.Date().toString());
	}
}
