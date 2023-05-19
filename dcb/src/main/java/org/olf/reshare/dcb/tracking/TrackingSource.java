package org.olf.reshare.dcb.tracking;

import org.olf.reshare.dcb.tracking.model.TrackingRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.naming.Named;

public interface TrackingSource extends Named {
        public Publisher<TrackingRecord> getTrackingData();

				public boolean isEnabled();
}
