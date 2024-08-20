package org.olf.dcb.ingest.metrics;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.IngestSourcesProvider;
import org.olf.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.PublisherTransformationService;
import services.k_int.micronaut.concurrency.ConcurrencyGroupService;

@Singleton
@Slf4j
@RequiresMetrics
@Replaces(bean = IngestService.class)
public class MeasuredIngestService extends IngestService {
	
	private final Map<String, DistributionSummary> sourceSummaries = new ConcurrentHashMap<>();

	private static final Pattern CLEAN_KEYS = Pattern.compile("(?>[^a-zA-Z0-9_\\n])+");
	
	private static final String METRIC_ROOT = "ingest";

	private final MeterRegistry meterRegistry;
	
	MeasuredIngestService(BibRecordService bibRecordService, List<IngestSourcesProvider> sourceProviders,
			PublisherTransformationService publisherHooksService, RecordClusteringService recordClusteringService,
			ConversionService conversionService,
			ConcurrencyGroupService concurrencyGroupService, ReactorFederatedLockService lockService, PublisherTransformationService publisherTransformationService, MeterRegistry meterRegistry) {
		super(bibRecordService, sourceProviders, publisherHooksService, recordClusteringService, conversionService, concurrencyGroupService, lockService);
		this.meterRegistry = meterRegistry;
		log.info("USING INSTRUMENTED INGEST PROCESS");
	}
	
	protected void printSummary() {
		
		StringBuffer sb = new StringBuffer();
		if (sourceSummaries.size() > 0) {
			sourceSummaries.forEach((key, summary) -> sb.append( "\n\t\t%s: %s".formatted(key, summary.takeSnapshot().toString()) ));
		} else {
			sb.append("N/A");
		}
		
		log.info("Ingest Summary: {}", sb);
	}
	
	@Override
	protected Function<IngestSource, Publisher<IngestRecord>> subscribeToSource(Mono<String> terminator) {
		
		final var superImpl = super.subscribeToSource(terminator);
		
		return ( source ) -> {
			
			final String key = Optional.ofNullable(source.getName())
					.or(() -> Optional.of(source.toString()))
					.map(CLEAN_KEYS::matcher)
					.map( m -> m.replaceAll("-") )
				.get();
			
			final var fetchCountKey = "%s.fetch.counts.%s".formatted(METRIC_ROOT, key);
			final var fetchGlobalCountKey = "%s.fetch.counts._global".formatted(METRIC_ROOT, key);
			final var fetchGlobalCountSummary = sourceSummaries.computeIfAbsent(fetchGlobalCountKey, theKey -> meterRegistry.summary(theKey));
			final var fetchCountSummary = sourceSummaries.computeIfAbsent(fetchCountKey, theKey -> meterRegistry.summary(theKey));
			
			
			final var multiCast = Flux.from( superImpl.apply( source ) )
				.share();
			
			final AtomicLong lastEmitted = new AtomicLong(0); 
			
			final var metricUpdater = multiCast
				.map( _i -> 1 ) // Just convert int
				.buffer(Duration.ofMinutes(1))
				.doOnSubscribe(_s -> {
					lastEmitted.set(System.currentTimeMillis());
				});
			
			return multiCast.doOnSubscribe( _s -> metricUpdater.subscribe(recordsLastMinute -> {
				final var size = recordsLastMinute.size();
				fetchCountSummary.record(size);
				fetchGlobalCountSummary.record(size);
			}, t -> log.error("Error collecting metrics", t)));
		};
	}
	
	@Override
	public Flux<BibRecord> getBibRecordStream() {
		
		final var multiCast = super.getBibRecordStream().share();
		final var metricPrinter = multiCast.window(Duration.ofMinutes(1));
		
		return multiCast.doOnSubscribe( _s -> metricPrinter.subscribe(_window -> {
			printSummary();
		}, t -> log.error("Error collecting metrics", t)));
	}
	
}
