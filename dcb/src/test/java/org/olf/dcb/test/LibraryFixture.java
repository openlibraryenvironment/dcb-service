package org.olf.dcb.test;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.storage.LibraryRepository;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

@Slf4j
@Singleton
public class LibraryFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final LibraryRepository libraryRepository;

	public LibraryFixture(LibraryRepository libraryRepository) {
		this.libraryRepository = libraryRepository;
	}

	public void deleteAll() {
		dataAccess.deleteAll(libraryRepository.queryAll(),
			mapping -> libraryRepository.delete(mapping.getId()));
	}

	public Library saveLibrary(Library library) {
		final var savedLibrary = singleValueFrom(libraryRepository.save(library));

		log.debug("Saved library: {}", savedLibrary);

		return savedLibrary;
	}

	public Library defineLibrary(String agencyCode, String principal, String secret) {
		return saveLibrary(Library.builder()
			.id(randomUUID())
			.agencyCode(agencyCode)
			.fullName("fullName")
			.shortName("shortName")
			.abbreviatedName("abbreviatedName")
			.principalLabel(principal)
			.secretLabel(secret)
			.build());
	}
}
