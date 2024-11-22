package org.olf.dcb.test;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.*;
import org.olf.dcb.storage.*;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

@Slf4j
@Singleton
public class ConsortiumFixture {
	private final LibraryGroupRepository libraryGroupRepository;
	private final ConsortiumRepository consortiumRepository;
	private final FunctionalSettingRepository functionalSettingRepository;
	private final ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository;

	public ConsortiumFixture(
		LibraryGroupRepository libraryGroupRepository,
		ConsortiumRepository consortiumRepository,
		FunctionalSettingRepository functionalSettingRepository,
		ConsortiumFunctionalSettingRepository consortiumFunctionalSettingRepository) {
		this.libraryGroupRepository = libraryGroupRepository;
		this.consortiumRepository = consortiumRepository;
		this.functionalSettingRepository = functionalSettingRepository;
		this.consortiumFunctionalSettingRepository = consortiumFunctionalSettingRepository;
	}

	private final DataAccess dataAccess = new DataAccess();


	/**
	 * Deletes all consortium-related entities in the system.
	 *
	 * This method performs a systematic deletion of:
	 * 1. Consortium Functional Settings
	 * 2. Functional Settings
	 * 3. Consortiums
	 * 4. Library Groups
	 *
	 * The deletion is performed using a generic delete method that queries all entities
	 * and then deletes them individually, ensuring clean removal of all related data.
	 */
	public void deleteAll() {
		log.debug("Attempting to delete all consortium setup and associated information");

		try {
			// Delete Consortium Functional Settings
			dataAccess.deleteAll(
				consortiumFunctionalSettingRepository.queryAll(),
				setting -> consortiumFunctionalSettingRepository.delete(setting.getId())
			);

			// Delete Functional Settings
			dataAccess.deleteAll(
				functionalSettingRepository.queryAll(),
				setting -> functionalSettingRepository.delete(setting.getId())
			);

			// Delete Consortiums
			dataAccess.deleteAll(
				consortiumRepository.queryAll(),
				consortium -> consortiumRepository.delete(consortium.getId())
			);

			// Delete Library Groups
			dataAccess.deleteAll(
				libraryGroupRepository.queryAll(),
				libraryGroup -> libraryGroupRepository.delete(libraryGroup.getId())
			);

			log.debug("Successfully deleted all consortium setup and associated information");
		} catch (Exception e) {
			log.error("Error during deletion of consortium setup", e);
			throw e;
		}
	}

	/**
	 * Creates a complete consortium setup with all necessary related entities.
	 * Includes a library group, consortium, functional setting, and links them together.
	 */
	public Consortium createConsortiumWithReResolutionFunctionalSetting() {
		var libraryGroup = persistLibraryGroup(createMobiusLibraryGroup());
		var consortium = persistConsortium(createConsortiumFor(libraryGroup));
		var functionalSetting = persistFunctionalSetting(createReResolutionSetting());
		persistConsortiumFunctionalSetting(linkConsortiumToSetting(consortium, functionalSetting));

		var savedConsortium = findConsortiumById(consortium.getId());
		log.debug("Test consortium created: {}", savedConsortium);

		return savedConsortium;
	}

	private LibraryGroup persistLibraryGroup(LibraryGroup libraryGroup) {
		var saved = singleValueFrom(libraryGroupRepository.save(libraryGroup));
		log.debug("Persisted library group: {}", saved);
		return saved;
	}

	private Consortium persistConsortium(Consortium consortium) {
		var saved = singleValueFrom(consortiumRepository.save(consortium));
		log.debug("Persisted consortium: {}", saved);
		return saved;
	}

	private FunctionalSetting persistFunctionalSetting(FunctionalSetting setting) {
		var saved = singleValueFrom(functionalSettingRepository.save(setting));
		log.debug("Persisted functional setting: {}", saved);
		return saved;
	}

	private ConsortiumFunctionalSetting persistConsortiumFunctionalSetting(ConsortiumFunctionalSetting setting) {
		var saved = singleValueFrom(consortiumFunctionalSettingRepository.save(setting));
		log.debug("Persisted consortium functional setting: {}", saved);
		return saved;
	}

	private Consortium findConsortiumById(UUID id) {
		return singleValueFrom(consortiumRepository.findById(id));
	}

	// Factory methods for creating entities
	private static LibraryGroup createMobiusLibraryGroup() {
		return LibraryGroup.builder()
			.id(randomUUID())
			.code("MOBIUS")
			.name("MOBIUS_CONSORTIUM")
			.type("CONSORTIUM")
			.build();
	}

	private static Consortium createConsortiumFor(LibraryGroup libraryGroup) {
		return Consortium.builder()
			.id(randomUUID())
			.libraryGroup(libraryGroup)
			.displayName("MOBIUS_CONSORTIUM")
			.reason("Consortium creation")
			.changeCategory("Initial setup")
			.build();
	}

	private static FunctionalSetting createReResolutionSetting() {
		return FunctionalSetting.builder()
			.id(randomUUID())
			.name(FunctionalSettingType.RE_RESOLUTION)
			.enabled(true)
			.description("Re-resolution")
			.build();
	}

	private static ConsortiumFunctionalSetting linkConsortiumToSetting(
		Consortium consortium,
		FunctionalSetting functionalSetting) {
		return ConsortiumFunctionalSetting.builder()
			.id(randomUUID())
			.functionalSetting(functionalSetting)
			.consortium(consortium)
			.build();
	}
}
