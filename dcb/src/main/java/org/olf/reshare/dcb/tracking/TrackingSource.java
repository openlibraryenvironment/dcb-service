package org.olf.reshare.dcb.tracking;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;

import org.olf.reshare.dcb.configuration.ConfigurationRecord;
import org.olf.reshare.dcb.tracking.model.TrackingRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.Toggleable;

public interface TrackingSource extends Named {
        public Publisher<TrackingRecord> getTrackingData();

				public boolean isEnabled();
}
