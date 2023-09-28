package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.polaris.papi.PAPILmsClient;
import org.olf.dcb.core.interaction.sierra.HostLmsSierraApiClient;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.NumericRangeMappingRepository;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.client.HttpClient;
import services.k_int.utils.UUIDUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Prototype
public class HostLmsFixture {

        private final Logger log = LoggerFactory.getLogger(HostLmsFixture.class);

	private final DataAccess dataAccess = new DataAccess();

	private final HostLmsRepository hostLmsRepository;
	private final HostLmsService hostLmsService;
	private final AgencyRepository agencyRepository;
	private final PatronFixture patronFixture;
	private final NumericRangeMappingRepository numericRangeMappingRepository;

	public HostLmsFixture(HostLmsRepository hostLmsRepository,
		HostLmsService hostLmsService, AgencyRepository agencyRepository,
		PatronFixture patronFixture,
                NumericRangeMappingRepository numericRangeMappingRepository) {

		this.hostLmsRepository = hostLmsRepository;
		this.hostLmsService = hostLmsService;
		this.agencyRepository = agencyRepository;
		this.patronFixture = patronFixture;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	public DataHostLms createHostLms(DataHostLms hostLms) {
		return singleValueFrom(hostLmsRepository.save(hostLms));
	}

        public NumericRangeMapping createNumericRangeMapping(String system, String domain, Long lowerBound, Long upperBound, String targetContext, String targetValue) {

                log.debug("createNumericRangeMapping({},{},{},{},{},{})",system,domain,lowerBound,upperBound,targetContext,targetValue);

                NumericRangeMapping nrm = NumericRangeMapping.builder()
                        .id(UUIDUtils.dnsUUID(system+":"+":"+domain+":"+targetContext+":"+lowerBound))
                        .context(system)
                        .domain(domain)
                        .lowerBound(lowerBound)
                        .upperBound(upperBound)
                        .targetContext(targetContext)
                        .mappedValue(targetValue)
                        .build();

                return singleValueFrom(numericRangeMappingRepository.save(nrm));
        }

	public DataHostLms createHostLms(UUID id, String code) {
		return createHostLms(new DataHostLms(id, code, "Test Host LMS",
			SierraLmsClient.class.getCanonicalName(), Map.of()));
	}

	public DataHostLms createSierraHostLms(String username, String password,
		String host, String code) {

		DataHostLms result = createHostLms(
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

                log.debug("Creating numeric range mapping");
                createNumericRangeMapping(code,"ItemType", Long.valueOf(998), Long.valueOf(1001), "DCB", "BKM");

                return result;
	}

	public DataHostLms createPAPIHostLms(String staffUsername, String staffPassword,
		String host, String code, String domain, String accessId, String accessKey) {

		Map<String, Object> clientConfig = new HashMap<>();
		clientConfig.put("staff-username", staffUsername);
		clientConfig.put("staff-password", staffPassword);
		clientConfig.put("base-url", host);
		clientConfig.put("domain-id", domain);
		clientConfig.put("access-id", accessId);
		clientConfig.put("access-key", accessKey);
		clientConfig.put("version", "v1");
		clientConfig.put("lang-id", "1033");
		clientConfig.put("app-id", "100");
		clientConfig.put("org-id", "1");
		clientConfig.put("page-size", 100);

		return createHostLms(
			DataHostLms.builder()
				.id(randomUUID())
				.code(code)
				.name(code)
				.lmsClientClass(PAPILmsClient.class.getCanonicalName())
				.clientConfig(clientConfig)
				.build());
	}

	public void deleteAllHostLMS() {
		dataAccess.deleteAll(agencyRepository.queryAll(),
			agency -> agencyRepository.delete(agency.getId()));

		patronFixture.deleteAllPatrons();

		dataAccess.deleteAll(numericRangeMappingRepository.queryAll(),
			nm -> numericRangeMappingRepository.delete(nm.getId()));

		dataAccess.deleteAll(hostLmsRepository.queryAll(),
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
