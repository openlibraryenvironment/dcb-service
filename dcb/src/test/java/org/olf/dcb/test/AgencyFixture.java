package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Prototype
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

	public DataAgency saveAgency(DataAgency agency) {
		final var savedAgency = singleValueFrom(agencyRepository.save(agency));

		log.debug("Saved agency: {}", savedAgency);

		return savedAgency;
	}

	public DataAgency defineAgency(String code, String name, DataHostLms hostLms) {
		return defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(name)
			.isSupplyingAgency(true)
			.hostLms(hostLms)
			.build());
	}

	public DataAgency defineAgency(DataAgency agency) {
		return saveAgency(agency);
	}

	public DataAgency defineAgency(String code, String name, DataHostLms hostLms, Double latitude, Double longitude) {
		return defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(name)
			.isSupplyingAgency(true)
			.hostLms(hostLms)
			.longitude(longitude)
			.latitude(latitude)
			.build());
	}
}
