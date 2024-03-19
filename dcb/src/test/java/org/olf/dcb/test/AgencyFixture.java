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

	public DataAgency saveAgency(DataAgency agency) {
		final DataAgency savedAgency = singleValueFrom(agencyRepository.save(agency));

		log.debug("Saved agency: {}", savedAgency);

		return savedAgency;
	}

	public DataAgency defineAgency(String code, String name) {
		return defineAgency(code, name, null);
	}

	public DataAgency defineAgency(String code, String name, DataHostLms hostLms) {
		return saveAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(name)
			.isSupplyingAgency(true)
			.hostLms(hostLms)
			.build());
	}
}
