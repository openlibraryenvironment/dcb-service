package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient;
import org.olf.dcb.core.interaction.folio.FolioOaiPmhIngestSource;
import org.olf.dcb.core.interaction.polaris.PolarisLmsClient;
import org.olf.dcb.core.interaction.sierra.HostLmsSierraApiClient;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.storage.HostLmsRepository;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Prototype
public class HostLmsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final HostLmsRepository hostLmsRepository;
	private final HostLmsService hostLmsService;
	private final PatronFixture patronFixture;
	private final NumericRangeMappingFixture numericRangeMappingFixture;
	private final AgencyFixture agencyFixture;

	public HostLmsFixture(HostLmsRepository hostLmsRepository, HostLmsService hostLmsService,
		PatronFixture patronFixture, NumericRangeMappingFixture numericRangeMappingFixture,
		AgencyFixture agencyFixture) {

		this.hostLmsRepository = hostLmsRepository;
		this.hostLmsService = hostLmsService;
		this.patronFixture = patronFixture;
		this.numericRangeMappingFixture = numericRangeMappingFixture;
		this.agencyFixture = agencyFixture;
	}

	public DataHostLms createFolioHostLms(String code, String baseUrl,
		String apiKey, String recordSyntax, String metadataPrefix) {

		return createHostLms(randomUUID(), code, ConsortialFolioHostLmsClient.class,
			Optional.of(FolioOaiPmhIngestSource.class), Map.of(
				"base-url", baseUrl,
				"apikey", apiKey,
				"record-syntax", recordSyntax,
				"metadata-prefix", metadataPrefix
		));
	}

	public DataHostLms createSierraHostLms(String code) {
		// These parameters have to be defined in order for the client to be instantiable
		return createSierraHostLms(code,"some-username", "some-password", "http://some-sierra-system");
	}

	public DataHostLms createSierraHostLms(String code, String username,
		String password, String baseUrl) {

		return createSierraHostLms(code, Map.of(
			"key", username,
			"secret", password,
			"base-url", baseUrl));
	}

	public DataHostLms createSierraHostLms(String code, String username,
		String password, String baseUrl, String holdPolicy) {

		return createSierraHostLms(code, Map.of(
			"key", username,
			"secret", password,
			"base-url", baseUrl,
			"holdPolicy", holdPolicy,
			"get-holds-retry-attempts", "1",
			"default-agency-code", "default-agency-code"));
	}

	private DataHostLms createSierraHostLms(String code, Map<String, Object> config) {
		log.debug("Creating numeric range mapping");

		numericRangeMappingFixture.createMapping(code, "ItemType", 998L, 1001L, "DCB", "BKM");

		return createHostLms(UUID.randomUUID(), code, SierraLmsClient.class,
			Optional.empty(), config);
	}

	public void createPolarisHostLms(String code, String staffUsername,
		String staffPassword, String baseUrl, String domain, String accessId, String accessKey) {

		Map<String, Object> clientConfig = new HashMap<>();
		clientConfig.put("staff-username", staffUsername);
		clientConfig.put("staff-password", staffPassword);
		clientConfig.put("base-url", baseUrl);
		clientConfig.put("domain-id", domain);
		clientConfig.put("access-id", accessId);
		clientConfig.put("access-key", accessKey);
		clientConfig.put("page-size", 100);
		clientConfig.put("logon-branch-id", "73");
		clientConfig.put("logon-user-id", "1");

		Map<String, Object> papi = new HashMap<>();
		papi.put("papi-version", "v1");
		papi.put("lang-id", "1033");
		papi.put("app-id", "100");
		papi.put("org-id", "1");

		clientConfig.put("papi", papi);

		Map<String, Object> services = new HashMap<>();
		services.put("services-version", "v1");
		services.put("language", "eng");
		services.put("product-id", "20");
		services.put("site-domain", "polaris");
		services.put("organisation-id", "73");
		services.put("workstation-id", "1");

		clientConfig.put("services", services);

		Map<String, Object> item = new HashMap<>();
		item.put("renewal-limit", "0");
		item.put("fine-code-id", "1");
		item.put("history-action-id", "6");
		item.put("loan-period-code-id", "9");
		item.put("shelving-scheme-id", "3");
		item.put("barcode-prefix", "test");

		clientConfig.put("item", item);

		createHostLms(randomUUID(), code, PolarisLmsClient.class,
			Optional.of(PolarisLmsClient.class), clientConfig);
	}

	public <T extends HostLmsClient, R extends IngestSource> DataHostLms createHostLms(
		UUID id, String code, Class<T> clientClass, Optional<Class<R>> ingestSourceClass,
		Map<String, Object> clientConfig) {

		return saveHostLms(DataHostLms.builder()
			.id(id)
			.code(code)
			.name("Test Host LMS")
			.lmsClientClass(clientClass.getCanonicalName())
			.ingestSourceClass(ingestSourceClass
				.map(Class::getCanonicalName)
				.orElse(null))
			.clientConfig(clientConfig)
			.build());
	}

	public DataHostLms saveHostLms(DataHostLms hostLms) {
		return singleValueFrom(hostLmsRepository.save(hostLms));
	}

	public void deleteAll() {
		agencyFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		numericRangeMappingFixture.deleteAll();

		dataAccess.deleteAll(hostLmsRepository.queryAll(),
			hostLms -> hostLmsRepository.delete(hostLms.getId()));
	}

	public HostLmsClient createClient(String code) {
		return singleValueFrom(hostLmsService.getClientFor(code));
	}

	@Nullable
	public IngestSource getIngestSource(String code) {
		return singleValueFrom(hostLmsService.getIngestSourceFor(code));
	}

	public HostLmsSierraApiClient createLowLevelSierraClient(String code, HttpClient client) {
		final var hostLms = findByCode(code);

		// Need to create a client directly
		// because injecting gives incorrectly configured client
		return new HostLmsSierraApiClient(hostLms, client);
	}

	public DataHostLms findByCode(String code) {
		return singleValueFrom(hostLmsService.findByCode(code));
	}
}
