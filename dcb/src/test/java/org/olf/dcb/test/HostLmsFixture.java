package org.olf.dcb.test;

import static java.util.UUID.randomUUID;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


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

	public void deleteAllHostLMS() {
		dataAccess.deleteAll(agencyRepository.findAll(),
			agency -> agencyRepository.delete(agency.getId()));

		patronFixture.deleteAllPatrons();

		dataAccess.deleteAll(numericRangeMappingRepository.findAll(),
			nm -> numericRangeMappingRepository.delete(nm.getId()));

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
