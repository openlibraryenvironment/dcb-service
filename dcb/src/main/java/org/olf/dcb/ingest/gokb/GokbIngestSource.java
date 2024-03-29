package org.olf.dcb.ingest.gokb;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.gokb.GokbApiClient;
import services.k_int.interaction.gokb.GokbTipp;
import services.k_int.utils.UUIDUtils;

@Singleton
@Requires(property = (GokbIngestSource.CONFIG_ROOT + ".enabled"), value = "true", defaultValue = "false")
public class GokbIngestSource implements IngestSource {

	public static final String CONFIG_ROOT = "gokb.ingest";

	private static Logger log = LoggerFactory.getLogger(GokbIngestSource.class);

	private final GokbApiClient gokbapi;
	// import io.micronaut.core.convert.ConversionService;

	GokbIngestSource(GokbApiClient gokbapi, ConversionService conversionService) {
		this.gokbapi = gokbapi;
	}

	@Override
	public Publisher<IngestRecord> apply(Instant since, Publisher<String> terminator) {

		log.info("Fetching from GOKb");

		// The stream of imported records.
		return Flux.from(scrollAllResults(since, null))
				.takeUntilOther( Mono.from(terminator)
					.doOnNext( reason -> log.info("Ejecting from collect sequence. Reason: {}", reason) ))
				
				.name("gokp-tipps")
				.filter(tipp -> tipp.tippTitleName() != null)
				.map(tipp -> IngestRecord.build(record -> {
					record.uuid(uuid5ForTippUuid(tipp.uuid())).title(tipp.tippTitleName());
				}));
	}

	private static final String UUID5_PREFIX = "ingest-source:gokb";

	public UUID uuid5ForTippUuid(@NotNull final UUID tippUuid) {

		final String concat = UUID5_PREFIX + ":" + tippUuid.toString();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	private Publisher<GokbTipp> scrollAllResults(final Instant lastRun, final String scrollId) {
		log.info("Fetching scroll page from Gokb");
		return Mono.from(gokbapi.scrollTipps(scrollId, lastRun))
				.filter(resp -> "OK".equalsIgnoreCase(resp.result()) && resp.size() > 0).flatMapMany(resp -> {

					log.info("Fetched a chunk of {} records", resp.size());
					// We already have results. If there are more results then we create a
					// new Stream from these results plus the result of recursing this
					// method with the scroll id the gokb api will supply the next page of
					// results. Doing this here effectively chunks a single stream into
					// the 5000 element pages that gokb supplies, but removes the onus
					// of paging from the subscriber.

					final Flux<GokbTipp> currentPage = Flux.fromIterable(resp.records());
					final boolean more = resp.hasMoreRecords();

					if (!more) {
						log.info("No more results to fetch");
					}

					return more ? Flux.concat(currentPage, scrollAllResults(lastRun, resp.scrollId())) : currentPage;
				});
	}

	@Override
	public String getName() {
		return this.getClass().getName();
	}

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {
		return Flux.empty();
	}

	@Override
	public ProcessStateService getProcessStateService() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PublisherState mapToPublisherState(Map<String, Object> mapData) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Publisher<PublisherState> saveState(UUID context, String process, PublisherState state) {
		// TODO Auto-generated method stub
		return null;
	}
}
