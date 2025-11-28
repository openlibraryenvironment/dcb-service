package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.ConsortiumFunctionalSetting;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.storage.ConsortiumFunctionalSettingRepository;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.FunctionalSettingRepository;
import org.olf.dcb.storage.LibraryGroupRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

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

	public void enableSetting(Consortium consortium, FunctionalSettingType type) {
		createSetting(consortium, type, true);
	}

	public void enableSetting(FunctionalSettingType settingType) {
		createConsortiumWithFunctionalSetting(settingType, true);
	}

	public void disableSetting(FunctionalSettingType settingType) {
		createConsortiumWithFunctionalSetting(settingType, false);
	}

	/**
	 * Creates a complete consortium setup with all necessary related entities.
	 * Includes a library group, consortium, functional setting, and links them together.
	 */
	public void createConsortiumWithFunctionalSetting(
		FunctionalSettingType functionalSettingType, boolean enabled) {

		final var consortium = createConsortium();

		createSetting(consortium, functionalSettingType, enabled);
	}

	public void createSetting(Consortium consortium,
		FunctionalSettingType functionalSettingType, Boolean enabled) {

		final var setting = createFunctionalSetting(functionalSettingType, enabled);
		final var functionalSetting = persistFunctionalSetting(setting);

		persistConsortiumFunctionalSetting(linkConsortiumToSetting(consortium, functionalSetting));
	}

	public Consortium createConsortium() {
		final var libraryGroup = persistLibraryGroup(createMobiusLibraryGroup());

		return persistConsortium(createConsortiumFor(libraryGroup));
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

	private void persistConsortiumFunctionalSetting(ConsortiumFunctionalSetting setting) {
		var saved = singleValueFrom(consortiumFunctionalSettingRepository.save(setting));
		log.debug("Persisted consortium functional setting: {}", saved);
	}

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

	private static FunctionalSetting createFunctionalSetting(FunctionalSettingType functionalSettingType, Boolean enabled) {
		return FunctionalSetting.builder()
			.id(randomUUID())
			.name(functionalSettingType)
			.enabled(enabled)
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
