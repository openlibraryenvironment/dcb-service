package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class AgencyFixture {

	private final DataAccess dataAccess = new DataAccess();

	private final AgencyRepository agencyRepository;

	public AgencyFixture(AgencyRepository agencyRepository) {
		this.agencyRepository = agencyRepository;
	}

	public void saveAgency(DataAgency agency) {
		singleValueFrom(agencyRepository.save(agency));
	}

	public void deleteAllAgencies() {
		dataAccess.deleteAll(agencyRepository.findAll(),
			mapping -> agencyRepository.delete(mapping.getId()));
	}
}
