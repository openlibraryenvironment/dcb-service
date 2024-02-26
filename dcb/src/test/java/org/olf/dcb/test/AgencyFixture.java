package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.context.annotation.Prototype;

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
		return singleValueFrom(agencyRepository.save(agency));
	}

	public DataAgency defineAgency(String code, String name) {
		return saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code(code)
			.name(name)
			.isSupplyingAgency(Boolean.TRUE)
			.build());
	}

	public DataAgency defineAgency(String code, String name, DataHostLms hostLms) {
		return saveAgency(DataAgency.builder()
			.id(UUID.randomUUID())
			.code(code)
			.name(name)
			.isSupplyingAgency(Boolean.TRUE)
			.hostLms(hostLms)
			.build());
	}


}
