package org.olf.dcb.core.svc;

import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;

@Singleton
public class AgencyService {
	private final AgencyRepository agencyRepository;

	public AgencyService(AgencyRepository agencyRepository) {
		this.agencyRepository = agencyRepository;
	}
}
