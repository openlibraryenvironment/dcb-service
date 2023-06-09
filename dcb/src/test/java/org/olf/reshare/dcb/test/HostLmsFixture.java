package org.olf.reshare.dcb.test;

import static java.util.UUID.randomUUID;

import java.util.Map;
import java.util.UUID;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.sierra.HostLmsSierraApiClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.storage.HostLmsRepository;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;

@Prototype
public class HostLmsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final HostLmsRepository hostLmsRepository;
	private final HostLmsService hostLmsService;
	private final AgencyRepository agencyRepository;
	private final PatronIdentityFixture patronIdentityFixture;

	public HostLmsFixture(HostLmsRepository hostLmsRepository,
		HostLmsService hostLmsService, AgencyRepository agencyRepository,
		PatronIdentityFixture patronIdentityFixture) {

		this.hostLmsRepository = hostLmsRepository;
		this.hostLmsService = hostLmsService;
		this.agencyRepository = agencyRepository;
		this.patronIdentityFixture = patronIdentityFixture;
	}

	public void createHostLms(DataHostLms hostLms) {
		Mono.from(hostLmsRepository.save(hostLms)).block();
	}

	public void createHostLms(UUID id, String code) {
		createHostLms(new DataHostLms(id, code, "Test Host LMS",
			SierraLmsClient.class.getCanonicalName(), Map.of()));
	}

	public void createSierraHostLms(String username, String password, String host,
		String code) {

		createHostLms(
			DataHostLms.builder()
				.id(randomUUID())
				.code(code)
				.name(code)
				.lmsClientClass(SierraLmsClient.class.getCanonicalName())
				.clientConfig(Map.of(
					"key", username,
					"secret", password,
					"base-url", host))
				.build());
	}

	public void deleteAllHostLMS() {
		dataAccess.deleteAll(agencyRepository.findAll(),
			agency -> agencyRepository.delete(agency.getId()));

		patronIdentityFixture.deleteAllPatronIdentities();

		dataAccess.deleteAll(hostLmsRepository.findAll(),
			hostLms -> hostLmsRepository.delete(hostLms.getId()));
	}

	public HostLmsClient createClient(String code) {
		return hostLmsService.getClientFor(code).block();
	}

	public HostLmsSierraApiClient createClient(String code, HttpClient client) {
		final var hostLms = findByCode(code);

		// Need to create a client directly
		// because injecting gives incorrectly configured client
		return new HostLmsSierraApiClient(hostLms, client);
	}

	public DataHostLms findByCode(String code) {
		return hostLmsService.findByCode(code).block();
	}
}
