package org.olf.dcb.tracking;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.management.endpoint.info.InfoSource;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import jakarta.inject.Singleton;
import io.micronaut.context.env.PropertySource;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.Map;
import reactor.core.publisher.Mono;



@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.config.enabled", notEquals = "false")
public class TrackingInfoContributor implements InfoSource {

    private final TrackingService trackingService;

    public TrackingInfoContributor(TrackingService trackingService) {
        this.trackingService = trackingService;
    }


    @Override
    public Publisher<PropertySource> getSource() {

        Duration duration = trackingService.getLastTrackingRunDuration();
        Long count = trackingService.getLastTrackingRunCount();

        Map<String, Object> trackingMap = Map.of(
            "trackingStatus", Map.of(
                "lastRunDuration", duration != null ? duration.toString() : "PT0S",
                "lastRunCount", ( count != null ? count : Long.valueOf(0) )
            )
        );

        PropertySource propertySource = PropertySource.of("trackingInfo", trackingMap);

        return Mono.just(propertySource);
    }

}
