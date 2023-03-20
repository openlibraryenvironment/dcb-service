package org.olf.reshare.dcb.demo;

import org.olf.reshare.dcb.core.AppConfig;
import org.olf.reshare.dcb.ingest.IngestService;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;
import services.k_int.micronaut.PublisherTransformation;

/**
 * Factory that houses any Transformers we wish to register, with the
 * transformation service. This particular factory is only active when the
 * application is started with the under the "demo" environment.
 * 
 * @author Steve Osguthorpe
 */
@Factory
public class DemoTransformations {

	private static final Logger log = LoggerFactory.getLogger(DemoTransformations.class);

	@Value("${" + AppConfig.ROOT + ".demo.ingest.limit:1000}")
	int ingestLimit;

	public DemoTransformations() {
		log.warn("Demo transformations are active");
	}

	@Prototype // Prototype ensures new instance of this bean at every injection point.
	@Named(IngestService.TRANSFORMATIONS_RECORDS) // Qualified name is used when searching for Applicable Transformers.
	PublisherTransformation<IngestRecord> limitIngestStream() {

		return (pub) -> {

			log.info("Limiting ingest size to first {}", ingestLimit);
			return Flux.from(pub).take(ingestLimit, true);
		};

	}

//	@Prototype
//	@Named(IngestService.TRANSFORMATIONS_RECORDS)
//	PublisherTransformation<IngestRecord> logIngestStream() {
//		return (Publisher<IngestRecord> pub) -> Flux.from(pub).log(DemoTransformations.class.getName(), Level.INFO, SignalType.ON_NEXT);
//	}
}
