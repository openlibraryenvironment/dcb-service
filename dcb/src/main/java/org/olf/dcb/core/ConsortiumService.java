package org.olf.dcb.core;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.exceptions.MultipleConsortiumException;
import org.olf.dcb.core.model.*;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.postgres.PostgresConsortiumFunctionalSettingRepository;
import org.olf.dcb.storage.postgres.PostgresFunctionalSettingRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ConsortiumService {

	private final ConsortiumRepository consortiumRepository;
	private final PostgresConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository;
	private final PostgresFunctionalSettingRepository functionalSettingRepository;

	ConsortiumService(ConsortiumRepository consortiumRepository,
		PostgresConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository,
		PostgresFunctionalSettingRepository functionalSettingRepository) {
		this.consortiumRepository = consortiumRepository;
		this.consortiumFunctionalSettingRepository = consortiumFunctionalSettingRepository;
		this.functionalSettingRepository = functionalSettingRepository;
	}

	/**
	 * Finds and validates the existence of a single Consortium in the system.
	 *
	 * @return Mono<Consortium> containing the single Consortium if exactly one exists
	 * @throws MultipleConsortiumException if multiple Consortiums are found
	 * @return Mono.empty() if no Consortium exists
	 */
	private Mono<Consortium> findOneConsortium() {
		return Flux.from(consortiumRepository.queryAll())
			.collectList()
			.flatMap(consortiums -> {
				if (consortiums.isEmpty()) {
					log.warn("No consortium exists for this DCB system.");

					return Mono.empty();
				}

				if (consortiums.size() > 1) {

					return Mono.error(
						new MultipleConsortiumException("Multiple Consortium found when only one was expected. Found: " + consortiums.size()));
				}

				return Mono.just(consortiums.get(0));
			});
	}

	/**
	 * Retrieves a functional setting of a specific type for the only consortium found in the repository.
	 *
	 * @param functionalSettingType the type of functional setting to retrieve
	 * @return a Mono that emits the functional setting, or an empty Mono if no matching setting is found
	 */
	public Mono<FunctionalSetting> findOneConsortiumFunctionalSetting(FunctionalSettingType functionalSettingType) {
		return findOneConsortium()
			.flatMapMany(consortiumFunctionalSettingRepository::findByConsortium)
			.map(consortiumSetting -> consortiumSetting.getFunctionalSetting().getId())
			.flatMap(functionalSettingRepository::findById)
			.distinct()
			.filter(setting -> functionalSettingType.equals(setting.getName()))
			.next();
	}
}
