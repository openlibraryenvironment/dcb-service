package org.olf.dcb.core.interaction.polaris;

import static java.lang.Integer.parseInt;
import static reactor.function.TupleUtils.function;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.HostLms;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.transaction.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import org.olf.dcb.core.AppState;
import org.olf.dcb.core.AppState.AppStatus;

class IngestHelper {
	private static final Logger log = LoggerFactory.getLogger(IngestHelper.class);
	private final PolarisLmsClient client;
	private final ProcessStateService processStateService;
	private final HostLms lms;
	private final AppState appState;

	public IngestHelper(
		PolarisLmsClient client,
		HostLms lms,
		ProcessStateService processStateService,
		AppState appState)
	{
		this.client = client;
		this.lms = lms;
		this.processStateService = processStateService;
		this.appState = appState;
	}

	public static String formatDateFrom(Instant instant) {
		if (instant == null) return null;

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));
		return formatter.format(instant);
	}

	Publisher<PolarisLmsClient.BibsPagedRow> pageAllResults(int pageSize) {
		return getInitialState(lms.getId(), "ingest")
			.flatMapMany(state -> fetchPageAndUpdateState(state, pageSize))
			.concatMap(function(this::processPageAndSaveState));
	}

	private Mono<PublisherState> getInitialState(UUID context, String process) {
		return processStateService.getStateMap(context, process)
			.defaultIfEmpty(new HashMap<>())
			.map(currentStateMap -> {
				PublisherState generatorState = new PublisherState(currentStateMap);
				log.trace("backpressureAwareBibResultGenerator - state={} lmsid={} thread={}",
					currentStateMap, lms.getId(), Thread.currentThread().getName());

				String cursor = (String) currentStateMap.get("cursor");
				if (cursor != null) {
					log.trace("Cursor: " + cursor);
					String[] components = cursor.split(":");

					if (components.length > 1) {
						switch (components[0]) {
							case "bootstrap":
								generatorState.offset = parseInt(components[1]);
								log.info("Resuming bootstrap for {} at offset {}", lms.getName(), generatorState.offset);
								break;
							case "deltaSince":
								generatorState.sinceMillis = Long.parseLong(components[1]);
								generatorState.since = Instant.ofEpochMilli(generatorState.sinceMillis);
								if (components.length == 3) {
									generatorState.offset = parseInt(components[2]);
								}
								log.info("Resuming delta at timestamp {} offset={} name={}", generatorState.since, generatorState.offset, lms.getName());
								break;
						}
					}
				} else {
					log.info("Start a fresh ingest");
				}

				// Make a note of the time before we start
				generatorState.request_start_time = System.currentTimeMillis();
				log.debug("Create generator: name={} offset={} since={}",
					lms.getName(), generatorState.offset, generatorState.since);

				return generatorState;
			});
	}

	 PublisherState mapToPublisherState(Map<String, Object> mapData) {
		PublisherState generatorState = new PublisherState(mapData);
		log.info("backpressureAwareBibResultGenerator - state={} lmsid={} thread={}", mapData, lms.getId(),
			Thread.currentThread().getName());

		String cursor = (String) mapData.get("cursor");
		if (cursor != null) {
			log.debug("Cursor: " + cursor);
			String[] components = cursor.split(":");

			if (components.length > 1) {
				switch (components[0]) {
					case "bootstrap":
						generatorState.offset = parseInt(components[1]);
						log.info("Resuming bootstrap for {} at offset {}", lms.getName(), generatorState.offset);
						break;
					case "deltaSince":
						generatorState.sinceMillis = Long.parseLong(components[1]);
						generatorState.since = Instant.ofEpochMilli(generatorState.sinceMillis);
						if (components.length == 3) {
							generatorState.offset = parseInt(components[2]);
						}
						log.info("Resuming delta at timestamp {} offset={} name={}", generatorState.since, generatorState.offset,
							lms.getName());
						break;
				}
			}
		} else {
			log.info("Start a fresh ingest");
		}

		// Make a note of the time before we start
		generatorState.request_start_time = System.currentTimeMillis();
		log.debug("Create generator: name={} offset={} since={}", lms.getName(), generatorState.offset,
			generatorState.since);

		return generatorState;
	}

	private Flux<Tuple2<PublisherState, PolarisLmsClient.BibsPagedResult>> fetchPageAndUpdateState(
		PublisherState state, int pageSize) {

		return Mono.zip(Mono.just(state.toBuilder().build()), fetchPage(state.since, state.offset, pageSize))
			.expand(function((currentState, results) -> {
				var bibs = results.getGetBibsPagedRows();
				log.trace("Fetched a chunk of {} records for {}", bibs.size(), lms.getName());
				log.trace("got page {} of data, containing {} results", currentState.page_counter++, bibs.size());
				currentState.possiblyMore = bibs.size() == pageSize;

				if (!currentState.possiblyMore) {
					log.info("{} ingest Terminating cleanly - run out of bib results - new timestamp is {}", lms.getName(), currentState.request_start_time);
					currentState.storred_state.put("cursor", "deltaSince:" + currentState.request_start_time);
					currentState.storred_state.put("name", lms.getName());
					log.info("No more results to fetch from {}", lms.getName());
					return Mono.empty();
				} else {
					log.trace("Exhausted current page from {}, prep next", lms.getName());
					if (currentState.since != null) {
						currentState.storred_state.put("cursor", "deltaSince:" + currentState.sinceMillis + ":" + currentState.offset);
					} else {
						currentState.storred_state.put("cursor", "bootstrap:" + currentState.offset);
					}
				}

        // If the app is in a state of shutting down, then don't get another page of data.. bail!
        if ( appState.getRunStatus() != AppStatus.RUNNING ) {
          log.info("Detected app shutdown - ejecting from collect sequence {}",lms.getName());
          return Mono.empty();
        }

				return Mono.just(currentState.toBuilder().build())
					.zipWhen(updatedState -> fetchPage(updatedState.since, updatedState.offset, pageSize));
			}));
	}

	private Publisher<PolarisLmsClient.BibsPagedRow> processPageAndSaveState(
		PublisherState state, PolarisLmsClient.BibsPagedResult page) {

		state.offset = page.getLastID();

		return Flux.fromIterable(page.getGetBibsPagedRows())
			.concatWith(Mono.defer(() -> saveState(state))
				.flatMap(_s -> {
					log.debug("Updating state...");
					return Mono.empty();
				}))
			.doOnComplete(() -> log.debug("Consumed {} items", page.getGetBibsPagedRows().size()));
	}

	private Mono<PolarisLmsClient.BibsPagedResult> fetchPage(Instant updateDate, Integer lastId, Integer nrecs) {
		log.trace("Creating subscribeable batch from last id;  {}, {}", lastId, nrecs);
		final var date = formatDateFrom(updateDate);
		return Mono.from( client.getBibs(date, lastId, nrecs) )
			//.doOnSuccess(bibsPagedResult -> log.debug("result of bibPagedResult: {}", bibsPagedResult))
			.doOnSubscribe(_s -> log.info("Fetching batch from Polaris {} with since={} offset={} limit={}",
				lms.getName(), updateDate, lastId, nrecs));
	}

	// @Transactional(value = Transactional.TxType.REQUIRES_NEW)

	protected Mono<PublisherState> saveState(PublisherState state) {
		log.debug("Update state {} - {}", state,lms.getName());

		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state))
			.thenReturn(state);
	}
}
