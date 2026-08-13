package org.olf.dcb.core.interaction.polaris;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.olf.dcb.core.interaction.polaris.exceptions.PolarisConfigurationException;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Serdeable
@NoArgsConstructor
@AllArgsConstructor
public class PolarisConfig {
	// No default-agency-code here on purpose. Polaris reads it, but through
	// HostLmsClient.getDefaultAgencyCode against the raw client config - which is also
	// where the shared-system guard lives. A second binding of the same key here was
	// never read and would only invite someone to use the unguarded one.
	@JsonProperty("roles")
	private List<String> roles;
	@JsonProperty("ingest")
	private Boolean ingest;
	@JsonProperty("base-url")
	private Object baseUrl;
	@JsonProperty("base-url-application-services")
	private String overrideBaseUrl;
	@JsonProperty("page-size")
	private Integer pageSize;
	@JsonProperty("staff-username")
	private String staffUsername;
	@JsonProperty("staff-password")
	private String staffPassword;
	@JsonProperty("domain-id")
	private String domainId;
	@JsonProperty("access-id")
	private String accessId;
	@JsonProperty("access-key")
	private String accessKey;
	@JsonProperty("logon-branch-id")
	private Object logonBranchId;
	@JsonProperty("virtual-patron-branch-id")
	private Object virtualPatronBranchId;
	@JsonProperty("logon-user-id")
	private Object logonUserId;
	@JsonProperty("contextHierarchy")
	private List<String> contextHierarchy;
	@JsonProperty("borrower-lending-flow")
	private String borrowerLendingFlow;
	@JsonProperty("hold-fetching-delay")
	private Integer holdFetchingDelay;
	@JsonProperty("hold-fetching-max-retry")
	private Integer holdFetchingMaxRetry;
	@JsonProperty("papi")
	private PapiConfig papi;
	@JsonProperty("services")
	private ServicesConfig services;
	@JsonProperty("item")
	private ItemConfig item;

	/**
	 * if present, this list is used to provide inferences from shelving location to requestability.
	 * Some shelves are "Reference only" some can be "Local Patron Only" etc.
	 */
	@JsonProperty("shelfLocationPolicyMap")
	private Map<String,String> shelfLocationPolicyMap;

	/**
	 * Minutes to rewind the harvest watermark by when the delta window rolls forward.
	 * Covers records modified during an id walk and clock skew between DCB and Polaris.
	 * Every minute configured here is re-harvested on EVERY cycle, and the source record import
	 * job runs on a two minute fixed delay - so keep this tight. Default 15.
	 */
	@JsonProperty("harvest-watermark-overlap-minutes")
	private Integer harvestWatermarkOverlapMinutes;
	public Duration getHarvestWatermarkOverlap() {
		return Duration.ofMinutes(valueWithDefault(harvestWatermarkOverlapMinutes, Integer.class, 15));
	}

	/**
	 * Handling of the "double delete" policy
	 * If enabled then we can "hard delete" records such as virtual items
	 * Otherwise default to the "soft delete" approach to records
	 */
	@JsonProperty("enableDoubleDelete")
	private Boolean enableDoubleDelete;
	public Boolean isDoubleDeleteEnabled() { return valueWithDefault(enableDoubleDelete, Boolean.class, false); }

	public String getBaseUrl() {

		return requiredValue("Base Url", this.baseUrl, String.class);
	}

	public Integer getLogonBranchId() {

		return requiredValue("Logon Branch ID", this.logonBranchId, Integer.class);
	}

	public Integer getLogonUserId() {

		return requiredValue("Logon User ID", this.logonUserId, Integer.class);
	}

	public String getPapiVersion() {

		final var value = getNestedProperty(getPapi(), PapiConfig::getPapiVersion);

		return valueWithDefault(value, String.class, "v1");
	}

	public String getPapiLangId() {

		final var value = getNestedProperty(getPapi(), PapiConfig::getLangId);

		return valueWithDefault(value, String.class, "1033");
	}

	public String getPapiAppId() {

		final var value = getNestedProperty(getPapi(), PapiConfig::getAppId);

		return valueWithDefault(value, String.class, "100");
	}

	public String getPapiOrgId() {

		final var value = getNestedProperty(getPapi(), PapiConfig::getOrgId);

		return valueWithDefault(value, String.class, "1");
	}

	public String getPatronBarcodePrefix(String defaultValue) {

		final var value = getNestedProperty(getServices(), ServicesConfig::getPatronBarcodePrefix);

		return valueWithDefault(value, String.class, defaultValue);
	}

	public String getServicesVersion() {

		final var value = getNestedProperty(getServices(), ServicesConfig::getServicesVersion);

		return valueWithDefault(value, String.class, "v1");
	}

	public String getServicesLanguage() {

		final var value = getNestedProperty(getServices(), ServicesConfig::getLanguage);

		return valueWithDefault(value, String.class, "eng");
	}

	public String getServicesProductId() {

		final var value = getNestedProperty(getServices(), ServicesConfig::getProductId);

		return valueWithDefault(value, String.class, "19");
	}

	public String getServicesSiteDomain() {

		final var value = getNestedProperty(getServices(), ServicesConfig::getSiteDomain);

		return valueWithDefault(value, String.class, "polaris");
	}

	public String getServicesOrganisationId() {

		final var value = getNestedProperty(getServices(), ServicesConfig::getOrganisationId);

		return requiredValue("Services Organisation Id", value, String.class);
	}

	public Integer getServicesWorkstationId() {

		final var value = getNestedProperty(getServices(), ServicesConfig::getWorkstationId);

		return valueWithDefault(value, Integer.class, 1);
	}

	public Integer getItemRenewalLimit() {

		final var value = getNestedProperty(getItem(), ItemConfig::getRenewalLimit);

		return requiredValue("Item Renewal Limit", value, Integer.class);
	}

	public Integer getItemFindCodeId() {

		final var value = getNestedProperty(getItem(), ItemConfig::getFineCodeId);

		return requiredValue("Item Fine Code ID", value, Integer.class);
	}

	public Integer getItemHistoryActionId() {

		final var value = getNestedProperty(getItem(), ItemConfig::getHistoryActionId);

		return requiredValue("Item History Action ID", value, Integer.class);
	}

	public Integer getItemLoanPeriodCodeId() {

		final var value = getNestedProperty(getItem(), ItemConfig::getLoanPeriodCodeId);

		return requiredValue("Item Loan Period Code ID", value, Integer.class);
	}

	public Integer getItemAvLoanPeriodCodeId() {

		final var value = getNestedProperty(getItem(), ItemConfig::getAvLoanPeriodCodeId);

		return requiredValue("Item AV Loan Period Code ID", value, Integer.class);
	}

	public Integer getItemShelvingSchemeId() {

		final var value = getNestedProperty(getItem(), ItemConfig::getShelvingSchemeId);

		return requiredValue("Item Shelving Scheme ID", value, Integer.class);
	}

	public String getItemBarcodePrefix() {

		final var value = getNestedProperty(getItem(), ItemConfig::getBarcodePrefix);

		return optionalValue(value, String.class);
	}

	public Integer getIllLocationId() {

		final var value = getNestedProperty(getItem(), ItemConfig::getIllLocationId);

		return requiredValue("Ill Location ID", value, Integer.class);
	}

	/**
	 * The branch a virtual patron is registered at. Polaris validates the patron code against this
	 * branch (error -3612 "PatronCodeID does not exist for patron's branch" when it does not match),
	 * so it must be a branch where the DCB patron types are defined. Defaults to the ILL location -
	 * the branch DCB already uses for patron defaults and checkout logon, and where the DCB patron
	 * types are configured. Override only when a deployment defines its DCB patron types at a
	 * different branch.
	 */
	public Integer getVirtualPatronBranchId() {

		return valueWithDefault(this.virtualPatronBranchId, Integer.class, getIllLocationId());
	}

	public Integer getHoldFetchingDelay(Integer defaultDelay) {

		return valueWithDefault(this.holdFetchingDelay, Integer.class, defaultDelay);
	}

	public Integer getMaxHoldFetchingRetry(Integer defaultMaxRetry) {

		return valueWithDefault(this.holdFetchingMaxRetry, Integer.class, defaultMaxRetry);
	}

	public Integer getItemAvRenewalLimit()
	{
		final var value = getNestedProperty(getItem(), ItemConfig::getRenewalLimit);
		return requiredValue("AV item Renewal Limit", value, Integer.class);
	}



	@Data
	@NoArgsConstructor
	@Serdeable
	public static class PapiConfig {
		@JsonProperty("papi-version")
		private Object papiVersion;
		@JsonProperty("lang-id")
		private Object langId;
		@JsonProperty("app-id")
		private Object appId;
		@JsonProperty("org-id")
		private Object orgId;
	}

	@Data
	@NoArgsConstructor
	@Serdeable
	public static class ServicesConfig {
		@JsonProperty("patron-barcode-prefix")
		private Object patronBarcodePrefix;
		@JsonProperty("services-version")
		private Object servicesVersion;
		@JsonProperty("language")
		private Object language;
		@JsonProperty("product-id")
		private Object productId;
		@JsonProperty("site-domain")
		private Object siteDomain;
		@JsonProperty("organisation-id")
		private Object organisationId;
		@JsonProperty("workstation-id")
		private Object workstationId;
	}

	@Data
	@Builder
	@Serdeable
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ItemConfig {
		@JsonProperty("renewal-limit")
		private Object renewalLimit;
		@JsonProperty("av-renewal-limit")
		private Object avRenewalLimit;
		@JsonProperty("fine-code-id")
		private Object fineCodeId;
		@JsonProperty("history-action-id")
		private Object historyActionId;
		@JsonProperty("loan-period-code-id")
		private Object loanPeriodCodeId;
		@JsonProperty("av-loan-period-code-id")
		private Object avLoanPeriodCodeId;
		@JsonProperty("shelving-scheme-id")
		private Object shelvingSchemeId;
		@JsonProperty("barcode-prefix")
		private Object barcodePrefix;
		@JsonProperty("ill-location-id")
		private Object illLocationId;

		/** Should enrichItemWithAgency in PolarisItemMapper use the strategy set out in DCB-1675 or the legacy 
		 * locationCode method. Default to the DCB-1675 method. N.B. This method relies upon the catalog instance (Which
     * may be different to the Circ Instance for shared systems, or the same for singletons) to have the required
     * Location to Agency mapping values installed. N.B. setting this to null in config will override the default */
		// NB: @Builder.Default is required. Without it Lombok discards the initialiser
		// entirely for builder-constructed instances (it warns about this at compile
		// time), so itemAgencyResolutionMethod arrives null and PolarisItemMapper's
		// switch on it throws NullPointerException.
		@JsonProperty("item-agency-resolution-method")
		@Builder.Default
		private String itemAgencyResolutionMethod = "Legacy";
	}

	public String applicationServicesUriParameters() {
		return "/" + this.getServicesVersion() +
			"/" + this.getServicesLanguage() +
			"/" + this.getServicesProductId() +
			"/" + this.getServicesSiteDomain() +
			"/" + this.getServicesOrganisationId() +
			"/" + this.getServicesWorkstationId();
	}

	public String pAPIServiceUriParameters() {
		return "/" + this.getPapiVersion() +
			"/" + this.getPapiLangId() +
			"/" + this.getPapiAppId() +
			"/" + this.getPapiOrgId();
	}

	private <T, R> R getNestedProperty(T parent, Function<T, R> extractor) {
		return parent != null ? extractor.apply(parent) : null;
	}

	static <T> T valueWithDefault(Object value, Class<T> type, Object defval) {
		final Object r1 = optionalValue(value,type);
		return type.cast( r1 != null ? r1 : defval );
	}

	static <T> T requiredValue(String name, Object value, Class<T> type) {

		T t = getT(value, type);
		if (t != null) return t;

		throw new PolarisConfigurationException("Missing required config value: " + name);
	}

	static <T> T optionalValue(Object value, Class<T> type) {
		return getT(value, type);
	}

	private static <T> T getT(Object value, Class<T> type) {
		if (value != null) {
			if (type.isInstance(value)) {
				return type.cast(value);
			} else if (type == String.class && value instanceof Integer) {
				return type.cast(value.toString());
			} else if (type == Integer.class && value instanceof String) {
				return type.cast(Integer.valueOf((String) value));
			}
		}
		return null;
	}
}
