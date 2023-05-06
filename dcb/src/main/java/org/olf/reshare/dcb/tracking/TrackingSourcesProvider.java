package org.olf.reshare.dcb.tracking;

import org.reactivestreams.Publisher;

public interface TrackingSourcesProvider {
        Publisher<TrackingSource> getTrackingSources();
}

