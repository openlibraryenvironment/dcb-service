package org.olf.dcb.core.svc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.management.endpoint.info.InfoSource;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import jakarta.inject.Singleton;
import io.micronaut.context.env.PropertySource;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import reactor.core.publisher.Mono;

@Singleton
@Requires(beans = HouseKeepingService.class)
@Requires(property = "endpoints.info.config.enabled", notEquals = "false")
public class HouseKeepingInfo implements InfoSource {

  private final HouseKeepingService houseKeepingservice;

  public HouseKeepingInfo(HouseKeepingService houseKeepingservice) {
    this.houseKeepingservice = houseKeepingservice;
  }

	@Override
	public Publisher<PropertySource> getSource() {
 
		Map<String, Object> trackingMap = Map.of(
			"reprocessStatus", houseKeepingservice.getReprocessStatus()
        // Map.of( "lastRunDuration", "PT0S", "lastRunCount", Long.valueOf(0) )
		);

		PropertySource propertySource = PropertySource.of("HouseKeepingInfo", trackingMap);

		return Mono.just(propertySource);
	}
}

