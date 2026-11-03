package org.olf.dcb.request.lifecycle.ncip.profile.application;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Singleton
public class DcbProfileMembershipSyncJob {
	private final DcbProfileRegistrationService registrationService;

	public DcbProfileMembershipSyncJob(DcbProfileRegistrationService registrationService) {
		this.registrationService = registrationService;
	}
	
	@AppTask
  @Scheduled(initialDelay = "2m", fixedDelay = "${dcb.profile-registration.sync-poll-interval:1m}")
	void syncDueMemberships() {
		registrationService.syncDue();
	}
}
