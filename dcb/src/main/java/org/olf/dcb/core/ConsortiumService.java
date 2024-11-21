package org.olf.dcb.core;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.*;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.postgres.PostgresConsortiumFunctionalSettingRepository;
import org.olf.dcb.storage.postgres.PostgresFunctionalSettingRepository;
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
	 * Retrieves the first consortium from the repository.
	 *
	 * @return a Mono that emits the first consortium, or an empty Mono if no consortium is found.
	 */
	private Mono<Consortium> findFirstConsortium() {
		return Mono.from(consortiumRepository.findFirst());
	}

	/**
	 * Retrieves a functional setting of a specific type for the first consortium found in the repository.
	 *
	 * @param functionalSettingType the type of functional setting to retrieve
	 * @return a Mono that emits the functional setting, or an empty Mono if no matching setting is found
	 */
	public Mono<FunctionalSetting> findFirstConsortiumFunctionalSetting(FunctionalSettingType functionalSettingType) {
		return findFirstConsortium()
			.flatMapMany(consortiumFunctionalSettingRepository::findByConsortium)
			.map(consortiumSetting -> consortiumSetting.getFunctionalSetting().getId())
			.flatMap(functionalSettingRepository::findById)
			.distinct()
			.filter(setting -> functionalSettingType.equals(setting.getName()))
			.next();
	}
}
