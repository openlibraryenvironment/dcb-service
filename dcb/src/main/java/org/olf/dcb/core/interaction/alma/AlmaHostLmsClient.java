package org.olf.dcb.core.interaction.alma;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.net.URI;
import java.util.List;

import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.*;

import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class AlmaHostLmsClient implements HostLmsClient {

	// @See https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/3234496514/ALMA+Integration

	// These are the same config keys as from FolioOaiPmhIngestSource
	// which was implemented prior to this client
	private static final HostLmsPropertyDefinition BASE_URL_SETTING
		= urlPropertyDefinition("base-url", "Base URL of the ALMA system", TRUE);
	private static final HostLmsPropertyDefinition API_KEY_SETTING
		= stringPropertyDefinition("apikey", "API key for this ALMA system", TRUE);

	private final HostLms hostLms;

	private final HttpClient httpClient;

	private final ReferenceValueMappingService referenceValueMappingService;
	private final ConversionService conversionService;
	private final AlmaApiClient client;

	private final String apiKey;
	private final URI rootUri;



	public AlmaHostLmsClient(@Parameter HostLms hostLms,
		@Parameter("client") HttpClient httpClient,
		AlmaClientFactory almaClientFactory,
		ReferenceValueMappingService referenceValueMappingService,
		ConversionService conversionService) {

		this.hostLms = hostLms;
		this.httpClient = httpClient;

		// this.consortialFolioItemMapper = consortialFolioItemMapper;
		this.client = almaClientFactory.createClientFor(hostLms);

		this.referenceValueMappingService = referenceValueMappingService;

		this.apiKey = API_KEY_SETTING.getRequiredConfigValue(hostLms);
		this.rootUri = UriBuilder.of(BASE_URL_SETTING.getRequiredConfigValue(hostLms)).build();
		this.conversionService = conversionService;
	}

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			BASE_URL_SETTING,
			API_KEY_SETTING
		);
	}



	
	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		return Mono.empty();
		// return getHoldings(bib.getSourceRecordId())
		// 	.flatMap(outerHoldings -> checkResponse(outerHoldings, bib.getSourceRecordId()))
		// 	.mapNotNull(OuterHoldings::getHoldings)
		// 	.flatMapMany(Flux::fromIterable)
		// 	.flatMap(this::mapHoldingsToItems)
		// 	.collectList();
	}


	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters) {

		log.debug("placeHoldRequestAtSupplyingAgency({})", parameters);
		return Mono.empty();
	}


	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {

		log.debug("placeHoldRequestAtBorrowingAgency({})", parameters);

		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtPickupAgency({})", parameters);
		return Mono.empty();
	}


	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		return raiseError(new UnsupportedOperationException("placeHoldRequestAtLocalAgency not supported by hostlms: " + getHostLmsCode()));
	}


	/** ToDo: This should be a default method I think */
	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {

		if (canonicalPatronType == null) {
			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("patronType", "DCB", canonicalPatronType, "patronType", getHostLmsCode())
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
				"Unable to map canonical patron type \"" + canonicalPatronType + "\" to a patron type on Host LMS: \"" + getHostLmsCode() + "\"",
				getHostLmsCode(), canonicalPatronType)));
	}


	/** ToDo: This should be a default method I think */
	@Override
	public Mono<String> findCanonicalPatronType(String localPatronType, String localId) {
		String hostLmsCode = getHostLmsCode();
		if (localPatronType == null) {
			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("patronType",
			hostLmsCode, localPatronType, "patronType", "DCB")
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
				"Unable to map patron type \"" + localPatronType + "\" on Host LMS: \"" + hostLmsCode + "\" to canonical value",
				hostLmsCode, localPatronType)));
	}


	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByUsername(String localUsername) {
		return Mono.empty();
	}


	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		return Mono.empty();
	}


	@Override
	public Mono<String> createPatron(Patron patron) {
		// POST /almaws/v1/users
		return Mono.empty();
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		return Mono.empty();
	}


	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		log.debug("{} cancelHoldRequest({})", getHostLms().getName(), parameters);
		return Mono.empty();
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		log.warn("Renewal is not currently implemented for {}", getHostLms().getName());
		return Mono.just(hostLmsRenewal);
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		log.warn("Update patron request is not currently implemented for {}", getHostLms().getName());
		return Mono.empty();
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		// DCB has no means to update users via the available edge modules
		// and edge-dcb does not do this on DCB's behalf when creating the transaction
		log.warn("NOOP: updatePatron called for hostlms {} localPatronId {} localPatronType {}",
			getHostLms().getName(), localId, patronType);

		return Mono.empty();
	}


	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {
		return Mono.empty();
	}


	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		return Mono.empty();
	}


	@Override
	public Mono<HostLmsRequest> getRequest(String localRequestId) {
		return Mono.empty();
	}


	@Override
	public Mono<HostLmsItem> getItem(String localItemId, String localRequestId) {
		log.debug("getItem({}, {})", localItemId, localRequestId);

		

		return Mono.empty();
	}


	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		return Mono.empty();
	}


	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		return Mono.empty();
	}

	@Override
	public Mono<String> deleteItem(String id) {
		log.info("Delete virtual item is not currently implemented");
		return Mono.just("OK");
	}

  @Override
  public Mono<String> deleteHold(String id) {
		log.info("Delete hold is not currently implemented");
		return Mono.empty();
  }

	@Override
	public Mono<String> deleteBib(String id) {
		log.info("Delete virtual bib is not currently implemented");
		return Mono.empty();
	}

	@Override
	public @NonNull String getClientId() {
			return "";
	}

  @Override
  public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
    return Mono.empty();
  }

  @Override
  public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
    log.debug("ALMA Supplier Preflight {} {} {} {}",borrowingAgencyCode,supplyingAgencyCode,canonicalItemType,canonicalPatronType);
    return Mono.just(Boolean.TRUE);
  }

}
