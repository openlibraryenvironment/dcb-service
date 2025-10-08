package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrThrow;

import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AgencyFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final AgencyRepository agencyRepository;

	public AgencyFixture(AgencyRepository agencyRepository) {
		this.agencyRepository = agencyRepository;
	}

	public void deleteAll() {
		dataAccess.deleteAll(agencyRepository.queryAll(),
			mapping -> agencyRepository.delete(mapping.getId()));
	}

	public DataAgency findByCode(String code) {
		return singleValueFrom(agencyRepository.findOneByCode(code));
	}

	public DataAgency defineAgency(String code, String name, DataHostLms hostLms) {
		return defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(name)
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.hostLms(hostLms)
			.build());
	}

	public DataAgency defineAgency(String code, String name, UUID hostLmsId) {
		return defineAgency(code, name, DataHostLms.builder().id(hostLmsId).build());
	}

	public DataAgency defineAgency(DataAgency agency) {
		final var savedAgency = singleValueFrom(agencyRepository.save(agency));

		log.debug("Saved agency: {}", savedAgency);

		return savedAgency;
	}

	public DataAgency defineAgency(String code, String name, DataHostLms hostLms,
		Double latitude, Double longitude) {

		return defineAgency(DataAgency.builder()
			.id(randomUUID())
			.name(name)
			.code(code)
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.hostLms(hostLms)
			.longitude(longitude)
			.latitude(latitude)
			.build());
	}

	public DataAgency defineAgencyWithNoHostLms(String code, String name) {
		return defineAgency(code, name, (DataHostLms) null);
	}

	public void delete(DataAgency agency) {
		final var id = getValueOrThrow(agency, DataAgency::getId,
			() -> new RuntimeException("Agency (%s) has no ID".formatted(agency)));

		singleValueFrom(agencyRepository.delete(id));
	}
}
