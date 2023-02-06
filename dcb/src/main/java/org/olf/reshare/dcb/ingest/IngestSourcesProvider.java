package org.olf.reshare.dcb.ingest;

import org.reactivestreams.Publisher;

public interface IngestSourcesProvider {
	Publisher<IngestSource> getIngestSources();
}
