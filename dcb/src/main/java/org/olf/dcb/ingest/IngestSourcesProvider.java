package org.olf.dcb.ingest;

import org.reactivestreams.Publisher;

public interface IngestSourcesProvider {
	Publisher<IngestSource> getIngestSources();
}
