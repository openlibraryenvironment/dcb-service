package org.olf.dcb.core.interaction.koha;

import org.olf.dcb.core.interaction.VirtualPatronEmail;

import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.model.HostLms;

import java.net.URI;
import java.util.List;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.booleanPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;

/** Config values for Koha LMS **/

public class KohaClientConfig {

	private static final HostLmsPropertyDefinition API_URL = urlPropertyDefinition("api-url", "Base API URL of the Koha system", TRUE);
	private static final HostLmsPropertyDefinition CLIENT_ID = stringPropertyDefinition("client_id", "Client ID for OAuth for the Koha system", TRUE);
	private static final HostLmsPropertyDefinition CLIENT_SECRET = stringPropertyDefinition("client_secret", "Client Secret for OAuth for the Koha system", TRUE);
	private static final HostLmsPropertyDefinition DCB_SHARING_LIBRARY_CODE
		= stringPropertyDefinition("sharing-library-code", "Library used to ship resources outside of Koha", TRUE);
	// The branch a virtual item is created at, used only when the borrowing patron's own
	// branch is unknown - see KohaHostLmsClient.virtualItemLibraryFor.
	//
	// There is deliberately no virtual-item-location-code here. Koha's createItem sets
	// home_library_id and holding_library_id and nothing else; a shelving location was
	// never sent, so the key was required configuration that no code path read.
	private static final HostLmsPropertyDefinition VIRTUAL_ITEM_LIBRARY_CODE = stringPropertyDefinition("virtual-item-library-code", "The library at which virtual items will be created", TRUE);

	/**
	 * The collection code DCB stamps on a virtual item to stop it being renewed, which has to
	 * match the value this Koha lists in its ItemsDeniedRenewal system preference. Optional
	 * because it only matters where renewal prevention is in use, and because a Koha that has
	 * not been configured for it is better served by the default than by failing to start.
	 * See KohaHostLmsClient.preventRenewalOnLoan for what the library has to configure.
	 */
	private static final HostLmsPropertyDefinition NO_RENEW_COLLECTION_CODE = stringPropertyDefinition(
		"no-renew-collection-code",
		"Collection code (items.ccode) DCB sets to deny renewal, matching this Koha's ItemsDeniedRenewal system preference",
		FALSE);

	public static final String DEFAULT_NO_RENEW_COLLECTION_CODE = "DCB_NO_RENEW";

	// The category a visiting patron is created in until patron type mappings exist. Koha
	// refuses the patron unless a category with exactly this code exists
	private static final HostLmsPropertyDefinition VIRTUAL_PATRON_CATEGORY = stringPropertyDefinition(
		"virtual-patron-category",
		"Koha patron category for a visiting patron whose patron type has no mapping yet", FALSE);

	public static final String DEFAULT_VIRTUAL_PATRON_CATEGORY = "DCB";

	// Shown to the owning library's staff on the item, so it is theirs to word and translate
	private static final HostLmsPropertyDefinition DO_NOT_RENEW_NOTE = stringPropertyDefinition(
		"do-not-renew-note", "Internal note set on an item DCB has stopped being renewed", FALSE);

	public static final String DEFAULT_DO_NOT_RENEW_NOTE = "Hold placed by owning library. Do not renew.";

	private static final HostLmsPropertyDefinition VIRTUAL_PATRON_EMAIL = stringPropertyDefinition(
		VirtualPatronEmail.SETTING, "Consortium mailbox for virtual patrons' notices", FALSE);

	private static final HostLmsPropertyDefinition BUNDLE_HOST_BARCODE = stringPropertyDefinition(
		"bundle-host-barcode",
		"Barcode of the never-lent item DCB bundles a returned virtual item into to check it in (ADR 0002)",
		FALSE);

	private static final HostLmsPropertyDefinition HONOUR_OPAC_HIDDEN_ITEMS = booleanPropertyDefinition(
		"honour-opac-hidden-items",
		"Leave out the copies OpacHiddenItems hides from the OPAC; needs RESTPublicAPI and RESTPublicAnonymousRequests",
		FALSE);



	private final HostLms hostLms;

	public KohaClientConfig(HostLms hostLms) {
		this.hostLms = hostLms;
	}

	URI getApiUrl() {
		return URI.create(API_URL.getRequiredConfigValue(hostLms));
	}
	String getClientId() {
		return CLIENT_ID.getRequiredConfigValue(hostLms);
	}
	String getClientSecret() {
		return CLIENT_SECRET.getRequiredConfigValue(hostLms);
	}
	List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			API_URL,
			CLIENT_ID,
			CLIENT_SECRET,
			DCB_SHARING_LIBRARY_CODE,
			VIRTUAL_ITEM_LIBRARY_CODE,
			NO_RENEW_COLLECTION_CODE,
			VIRTUAL_PATRON_CATEGORY,
			VIRTUAL_PATRON_EMAIL,
			DO_NOT_RENEW_NOTE,
			HONOUR_OPAC_HIDDEN_ITEMS,
			BUNDLE_HOST_BARCODE
		);
	}

	public String getDcbSharingLibraryCode() {
		return DCB_SHARING_LIBRARY_CODE.getRequiredConfigValue(hostLms);
	}

	public String getVirtualItemLibraryCode() {
		return VIRTUAL_ITEM_LIBRARY_CODE.getRequiredConfigValue(hostLms);
	}

	public String getDoNotRenewNote() {
		return DO_NOT_RENEW_NOTE.getOptionalValueFrom(hostLms.getClientConfig(), DEFAULT_DO_NOT_RENEW_NOTE);
	}

	public String getVirtualPatronEmail() {
		return VirtualPatronEmail.addressOrNull(
			VIRTUAL_PATRON_EMAIL.getOptionalValueFrom(hostLms.getClientConfig(), null));
	}

	public String getVirtualPatronCategory() {
		return VIRTUAL_PATRON_CATEGORY.getOptionalValueFrom(
			hostLms.getClientConfig(), DEFAULT_VIRTUAL_PATRON_CATEGORY);
	}

	public static final String DEFAULT_HONOUR_OPAC_HIDDEN_ITEMS = "true";

	// Absent means check-in is not configured: there is no default host item to fall back to
	public String getBundleHostBarcode() {
		final var barcode = BUNDLE_HOST_BARCODE.getOptionalValueFrom(hostLms.getClientConfig(), null);
		return barcode == null || barcode.isBlank() ? null : barcode.trim();
	}

	// Only "false" turns it off: a mistyped value must not offer the copies a library hides
	public boolean honoursOpacHiddenItems() {
		return !"false".equalsIgnoreCase(HONOUR_OPAC_HIDDEN_ITEMS
			.getOptionalValueFrom(hostLms.getClientConfig(), DEFAULT_HONOUR_OPAC_HIDDEN_ITEMS).trim());
	}

	public String getNoRenewCollectionCode() {
		return NO_RENEW_COLLECTION_CODE.getOptionalValueFrom(
			hostLms.getClientConfig(), DEFAULT_NO_RENEW_COLLECTION_CODE);
	}
}
