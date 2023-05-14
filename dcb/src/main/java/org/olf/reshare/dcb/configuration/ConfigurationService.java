package org.olf.reshare.dcb.configuration;

import java.util.List;

import org.olf.reshare.dcb.core.model.*;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.storage.LocationRepository;
import org.olf.reshare.dcb.storage.RefdataValueRepository;
import org.olf.reshare.dcb.storage.ShelvingLocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.olf.reshare.dcb.ingest.IngestSourcesProvider;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.scheduling.processor.AppTask;


/**
 * The resource sharing network is composed of Agencies hosted on HostLMS systems (A single HostLMS can play host to many Agencies).
 * This service polls the different HostLMS systems and attempts to extract configuraiton data important to the requesting process
 * such as branches, shelving locations and agencies.
 */
@Refreshable
@Singleton
public class ConfigurationService implements Runnable {

    private Disposable mutex = null;
    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final List<IngestSourcesProvider> sourceProviders;
    private final ShelvingLocationRepository shelvingLocationRepository;

    private final AgencyRepository agencyRepository;

    private final LocationRepository locationRepository;

    private final RefdataValueRepository refdataValueRepository;

    public ConfigurationService(List<IngestSourcesProvider> sourceProviders,
                                ShelvingLocationRepository shelvingLocationRepository,
                                AgencyRepository agencyRepository,
                                LocationRepository locationRepository,
                                RefdataValueRepository refdataValueRepository) {
        this.sourceProviders = sourceProviders;
        this.shelvingLocationRepository = shelvingLocationRepository;
        this.agencyRepository = agencyRepository;
        this.locationRepository = locationRepository;
        this.refdataValueRepository = refdataValueRepository;
    }


    @javax.annotation.PostConstruct
    private void init() {
        log.info("ConfigurationService::init - providers:{}", sourceProviders.toString());
    }

    private Runnable cleanUp() {
        final var me = this;
        return () -> {
            log.info("Removing mutex");
            me.mutex = null;
            log.info("Mutex now set to {}", me.mutex);
        };
    }

    public Flux<ConfigurationRecord> getConfigRecordStream() {
        return Flux.merge(
                Flux.fromIterable(sourceProviders)
                        .concatMap(IngestSourcesProvider::getIngestSources)
                        .filter(source -> {
                            if (source.isEnabled()) return true;
                            log.info("Ingest from source: {} has been disabled in config", source.getName());
                            return false;
                        })
                        .map(IngestSource::getConfigStream)
                        .onErrorResume(t -> {
                            log.error("Error ingesting data {}", t.getMessage());
                            t.printStackTrace();
                            return Mono.empty();
                        }));
    }

    private ShelvingLocation mapShelvingLocationRecordToShelvingLocation(ShelvingLocationRecord slr, Location l, BranchRecord br) {
        // log.debug("create ShelvingLocation for {}",slr);
        return new ShelvingLocation()
                .builder()
                .id(slr.getId())
                .code(slr.getCode())
                .name(slr.getName())
                .hostSystem((DataHostLms) br.getLms())
                .location(l)
                .build();
    }

    private Mono<ShelvingLocation> upsertShelvingLocation(ShelvingLocation sl) {
        // log.debug("upsertShelvingLocation {}", sl);
        return Mono.from(shelvingLocationRepository.existsById(sl.getId()))
                .flatMap(exists -> Mono.fromDirect(exists ? shelvingLocationRepository.update(sl) : shelvingLocationRepository.save(sl)));
    }

    private Mono<Void> createShelvingLocations(Location l, BranchRecord br) {
        return Flux.fromIterable(br.getShelvingLocations())
                .map(slr -> mapShelvingLocationRecordToShelvingLocation(slr, l, br))
                .flatMap(sl -> upsertShelvingLocation(sl))
                .then(Mono.empty());
    }

	private Mono<Agency> handleAgencyRecord(BranchRecord br) {
		//log.debug("handleBranchRecord {}",br);
		// Different host LMS systems will have different policies on how BranchRecords map to agencies
		// In a multi-tenant sierra for example, branch records represent institutions and we should create
		// an agency for each branch record. This method will take account of policies configured for the
		// host LMS and return the appropriate agency for a given branch record.
		// There seems to be some hidden link between a sierra agency and sierra branch
		if (br.getLms() instanceof DataHostLms) {
			DataAgency upsert_agency = DataAgency
				.builder()
				.id(br.getId())
				.code(br.getLms().getCode() + "-BR-" + br.getLocalBranchId())
				.name(br.getBranchName())
				.hostLms((DataHostLms) br.getLms())
				.build();

			// log.debug("upsertAgency {}", br);
			return Mono.from(agencyRepository.existsById(upsert_agency.getId()))
				.flatMap(exists -> Mono.fromDirect(exists ? agencyRepository.update(upsert_agency) : agencyRepository.save(upsert_agency)))
				.thenReturn(upsert_agency);
		} else {
			log.warn("Unable to save agency for statically configured HostLMS");
			return Mono.empty();
		}
	}

    /**
     * Given a BranchRecord which includes a UUID5 that globally and consistently identifies the branch (In the context of a HostLMS)
     * Assuming that we wish to create an Agency record that corresponds to the branch, create the Agency and then
     * check to see if we need to create any ShelvingLocation records
     *
     * @param br
     * @return
     */
    private Mono<Location> handleBranchRecord(BranchRecord br) {
        //log.debug("handleBranchRecord {}",br);
				// Branch records map onto Location (Type=Branch) records for us
        if (br.getLms() instanceof DataHostLms) {
					Location l = Location.builder()
						.id(br.getId())
						.code(br.getLocalBranchId())
						.name(br.getBranchName())
						.hostSystem((DataHostLms) br.getLms())
						.type("BRANCH")
						.build();

					return Mono.from(locationRepository.existsById(l.getId()))
						.flatMap(exists -> Mono.fromDirect(exists ? locationRepository.update(l) : locationRepository.save(l)))
						.flatMap(savedLocation -> createShelvingLocations(savedLocation, br))
						.thenReturn(l);

				} else {
            log.warn("Unable to save location for statically configured HostLMS");
            return Mono.empty();
        }
    }

    private Mono<Location> handlePickupLocation(PickupLocationRecord pickupLocationRecord) {
        //log.debug("handlePickupLocation {}",pickupLocationRecord);
        if (pickupLocationRecord.getLms() instanceof DataHostLms) {
            Location upsert_location = Location
                    .builder()
                    .id(pickupLocationRecord.getId())
                    .code(pickupLocationRecord.getCode())
                    .name(pickupLocationRecord.getName())
                    .hostSystem((DataHostLms) pickupLocationRecord.getLms())
                    .isPickup(true)
                    .type("PICKUP")
                    .build();

            return Mono.from(locationRepository.existsById(upsert_location.getId()))
                    .flatMap(exists -> Mono.fromDirect(exists ? locationRepository.update(upsert_location) : locationRepository.save(upsert_location)))
                    .thenReturn(upsert_location);
        } else {
            log.warn("Unable to save agency for statically configured HostLMS");
            return Mono.empty();
        }
    }

    /**
     * We don't know what kinds of configuration records a hostLms might emit, so here we handle all the
     * different possible cases. At the moment BranchRecords and PickupLocations.
     *
     * @param cr - A canonical branch record which tries to minimise the differences between different host systems
     * @return the same config record but processed
     */
    public Mono<ConfigurationRecord> handleConfigRecord(ConfigurationRecord cr) {
        // log.debug("handleConfigRecord({})",cr);
        return Mono.just(cr)
                // We only handle branch records at the moment - and the HostLMS must  be a data host lms
                .flatMap(confrec -> {
                    return switch (confrec.getRecordType()) {
                        case BranchRecord.RECORD_TYPE -> handleBranchRecord((BranchRecord) confrec);
                        case PickupLocationRecord.RECORD_TYPE -> handlePickupLocation((PickupLocationRecord) confrec);
                        case RefdataRecord.RECORD_TYPE -> handleRefdataRecord((RefdataRecord) confrec);
                        default -> Mono.empty();
                    };
                })
                .thenReturn(cr);
    }

    private Mono<RefdataValue> handleRefdataRecord(RefdataRecord refdataRecord) {
        //log.debug("handleRefdataRecord {}",refdataRecord);
        RefdataValue rdv = RefdataValue
                .builder()
                .id(refdataRecord.getId())
                .category(refdataRecord.getCategory())
                .context(refdataRecord.getContext())
                .label(refdataRecord.getLabel())
                .value(refdataRecord.getValue())
                .build();
        return Mono.from(refdataValueRepository.existsById(rdv.getId()))
                .flatMap(exists -> Mono.fromDirect(exists ? refdataValueRepository.update(rdv) : refdataValueRepository.save(rdv)));
    }

    @Override
    @Scheduled(initialDelay = "10s", fixedDelay = "${dcb.networkconfigingest.interval:24h}")
    @AppTask
    public void run() {

        if (this.mutex != null && !this.mutex.isDisposed()) {
            log.info("Ingest already running skipping. Mutex: {}", this.mutex);
            return;
        }

        log.info("Scheduled Ingest");

        this.mutex = getConfigRecordStream()
                .doOnCancel(cleanUp())
                .flatMap(this::handleConfigRecord)
                .subscribe();
    }

}


