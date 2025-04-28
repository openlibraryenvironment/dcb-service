package org.olf.dcb.tracking;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.Map;
import org.olf.dcb.core.model.PatronRequest.Status;

@ConfigurationProperties("dcb.polling")
public class PollingConfig {
   private Map<Status, Duration> durations;

    public Map<Status, Duration> getDurations() {
        return durations;
    }

    public void setDurations(Map<Status, Duration> durations) {
        this.durations = durations;
    }
}
