package org.olf.dcb.request.lifecycle.ncip.profile.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.olf.dcb.request.lifecycle.ncip.profile.api.DcbProfileRegistrationApi;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationException;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationProperties;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;

@Singleton
public class DcbProfileDirectoryPullService {
	private static final int MAX_DOCUMENT_BYTES = 1_048_576;
	private static final Set<String> SENSITIVE_CHANGE_FIELDS = Set.of(
		"issuer", "jwksUrl", "ncipOrigin", "oaiOrigin", "selectedSymbol", "authProfile");

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final DcbProfileRegistrationProperties properties;
	private final DcbPeerAuthProperties peerAuthProperties;

	public DcbProfileDirectoryPullService(
		@Client("/") HttpClient httpClient,
		ObjectMapper objectMapper,
		DcbProfileRegistrationProperties properties,
		DcbPeerAuthProperties peerAuthProperties
	) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.peerAuthProperties = peerAuthProperties;
	}

	public ValidatedRegistration validate(
		DcbProfileMembership invitation,
		DcbProfileRegistrationApi.RegistrationRequest request,
		String proof
	) {
		String authProfile = DcbProfileAuthPolicy.resolve(invitation, request.authProfile());
		ValidatedRegistration validated = resolve(
			invitation,
			request.directoryUrl(),
			request.selectedSymbol(),
			request.locations(),
			authProfile,
			!blank(request.authProfile()));
		if (!MessageDigest.isEqual(
			validated.descriptorHash().getBytes(StandardCharsets.US_ASCII),
			request.descriptorHash().getBytes(StandardCharsets.US_ASCII))) {
			throw DcbProfileRegistrationException.conflict(
				"DESCRIPTOR_HASH_MISMATCH",
				"ORS profile changed after readiness; run readiness again.",
				"descriptorHash");
		}
		Map<String, Object> jwks = fetchJson(
			guardedUri(validated.jwksUrl(), "peerAuthJwksUrl"),
			"JWKS_UNAVAILABLE");
		verifyProof(
			invitation,
			request,
			proof,
			validated.descriptor(),
			validated.descriptorHash(),
			jwks);
		return validated;
	}

	public ValidatedRegistration pull(DcbProfileMembership membership) {
		boolean includeAuthProfile = membership.getApprovedDescriptor() != null
			&& membership.getApprovedDescriptor().containsKey("authProfile");
		List<DcbProfileRegistrationApi.LocationSelection> locations = maps(
			membership.getApprovedDescriptor() != null
				? membership.getApprovedDescriptor().get("locations")
				: null)
			.stream()
			.map(location -> new DcbProfileRegistrationApi.LocationSelection(
				text(location, "sourceCode"),
				text(location, "dcbCode"),
				bool(location, "pickup"),
				bool(location, "supplying")))
			.toList();
		return resolve(
			membership,
			membership.getRemoteDirectoryUrl(),
			membership.getSelectedSymbol(),
			locations,
			DcbProfileAuthPolicy.approvedOrDefault(membership),
			includeAuthProfile);
	}

	private ValidatedRegistration resolve(
		DcbProfileMembership invitation,
		String directoryUrl,
		String requestedSymbol,
		List<DcbProfileRegistrationApi.LocationSelection> locationSelections,
		String authProfile,
		boolean includeAuthProfile
	) {
		URI directoryUri = guardedUri(directoryUrl, "directoryUrl");
		Map<String, Object> page = fetchJson(
			UriBuilder.of(directoryUri).replaceQueryParam("self", true).build(),
			"DIRECTORY_UNAVAILABLE");
		List<Map<String, Object>> entries = maps(page.get("content"));
		List<Map<String, Object>> selfEntries = entries.stream()
			.filter(entry -> bool(entry, "isSelf"))
			.filter(entry -> !Boolean.FALSE.equals(entry.get("isPublic")))
			.toList();
		if (selfEntries.size() != 1) {
			throw DcbProfileRegistrationException.invalid(
				"SELF_ENTRY_REQUIRED",
				"Public directory must contain exactly one public self entry.",
				"directory.content[].isSelf");
		}

		Map<String, Object> self = selfEntries.getFirst();
		String address = requireDirectoryAddress(self);
		String selectedSymbol = normalizeSymbol(requestedSymbol);
		requireSelectedSymbol(self, selectedSymbol);
		Map<String, Object> ncip = requireNcipService(self, selectedSymbol);
		Map<String, Object> oai = requireOaiService(self, selectedSymbol);
		Map<String, Object> ncipConfig = map(ncip.get("config"));
		Map<String, Object> oaiConfig = map(oai.get("config"));
		requireText(ncipConfig, "ncipSystemId", "NCIP_SYSTEM_ID_REQUIRED");
		requireText(ncipConfig, "ncipAgencyId", "NCIP_AGENCY_ID_REQUIRED");
		requireText(ncipConfig, "peerAuthIssuer", "ISSUER_REQUIRED");
		requireText(ncipConfig, "peerAuthJwksUrl", "JWKS_URL_REQUIRED");
		requireText(ncipConfig, "peerAuthSubject", "JWT_SUBJECT_REQUIRED");
		requireText(ncipConfig, "peerAuthOutboundAudience", "JWT_AUDIENCE_REQUIRED");
		if (!"JWT_REQUIRED".equalsIgnoreCase(text(ncip, "authMechanism"))) {
			throw DcbProfileRegistrationException.invalid(
				"JWT_REQUIRED", "NCIP service must require JWT authentication.", "services.authMechanism");
		}
		if (!containsIgnoreCase(strings(oaiConfig.get("metadataPrefixes")), "marcxml")) {
			throw DcbProfileRegistrationException.invalid(
				"OAI_MARCXML_REQUIRED", "OAI-PMH service must advertise marcxml.", "services.config.metadataPrefixes");
		}

		String hostLmsCode = policyText(invitation, "hostLmsCode");
		String ncipEndpoint = resolveEndpoint(ncip, ncipConfig, "endpointTemplate", hostLmsCode);
		String oaiEndpoint = resolveEndpoint(oai, oaiConfig, "endpointTemplate", hostLmsCode);
		guardedUri(ncipEndpoint, "ncipEndpoint");
		guardedUri(oaiEndpoint, "oaiEndpoint");
		guardedUri(text(ncipConfig, "peerAuthJwksUrl"), "peerAuthJwksUrl");

		List<Map<String, Object>> resolvedLocations = resolveLocations(
			self,
			locationSelections,
			policyBoolean(invitation, "borrowingAllowed"),
			policyBoolean(invitation, "supplyingAllowed"));

		Map<String, Object> descriptor = descriptor(
			self,
			selectedSymbol,
			ncipEndpoint,
			oaiEndpoint,
			ncipConfig,
			resolvedLocations,
			authProfile,
			includeAuthProfile);
		String descriptorHash = descriptorHash(descriptor);

		return new ValidatedRegistration(
			descriptor,
			descriptorHash,
			URI.create(directoryUrl).resolve("/").toString(),
			directoryUrl,
			text(ncipConfig, "peerAuthIssuer"),
			text(self, "slug"),
			selectedSymbol,
			ncipEndpoint,
			oaiEndpoint,
			text(ncipConfig, "ncipSystemId"),
			text(ncipConfig, "ncipAgencyId"),
			text(ncipConfig, "peerAuthJwksUrl"),
			text(ncipConfig, "peerAuthOutboundAudience"),
			text(ncipConfig, "peerAuthInboundAudience"),
			text(ncipConfig, "peerAuthSubject"),
			text(self, "commonName"),
			address,
			resolvedLocations,
			authProfile
		);
	}

	public Set<String> sensitiveChanges(Map<String, Object> approved, Map<String, Object> proposed) {
		if (approved == null) {
			return Set.of();
		}
		java.util.LinkedHashSet<String> changed = new java.util.LinkedHashSet<>();
		for (String field : SENSITIVE_CHANGE_FIELDS) {
			if (!Objects.equals(approved.get(field), proposed.get(field))) {
				changed.add(field);
			}
		}
		return Set.copyOf(changed);
	}

	public String descriptorHash(Map<String, Object> descriptor) {
		try {
			byte[] canonical = objectMapper.writeValueAsBytes(canonicalize(descriptor));
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not hash DCB profile descriptor", exception);
		}
	}

	private void verifyProof(
		DcbProfileMembership invitation,
		DcbProfileRegistrationApi.RegistrationRequest request,
		String proof,
		Map<String, Object> descriptor,
		String descriptorHash,
		Map<String, Object> jwks
	) {
		if (proof == null || proof.isBlank()) {
			throw DcbProfileRegistrationException.unauthorized(
				"REGISTRATION_PROOF_REQUIRED", "ORS registration proof is required.");
		}
		try {
			SignedJWT jwt = SignedJWT.parse(proof);
			if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
				throw new IllegalArgumentException("proof algorithm must be RS256");
			}
			JWKSet keySet = JWKSet.parse(objectMapper.writeValueAsString(jwks));
			JWK key = jwt.getHeader().getKeyID() != null
				? keySet.getKeyByKeyId(jwt.getHeader().getKeyID())
				: keySet.getKeys().stream().findFirst().orElse(null);
			if (!(key instanceof RSAKey rsaKey)
				|| !jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))) {
				throw new IllegalArgumentException("proof signature is invalid");
			}
			var claims = jwt.getJWTClaimsSet();
			Instant now = Instant.now();
			Date issuedAt = claims.getIssueTime();
			Date expiresAt = claims.getExpirationTime();
			if (issuedAt == null || expiresAt == null
				|| issuedAt.toInstant().isAfter(now.plusSeconds(30))
				|| expiresAt.toInstant().isBefore(now)
				|| expiresAt.toInstant().isAfter(now.plusSeconds(330))) {
				throw new IllegalArgumentException("proof lifetime is invalid");
			}
			requireClaim(claims.getIssuer(), descriptor.get("issuer"), "issuer");
			requireClaim(claims.getSubject(), descriptor.get("ncipSystemId"), "subject");
			if (!claims.getAudience().contains(nodeId())) {
				throw new IllegalArgumentException("proof audience does not include this DCB");
			}
			requireClaim(claims.getStringClaim("invitationId"), invitation.getId().toString(), "invitationId");
			requireClaim(claims.getStringClaim("profile"), DcbProfileRegistrationApi.PROFILE_ID, "profile");
			requireClaim(claims.getStringClaim("directoryUrl"), request.directoryUrl(), "directoryUrl");
			requireClaim(normalizeSymbol(claims.getStringClaim("selectedSymbol")),
				normalizeSymbol(request.selectedSymbol()), "selectedSymbol");
			requireClaim(claims.getStringClaim("descriptorHash"), descriptorHash, "descriptorHash");
			if (claims.getJWTID() == null || claims.getJWTID().isBlank()) {
				throw new IllegalArgumentException("proof jti is required");
			}
		} catch (DcbProfileRegistrationException exception) {
			throw exception;
		} catch (Exception exception) {
			throw DcbProfileRegistrationException.unauthorized(
				"REGISTRATION_PROOF_INVALID", "ORS registration proof is invalid: " + exception.getMessage());
		}
	}

	private List<Map<String, Object>> resolveLocations(
		Map<String, Object> self,
		List<DcbProfileRegistrationApi.LocationSelection> selections,
		boolean borrowing,
		boolean supplying
	) {
		Map<String, Map<String, Object>> available = new LinkedHashMap<>();
		for (Map<String, Object> location : maps(self.get("locations"))) {
			String code = text(location, "code");
			if (code != null) {
				available.put(code.toLowerCase(Locale.ROOT), location);
			}
		}
		if ((borrowing || supplying) && available.isEmpty()) {
			throw DcbProfileRegistrationException.invalid(
				"LOCATION_REQUIRED", "Public directory has no selectable locations.", "directory.locations");
		}
		List<Map<String, Object>> resolved = new ArrayList<>();
		for (DcbProfileRegistrationApi.LocationSelection selection : selections) {
			Map<String, Object> source = available.get(selection.sourceCode().toLowerCase(Locale.ROOT));
			if (source == null) {
				throw DcbProfileRegistrationException.invalid(
					"LOCATION_NOT_ADVERTISED",
					"Selected location is not present in the public directory: " + selection.sourceCode(),
					"locations.sourceCode");
			}
			Map<String, Object> location = new LinkedHashMap<>(source);
			location.put("sourceCode", selection.sourceCode());
			location.put("dcbCode", blank(selection.dcbCode()) ? selection.sourceCode() : selection.dcbCode());
			location.put("pickup", selection.pickup());
			location.put("supplying", selection.supplying());
			resolved.add(location);
		}
		if (borrowing && resolved.stream().noneMatch(location -> bool(location, "pickup"))) {
			throw DcbProfileRegistrationException.invalid(
				"PICKUP_LOCATION_REQUIRED", "Borrowing membership requires a pickup location.", "locations.pickup");
		}
		if (supplying && resolved.stream().noneMatch(location -> bool(location, "supplying"))) {
			throw DcbProfileRegistrationException.invalid(
				"SUPPLYING_LOCATION_REQUIRED", "Supplying membership requires a supplying location.", "locations.supplying");
		}
		return List.copyOf(resolved);
	}

	Map<String, Object> descriptor(
		Map<String, Object> self,
		String selectedSymbol,
		String ncipEndpoint,
		String oaiEndpoint,
		Map<String, Object> ncipConfig,
		List<Map<String, Object>> locations,
		String authProfile,
		boolean includeAuthProfile
	) {
		Map<String, Object> descriptor = new LinkedHashMap<>();
		descriptor.put("profile", DcbProfileRegistrationApi.PROFILE_ID);
		descriptor.put("profileVersion", DcbProfileRegistrationApi.PROFILE_VERSION);
		descriptor.put("selfSlug", text(self, "slug"));
		descriptor.put("commonName", text(self, "commonName"));
		descriptor.put("address", requireDirectoryAddress(self));
		descriptor.put("selectedSymbol", selectedSymbol);
		if (includeAuthProfile) {
			descriptor.put("authProfile", authProfile);
		}
		descriptor.put("ncipEndpoint", ncipEndpoint);
		descriptor.put("ncipOrigin", origin(ncipEndpoint));
		descriptor.put("ncipSystemId", text(ncipConfig, "ncipSystemId"));
		descriptor.put("ncipAgencyId", text(ncipConfig, "ncipAgencyId"));
		descriptor.put("issuer", text(ncipConfig, "peerAuthIssuer"));
		descriptor.put("jwksUrl", text(ncipConfig, "peerAuthJwksUrl"));
		descriptor.put("outboundAudience", text(ncipConfig, "peerAuthOutboundAudience"));
		descriptor.put("inboundAudience", text(ncipConfig, "peerAuthInboundAudience"));
		descriptor.put("peerSubject", text(ncipConfig, "peerAuthSubject"));
		descriptor.put("oaiEndpoint", oaiEndpoint);
		descriptor.put("oaiOrigin", origin(oaiEndpoint));
		descriptor.put("locations", locations);
		return descriptor;
	}

	private String requireDirectoryAddress(Map<String, Object> self) {
		Map<String, Object> address = map(self.get("address"));
		Map<String, Object> printable = map(address.get("printable"));
		List<String> lines = strings(printable.get("labelLines"));
		String formatted = lines.isEmpty()
			? text(map(address.get("input")), "freeform")
			: String.join(", ", lines);
		if (blank(formatted)) {
			throw DcbProfileRegistrationException.invalid(
				"DIRECTORY_ADDRESS_REQUIRED",
				"Public directory self entry must include a printable address.",
				"directory.content[].address");
		}
		return formatted;
	}

	private Map<String, Object> requireNcipService(Map<String, Object> self, String selectedSymbol) {
		return maps(self.get("services")).stream()
			.filter(service -> "NCIP2".equalsIgnoreCase(text(service, "type")))
			.filter(service -> {
				Map<String, Object> config = map(service.get("config"));
				return DcbProfileRegistrationApi.PROFILE_ID.equalsIgnoreCase(text(config, "profile"))
					&& Integer.valueOf(DcbProfileRegistrationApi.PROFILE_VERSION)
						.equals(integer(config.get("profileVersion")));
			})
			.filter(service -> enabledFor(service, selectedSymbol))
			.findFirst()
			.orElseThrow(() -> DcbProfileRegistrationException.invalid(
				"NCIP_PROFILE_SERVICE_REQUIRED",
				"Selected symbol has no enabled DCB Profile NCIP2.02+ service.",
				"directory.services"));
	}

	private Map<String, Object> requireOaiService(Map<String, Object> self, String selectedSymbol) {
		return maps(self.get("services")).stream()
			.filter(service -> "OAI-PMH".equalsIgnoreCase(text(service, "type")))
			.filter(service -> enabledFor(service, selectedSymbol))
			.findFirst()
			.orElseThrow(() -> DcbProfileRegistrationException.invalid(
				"OAI_SERVICE_REQUIRED",
				"Selected symbol has no enabled OAI-PMH service.",
				"directory.services"));
	}

	private boolean enabledFor(Map<String, Object> service, String selectedSymbol) {
		return maps(service.get("enabledForSymbols")).stream()
			.map(symbol -> normalizeSymbol(text(symbol, "authority") + ":" + text(symbol, "code")))
			.anyMatch(selectedSymbol::equals);
	}

	private void requireSelectedSymbol(Map<String, Object> self, String selectedSymbol) {
		boolean present = maps(self.get("symbols")).stream()
			.map(symbol -> normalizeSymbol(text(symbol, "authority") + ":" + text(symbol, "code")))
			.anyMatch(selectedSymbol::equals);
		if (!present) {
			throw DcbProfileRegistrationException.invalid(
				"SYMBOL_REQUIRED", "Selected symbol is not advertised by the self entry.", "selectedSymbol");
		}
	}

	private String resolveEndpoint(
		Map<String, Object> service,
		Map<String, Object> config,
		String templateKey,
		String hostLmsCode
	) {
		String value = text(config, templateKey);
		if (blank(value)) {
			value = text(service, "serviceAddress");
		}
		if (blank(value)) {
			throw DcbProfileRegistrationException.invalid(
				"SERVICE_ADDRESS_REQUIRED", "Service address is required.", "services.serviceAddress");
		}
		return value.replace("{hostLmsCode}", hostLmsCode);
	}

	private Map<String, Object> fetchJson(URI uri, String failureCode) {
		try {
			String body = httpClient.toBlocking().retrieve(
				HttpRequest.GET(uri).accept(MediaType.APPLICATION_JSON_TYPE),
				Argument.of(String.class)
			);
			if (body == null || body.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
				throw new IllegalArgumentException("response is empty or exceeds size limit");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> result = objectMapper.readValue(body, Map.class);
			return result;
		} catch (Exception exception) {
			throw DcbProfileRegistrationException.unavailable(
				failureCode, "Could not retrieve public registration metadata from " + uri);
		}
	}

	private URI guardedUri(String value, String field) {
		try {
			URI uri = URI.create(value).normalize();
			if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
				|| uri.getFragment() != null
				|| (!"https".equalsIgnoreCase(uri.getScheme())
					&& !("http".equalsIgnoreCase(uri.getScheme()) && properties.isAllowHttp()))) {
				throw new IllegalArgumentException();
			}
			if (!properties.isAllowPrivateAddresses()) {
				for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
					if (address.isAnyLocalAddress() || address.isLoopbackAddress()
						|| address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
						throw new IllegalArgumentException();
					}
				}
			}
			return uri;
		} catch (Exception exception) {
			throw DcbProfileRegistrationException.invalid(
				"URL_NOT_ALLOWED", "URL is not allowed for DCB registration.", field);
		}
	}

	private String nodeId() {
		return peerAuthProperties.getLocalIdentity().getId();
	}

	private String policyText(DcbProfileMembership invitation, String name) {
		String value = text(invitation.getPolicy(), name);
		if (blank(value)) {
			throw new IllegalStateException("Invitation policy is missing " + name);
		}
		return value;
	}

	private boolean policyBoolean(DcbProfileMembership invitation, String name) {
		return bool(invitation.getPolicy(), name);
	}

	private void requireText(Map<String, Object> map, String field, String code) {
		if (blank(text(map, field))) {
			throw DcbProfileRegistrationException.invalid(
				code, "Required profile field is missing: " + field, "services.config." + field);
		}
	}

	private void requireClaim(Object actual, Object expected, String claim) {
		if (!Objects.equals(actual, expected)) {
			throw new IllegalArgumentException("proof " + claim + " does not match");
		}
	}

	private String origin(String value) {
		URI uri = URI.create(value);
		int port = uri.getPort();
		return uri.getScheme() + "://" + uri.getHost() + (port >= 0 ? ":" + port : "");
	}

	private String normalizeSymbol(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toUpperCase(Locale.ROOT);
	}

	private boolean containsIgnoreCase(List<String> values, String expected) {
		return values.stream().anyMatch(value -> expected.equalsIgnoreCase(value));
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private String text(Map<String, Object> map, String key) {
		Object value = map != null ? map.get(key) : null;
		return value != null ? String.valueOf(value) : null;
	}

	private boolean bool(Map<String, Object> map, String key) {
		Object value = map != null ? map.get(key) : null;
		return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
	}

	private Integer integer(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return value != null ? Integer.valueOf(String.valueOf(value)) : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> maps(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
			.filter(Map.class::isInstance)
			.map(item -> (Map<String, Object>) item)
			.toList();
	}

	private List<String> strings(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
	}

	@SuppressWarnings("unchecked")
	private Object canonicalize(Object value) {
		if (value instanceof Map<?, ?> source) {
			Map<String, Object> sorted = new java.util.TreeMap<>();
			source.forEach((key, item) -> {
				if (key != null && item != null) {
					sorted.put(String.valueOf(key), canonicalize(item));
				}
			});
			return sorted;
		}
		if (value instanceof List<?> list) {
			List<Object> normalized = list.stream().map(this::canonicalize).collect(java.util.stream.Collectors.toList());
			if (normalized.stream().allMatch(Map.class::isInstance)) {
				normalized.sort(Comparator.comparing(String::valueOf));
			}
			return normalized;
		}
		return value;
	}

	public record ValidatedRegistration(
		Map<String, Object> descriptor,
		String descriptorHash,
		String remoteBaseUrl,
		String directoryUrl,
		String issuer,
		String selfSlug,
		String selectedSymbol,
		String ncipEndpoint,
		String oaiEndpoint,
		String ncipSystemId,
		String ncipAgencyId,
		String jwksUrl,
		String outboundAudience,
		String inboundAudience,
		String peerSubject,
		String commonName,
		String address,
		List<Map<String, Object>> locations,
		String authProfile
	) {
	}
}
