package org.olf.dcb.request.lifecycle.ncip.profile.application;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.olf.dcb.core.interaction.ors.ORSApplianceOaiPmhIngestSource;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.request.lifecycle.ncip.profile.api.DcbProfileRegistrationApi;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembershipState;
import org.olf.dcb.request.lifecycle.ncip.profile.persistence.DcbProfileMembershipRepository;
import org.olf.dcb.request.lifecycle.ncip.profile.support.DcbProfileDirectoryPullService;
import org.olf.dcb.request.lifecycle.ncip.profile.support.DcbProfileDirectoryPullService.ValidatedRegistration;
import org.olf.dcb.request.lifecycle.ncip.NcipIdentityConfiguration;
import org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.LocationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Singleton
public class DcbProfileRegistrationService {
	private static final SecureRandom RANDOM = new SecureRandom();

	private final DcbProfileMembershipRepository membershipRepository;
	private final HostLmsRepository hostLmsRepository;
	private final AgencyRepository agencyRepository;
	private final LibraryRepository libraryRepository;
	private final LocationRepository locationRepository;
	private final R2dbcOperations r2dbcOperations;
	private final DcbProfileDirectoryPullService directoryPullService;
	private final DcbProfileRegistrationProperties properties;
	private final DcbPeerAuthProperties peerAuthProperties;
	private final NcipIdentityConfiguration ncipIdentity;
	private final DcbProfileReadinessService readinessService;

	public DcbProfileRegistrationService(
		DcbProfileMembershipRepository membershipRepository,
		HostLmsRepository hostLmsRepository,
		AgencyRepository agencyRepository,
		LibraryRepository libraryRepository,
		LocationRepository locationRepository,
		R2dbcOperations r2dbcOperations,
		DcbProfileDirectoryPullService directoryPullService,
		DcbProfileRegistrationProperties properties,
		DcbPeerAuthProperties peerAuthProperties,
		NcipIdentityConfiguration ncipIdentity,
		DcbProfileReadinessService readinessService
	) {
		this.membershipRepository = membershipRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.agencyRepository = agencyRepository;
		this.libraryRepository = libraryRepository;
		this.locationRepository = locationRepository;
		this.r2dbcOperations = r2dbcOperations;
		this.directoryPullService = directoryPullService;
		this.properties = properties;
		this.peerAuthProperties = peerAuthProperties;
		this.ncipIdentity = ncipIdentity;
		this.readinessService = readinessService;
	}

	public DcbProfileRegistrationApi.InvitationResponse issue(
		DcbProfileRegistrationApi.IssueInvitationRequest request,
		String actor
	) {
		readinessService.requireReady();
		String profile = blank(request.profile())
			? DcbProfileRegistrationApi.PROFILE_ID
			: request.profile().trim();
		int profileVersion = request.profileVersion() == null
			? DcbProfileRegistrationApi.PROFILE_VERSION
			: request.profileVersion();
		if (!DcbProfileRegistrationApi.PROFILE_ID.equals(profile)
			|| profileVersion != DcbProfileRegistrationApi.PROFILE_VERSION) {
			throw DcbProfileRegistrationException.invalid(
				"PROFILE_NOT_SUPPORTED", "Only DCB-NCIP2.02+ version 1 is supported.", "profile");
		}
		validatePolicy(request.policy());

		UUID id = UUID.randomUUID();
		byte[] secret = new byte[32];
		RANDOM.nextBytes(secret);
		String invitation = id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.getInvitationTtl());
		DcbProfileMembership membership = DcbProfileMembership.builder()
			.id(id)
			.profileId(profile)
			.profileVersion(profileVersion)
			.state(DcbProfileMembershipState.INVITED)
			.tokenHash(hash(invitation))
			.expiresAt(expiresAt)
			.policy(policyMap(request.policy()))
			.issuedBy(actor)
			.issuedAt(now)
			.build();
		Mono.from(membershipRepository.save(membership)).block();
		return new DcbProfileRegistrationApi.InvitationResponse(
			id,
			invitation,
			profile,
			profileVersion,
			expiresAt,
			nodeId(),
			properties.getNodeName(),
			request.policy()
		);
	}

	public DcbProfileRegistrationApi.InvitationMetadata invitation(String token) {
		DcbProfileMembership membership = requireInvitation(token);
		return invitationMetadata(membership);
	}

	public DcbProfileRegistrationApi.ValidationResponse validate(
		String token,
		String proof,
		DcbProfileRegistrationApi.RegistrationRequest request
	) {
		DcbProfileMembership invitation = requireRedeemableInvitation(token);
		validateExpectedSymbol(invitation, request.selectedSymbol());
		ValidatedRegistration validated = directoryPullService.validate(invitation, request, proof);
		validateObjectConflicts(invitation, validated);
		return new DcbProfileRegistrationApi.ValidationResponse(
			true,
			invitation.getId(),
			validated.descriptorHash(),
			validated.descriptor(),
			List.of(),
			invitation.getExpiresAt()
		);
	}

	public DcbProfileRegistrationApi.MembershipResponse redeem(
		String token,
		String proof,
		DcbProfileRegistrationApi.RegistrationRequest request
	) {
		DcbProfileMembership invitation = requireInvitation(token);
		if (invitation.getState() == DcbProfileMembershipState.ACTIVE) {
			if (request.idempotencyKey().equals(invitation.getIdempotencyKey())) {
				return response(invitation);
			}
			throw DcbProfileRegistrationException.conflict(
				"INVITATION_ALREADY_REDEEMED", "Invitation has already been redeemed.", "invitation");
		}
		requireRedeemable(invitation);
		validateExpectedSymbol(invitation, request.selectedSymbol());
		ValidatedRegistration validated = directoryPullService.validate(invitation, request, proof);
		validateObjectConflicts(invitation, validated);

		return Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(membershipRepository.findByTokenHashForUpdate(hash(token)))
				.switchIfEmpty(Mono.error(DcbProfileRegistrationException.unauthorized(
					"INVITATION_INVALID", "Invitation is invalid.")))
				.flatMap(current -> {
					if (current.getState() == DcbProfileMembershipState.ACTIVE
						&& request.idempotencyKey().equals(current.getIdempotencyKey())) {
						return Mono.just(response(current));
					}
					requireRedeemable(current);
					return validateObjectConflictsReactive(current, validated)
						.then(createMembershipObjects(current, validated, request.idempotencyKey()));
				})
		)).block();
	}

	public DcbProfileRegistrationApi.MembershipResponse status(UUID membershipId) {
		return response(requireMembership(membershipId));
	}

	public DcbProfileRegistrationApi.MembershipResponse sync(UUID membershipId) {
		DcbProfileMembership membership = requireMembership(membershipId);
		requireSynchronizable(membership);
		try {
			ValidatedRegistration validated = directoryPullService.pull(membership);
			if (validated.descriptorHash().equals(membership.getApprovedDescriptorHash())) {
				Instant now = Instant.now();
				membership
					.setState(DcbProfileMembershipState.ACTIVE)
					.setPendingDescriptor(null)
					.setPendingDescriptorHash(null)
					.setLastSyncedAt(now)
					.setNextSyncAt(now.plus(properties.getSyncInterval()))
					.setLastSyncError(null);
				return response(Mono.from(membershipRepository.update(membership)).block());
			}
			if (!directoryPullService.sensitiveChanges(
				membership.getApprovedDescriptor(),
				validated.descriptor()).isEmpty()) {
				Instant now = Instant.now();
				membership
					.setState(DcbProfileMembershipState.REVIEW_REQUIRED)
					.setPendingDescriptor(new LinkedHashMap<>(validated.descriptor()))
					.setPendingDescriptorHash(validated.descriptorHash())
					.setLastSyncedAt(now)
					.setNextSyncAt(now.plus(properties.getSyncInterval()))
					.setLastSyncError(null);
				return response(Mono.from(membershipRepository.update(membership)).block());
			}
			return applyValidated(membership, validated, "DCB Profile NCIP2.02+ directory sync");
		} catch (RuntimeException exception) {
			recordSyncFailure(membership, exception);
			throw exception;
		}
	}

	public DcbProfileRegistrationApi.MembershipResponse approveChange(UUID membershipId) {
		DcbProfileMembership membership = requireMembership(membershipId);
		if (membership.getState() != DcbProfileMembershipState.REVIEW_REQUIRED
			|| blank(membership.getPendingDescriptorHash())) {
			throw DcbProfileRegistrationException.conflict(
				"REVIEW_NOT_PENDING", "Membership has no pending sensitive change.", "membershipId");
		}
		ValidatedRegistration validated = directoryPullService.pull(membership);
		if (!membership.getPendingDescriptorHash().equals(validated.descriptorHash())) {
			throw DcbProfileRegistrationException.conflict(
				"PENDING_DESCRIPTOR_CHANGED",
				"Remote profile changed after review was requested; sync and review again.",
				"membershipId");
		}
		return applyValidated(membership, validated, "Approved DCB Profile NCIP2.02+ directory change");
	}

	public DcbProfileRegistrationApi.MembershipResponse rejectChange(UUID membershipId) {
		DcbProfileMembership membership = requireMembership(membershipId);
		if (membership.getState() != DcbProfileMembershipState.REVIEW_REQUIRED) {
			throw DcbProfileRegistrationException.conflict(
				"REVIEW_NOT_PENDING", "Membership has no pending sensitive change.", "membershipId");
		}
		membership
			.setState(DcbProfileMembershipState.ACTIVE)
			.setPendingDescriptor(null)
			.setPendingDescriptorHash(null)
			.setNextSyncAt(Instant.now().plus(properties.getSyncInterval()))
			.setLastSyncError(null);
		return response(Mono.from(membershipRepository.update(membership)).block());
	}

	public DcbProfileRegistrationApi.MembershipResponse revoke(UUID membershipId) {
		DcbProfileMembership membership = requireMembership(membershipId);
		if (membership.getState() == DcbProfileMembershipState.REVOKED) {
			return response(membership);
		}
		if (membership.getState() != DcbProfileMembershipState.ACTIVE
			&& membership.getState() != DcbProfileMembershipState.REVIEW_REQUIRED) {
			throw DcbProfileRegistrationException.conflict(
				"MEMBERSHIP_NOT_ACTIVE", "Only an active membership can be revoked.", "membershipId");
		}
		return Mono.from(r2dbcOperations.withTransaction(status ->
			disableMembershipObjects(membership)
				.then(Mono.defer(() -> {
					Instant now = Instant.now();
					membership
						.setState(DcbProfileMembershipState.REVOKED)
						.setRevokedAt(now)
						.setNextSyncAt(null)
						.setPendingDescriptor(null)
						.setPendingDescriptorHash(null)
						.setLastSyncError(null);
					return Mono.from(membershipRepository.update(membership));
				}))
				.map(this::response)
		)).block();
	}

	public void syncDue() {
		Flux.from(membershipRepository.findDueForSync(Instant.now(), 25))
			.map(DcbProfileMembership::getId)
			.collectList()
			.blockOptional()
			.orElse(List.of())
			.forEach(this::syncIgnoringFailure);
	}

	private Mono<DcbProfileRegistrationApi.MembershipResponse> createMembershipObjects(
		DcbProfileMembership membership,
		ValidatedRegistration validated,
		String idempotencyKey
	) {
		String hostCode = policyText(membership, "hostLmsCode");
		String agencyCode = policyText(membership, "agencyCode");
		UUID hostId = UUIDUtils.generateHostLmsId(hostCode);
		UUID agencyId = UUIDUtils.generateAgencyId(agencyCode);
		UUID libraryId = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Library:" + agencyCode);

		DataHostLms hostLms = DataHostLms.builder()
			.id(hostId)
			.code(hostCode)
			.name(validated.commonName())
			.lmsClientClass(ORSApplianceHostLMS.class.getName())
			.ingestSourceClass(ORSApplianceOaiPmhIngestSource.class.getName())
			.suppressionRulesetName(policyTextOrNull(membership, "suppressionRulesetName"))
			.itemSuppressionRulesetName(policyTextOrNull(membership, "itemSuppressionRulesetName"))
			.clientConfig(clientConfig(membership, validated, agencyCode))
			.reason("DCB Profile NCIP2.02+ invitation redemption")
			.build();
		DataAgency agency = DataAgency.builder()
			.id(agencyId)
			.code(agencyCode)
			.name(validated.commonName())
			.hostLms(hostLms)
			.authProfile(defaultText(policyTextOrNull(membership, "authProfile"), DataAgency.BASIC_BARCODE_AND_PIN))
			.isBorrowingAgency(policyBoolean(membership, "borrowingAllowed"))
			.isSupplyingAgency(policyBoolean(membership, "supplyingAllowed"))
			.maxConsortialLoans(policyInteger(membership, "maxConsortialLoans"))
			.reason("DCB Profile NCIP2.02+ invitation redemption")
			.build();
		Library library = Library.builder()
			.id(libraryId)
			.agencyCode(agencyCode)
			.fullName(validated.commonName())
			.shortName(limit(validated.commonName(), 32))
			.abbreviatedName(limit(agencyCode, 32))
			.address(limit(validated.address(), 200))
			.agency(agency)
			.reason("DCB Profile NCIP2.02+ invitation redemption")
			.build();
		List<Location> locations = validated.locations().stream()
			.map(location -> location(membership, validated, hostLms, agency, location))
			.toList();

		return Mono.from(hostLmsRepository.save(hostLms))
			.then(Mono.from(agencyRepository.save(agency)))
			.then(Mono.from(libraryRepository.save(library)))
			.thenMany(Flux.fromIterable(locations).concatMap(locationRepository::save))
			.collectList()
			.flatMap(savedLocations -> {
				Instant now = Instant.now();
				membership
					.setState(DcbProfileMembershipState.ACTIVE)
					.setRemoteBaseUrl(validated.remoteBaseUrl())
					.setRemoteDirectoryUrl(validated.directoryUrl())
					.setRemoteIssuer(validated.issuer())
					.setRemoteSelfSlug(validated.selfSlug())
					.setSelectedSymbol(validated.selectedSymbol())
					.setHostLmsId(hostId)
					.setApprovedDescriptor(new LinkedHashMap<>(validated.descriptor()))
					.setApprovedDescriptorHash(validated.descriptorHash())
					.setPendingDescriptor(null)
					.setPendingDescriptorHash(null)
					.setIdempotencyKey(idempotencyKey)
					.setRedeemedAt(now)
					.setLastSyncedAt(now)
					.setNextSyncAt(now.plus(properties.getSyncInterval()))
					.setLastSyncError(null);
				return Mono.from(membershipRepository.update(membership))
					.map(this::response);
			});
	}

	private DcbProfileRegistrationApi.MembershipResponse applyValidated(
		DcbProfileMembership membership,
		ValidatedRegistration validated,
		String reason
	) {
		return Mono.from(r2dbcOperations.withTransaction(status ->
			updateMembershipObjects(membership, validated, reason)
				.then(Mono.defer(() -> {
					Instant now = Instant.now();
					membership
						.setState(DcbProfileMembershipState.ACTIVE)
						.setRemoteBaseUrl(validated.remoteBaseUrl())
						.setRemoteDirectoryUrl(validated.directoryUrl())
						.setRemoteIssuer(validated.issuer())
						.setRemoteSelfSlug(validated.selfSlug())
						.setSelectedSymbol(validated.selectedSymbol())
						.setApprovedDescriptor(new LinkedHashMap<>(validated.descriptor()))
						.setApprovedDescriptorHash(validated.descriptorHash())
						.setPendingDescriptor(null)
						.setPendingDescriptorHash(null)
						.setLastSyncedAt(now)
						.setNextSyncAt(now.plus(properties.getSyncInterval()))
						.setLastSyncError(null);
					return Mono.from(membershipRepository.update(membership));
				}))
				.map(this::response)
		)).block();
	}

	private Mono<Void> updateMembershipObjects(
		DcbProfileMembership membership,
		ValidatedRegistration validated,
		String reason
	) {
		String agencyCode = policyText(membership, "agencyCode");
		UUID hostId = UUIDUtils.generateHostLmsId(policyText(membership, "hostLmsCode"));
		UUID agencyId = UUIDUtils.generateAgencyId(agencyCode);
		UUID libraryId = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Library:" + agencyCode);
		return Mono.zip(
				Mono.from(hostLmsRepository.findById(hostId)),
				Mono.from(agencyRepository.findById(agencyId)),
				Mono.from(libraryRepository.findById(libraryId)))
			.switchIfEmpty(Mono.error(DcbProfileRegistrationException.conflict(
				"MEMBERSHIP_OBJECT_MISSING",
				"A DCB object owned by this membership is missing.",
				"membershipId")))
			.flatMap(objects -> {
				DataHostLms host = objects.getT1();
				DataAgency agency = objects.getT2();
				Library library = objects.getT3();
				host.setName(validated.commonName());
				host.setClientConfig(clientConfig(membership, validated, agencyCode));
				host.setReason(reason);
				agency
					.setName(validated.commonName())
					.setReason(reason);
				library.setFullName(validated.commonName());
				library.setShortName(limit(validated.commonName(), 32));
				library.setAddress(limit(validated.address(), 200));
				library.setReason(reason);
				return Mono.from(hostLmsRepository.update(host))
					.then(Mono.from(agencyRepository.update(agency)))
					.then(Mono.from(libraryRepository.update(library)))
					.thenMany(Flux.fromIterable(validated.locations())
						.concatMap(source -> {
							Location proposed = location(membership, validated, host, agency, source);
							proposed.setReason(reason);
							return locationRepository.saveOrUpdate(proposed);
						}))
					.then();
			});
	}

	private Mono<Void> disableMembershipObjects(DcbProfileMembership membership) {
		String agencyCode = policyText(membership, "agencyCode");
		UUID hostId = UUIDUtils.generateHostLmsId(policyText(membership, "hostLmsCode"));
		UUID agencyId = UUIDUtils.generateAgencyId(agencyCode);
		return Mono.zip(
				Mono.from(hostLmsRepository.findById(hostId)),
				Mono.from(agencyRepository.findById(agencyId)))
			.switchIfEmpty(Mono.error(DcbProfileRegistrationException.conflict(
				"MEMBERSHIP_OBJECT_MISSING",
				"A DCB object owned by this membership is missing.",
				"membershipId")))
			.flatMap(objects -> {
				DataHostLms host = objects.getT1();
				DataAgency agency = objects.getT2();
				Map<String, Object> config = host.getClientConfig() != null
					? new LinkedHashMap<>(host.getClientConfig())
					: new LinkedHashMap<>();
				config.put("ingest", false);
				config.put("revoked", true);
				config.put("ncip-peer-auth-mode", "REVOKED");
				host.setClientConfig(config);
				host.setReason("DCB Profile NCIP2.02+ membership revoked");
				agency
					.setIsBorrowingAgency(false)
					.setIsSupplyingAgency(false)
					.setReason("DCB Profile NCIP2.02+ membership revoked");
				return Mono.from(hostLmsRepository.update(host))
					.then(Mono.from(agencyRepository.update(agency)))
					.thenMany(Flux.fromIterable(descriptorLocations(membership))
						.concatMap(source -> {
							UUID id = UUIDUtils.generateLocationId(agencyCode, text(source, "dcbCode"));
							return Mono.from(locationRepository.findById(id))
								.flatMap(location -> {
									location
										.setIsPickup(false)
										.setIsEnabledForPickupAnywhere(false)
										.setIsSupplyingLocation(false)
										.setIsShelving(false)
										.setReason("DCB Profile NCIP2.02+ membership revoked");
									return Mono.from(locationRepository.update(location));
								});
						}))
					.then();
			});
	}

	private Location location(
		DcbProfileMembership membership,
		ValidatedRegistration validated,
		DataHostLms hostLms,
		DataAgency agency,
		Map<String, Object> source
	) {
		String dcbCode = text(source, "dcbCode");
		return Location.builder()
			.id(UUIDUtils.generateLocationId(policyText(membership, "agencyCode"), dcbCode))
			.code(dcbCode)
			.localId(text(source, "sourceCode"))
			.name(defaultText(text(source, "name"), dcbCode))
			.type("LIBRARY")
			.agency(agency)
			.hostSystem(hostLms)
			.isPickup(bool(source, "pickup"))
			.isEnabledForPickupAnywhere(bool(source, "pickup"))
			.isSupplyingLocation(bool(source, "supplying"))
			.isShelving(bool(source, "supplying"))
			.latitude(decimal(source, "latitude"))
			.longitude(decimal(source, "longitude"))
			.printLabel(limit(defaultText(text(source, "name"), dcbCode), 128))
			.reason("DCB Profile NCIP2.02+ invitation redemption")
			.build();
	}

	private Map<String, Object> clientConfig(
		DcbProfileMembership membership,
		ValidatedRegistration validated,
		String agencyCode
	) {
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("profile", DcbProfileRegistrationApi.PROFILE_ID);
		config.put("profile-version", DcbProfileRegistrationApi.PROFILE_VERSION);
		config.put("base-url", origin(validated.oaiEndpoint()));
		config.put("base-url-qualifier", validated.selfSlug());
		config.put("oai-endpoint-url", validated.oaiEndpoint());
		config.put("metadata-prefix", "marcxml");
		config.put("ingest", policyBoolean(membership, "ingestAllowed"));
		config.put("default-agency-code", agencyCode);
		config.put("ncip-system-id", validated.ncipSystemId());
		config.put("ncip-agency-id", validated.ncipAgencyId());
		config.put("ncip-endpoint-url", validated.ncipEndpoint());
		config.put("ncip-peer-auth-mode", "JWT_REQUIRED");
		config.put("ncip-peer-issuer", validated.issuer());
		config.put("ncip-peer-jwks-url", validated.jwksUrl());
		config.put("ncip-peer-audience", validated.inboundAudience());
		return config;
	}

	private void validateObjectConflicts(
		DcbProfileMembership membership,
		ValidatedRegistration validated
	) {
		validateObjectConflictsReactive(membership, validated).block();
	}

	private Mono<Void> validateObjectConflictsReactive(
		DcbProfileMembership membership,
		ValidatedRegistration validated
	) {
		if (membership.getState() == DcbProfileMembershipState.ACTIVE) {
			return Mono.empty();
		}
		String hostCode = policyText(membership, "hostLmsCode");
		String agencyCode = policyText(membership, "agencyCode");
		Mono<Void> coreChecks = Mono.zip(
				Mono.from(hostLmsRepository.findByCode(hostCode)).hasElement(),
				Mono.from(agencyRepository.findOneByCode(agencyCode)).hasElement(),
				Mono.from(libraryRepository.findOneByAgencyCode(agencyCode)).hasElement())
			.flatMap(result -> {
				if (result.getT1()) {
					return Mono.error(DcbProfileRegistrationException.conflict(
						"HOST_LMS_CODE_CONFLICT", "HostLMS code already exists: " + hostCode, "policy.hostLmsCode"));
				}
				if (result.getT2()) {
					return Mono.error(DcbProfileRegistrationException.conflict(
						"AGENCY_CODE_CONFLICT", "Agency code already exists: " + agencyCode, "policy.agencyCode"));
				}
				if (result.getT3()) {
					return Mono.error(DcbProfileRegistrationException.conflict(
						"LIBRARY_CODE_CONFLICT", "Library already exists for Agency: " + agencyCode, "policy.agencyCode"));
				}
				return Mono.empty();
			});
		Mono<Void> locationChecks = Flux.fromIterable(validated.locations())
			.concatMap(location -> {
				String code = text(location, "dcbCode");
				return Mono.from(locationRepository.existsByCode(code))
					.defaultIfEmpty(false)
					.flatMap(exists -> exists
						? Mono.error(DcbProfileRegistrationException.conflict(
							"LOCATION_CODE_CONFLICT",
							"Location code already exists: " + code,
							"locations.dcbCode"))
						: Mono.empty());
			})
			.then();
		return coreChecks.then(locationChecks);
	}

	private DcbProfileMembership requireInvitation(String token) {
		if (blank(token)) {
			throw DcbProfileRegistrationException.unauthorized(
				"INVITATION_REQUIRED", "Invitation bearer token is required.");
		}
		return Mono.from(membershipRepository.findByTokenHash(hash(token)))
			.blockOptional()
			.orElseThrow(() -> DcbProfileRegistrationException.unauthorized(
				"INVITATION_INVALID", "Invitation is invalid."));
	}

	private DcbProfileMembership requireMembership(UUID membershipId) {
		return Mono.from(membershipRepository.findById(membershipId))
			.blockOptional()
			.orElseThrow(() -> DcbProfileRegistrationException.invalid(
				"MEMBERSHIP_NOT_FOUND", "Membership was not found.", "membershipId"));
	}

	private void requireSynchronizable(DcbProfileMembership membership) {
		if (membership.getState() != DcbProfileMembershipState.ACTIVE
			&& membership.getState() != DcbProfileMembershipState.REVIEW_REQUIRED) {
			throw DcbProfileRegistrationException.conflict(
				"MEMBERSHIP_NOT_ACTIVE", "Membership cannot be synchronized in its current state.", "membershipId");
		}
	}

	private void syncIgnoringFailure(UUID membershipId) {
		try {
			sync(membershipId);
		} catch (RuntimeException ignored) {
			// Failure details and retry time are persisted by sync().
		}
	}

	private void recordSyncFailure(DcbProfileMembership membership, RuntimeException exception) {
		try {
			membership
				.setLastSyncError(limit(exception.getMessage(), 1000))
				.setNextSyncAt(Instant.now().plus(properties.getSyncInterval()));
			Mono.from(membershipRepository.update(membership)).block();
		} catch (RuntimeException ignored) {
			// Preserve the original pull/apply failure.
		}
	}

	private DcbProfileMembership requireRedeemableInvitation(String token) {
		DcbProfileMembership invitation = requireInvitation(token);
		requireRedeemable(invitation);
		return invitation;
	}

	private void requireRedeemable(DcbProfileMembership invitation) {
		if (invitation.getState() != DcbProfileMembershipState.INVITED) {
			throw DcbProfileRegistrationException.conflict(
				"INVITATION_NOT_REDEEMABLE",
				"Invitation state is " + invitation.getState() + ".",
				"invitation");
		}
		if (invitation.getExpiresAt() == null || !invitation.getExpiresAt().isAfter(Instant.now())) {
			throw DcbProfileRegistrationException.unauthorized(
				"INVITATION_EXPIRED", "Invitation has expired.");
		}
	}

	private void validateExpectedSymbol(DcbProfileMembership invitation, String selectedSymbol) {
		String expected = policyTextOrNull(invitation, "expectedSymbol");
		if (!blank(expected) && !normalizeSymbol(expected).equals(normalizeSymbol(selectedSymbol))) {
			throw DcbProfileRegistrationException.conflict(
				"SYMBOL_NOT_INVITED", "Selected symbol does not match invitation policy.", "selectedSymbol");
		}
	}

	private void validatePolicy(DcbProfileRegistrationApi.InvitationPolicy policy) {
		if (policy.hostLmsCode().length() > 32 || policy.agencyCode().length() > 32) {
			throw DcbProfileRegistrationException.invalid(
				"CODE_TOO_LONG", "HostLMS and Agency codes must not exceed 32 characters.", "policy");
		}
		if (!policy.borrowingAllowed() && !policy.supplyingAllowed()) {
			throw DcbProfileRegistrationException.invalid(
				"PARTICIPATION_ROLE_REQUIRED",
				"Invitation must allow borrowing, supplying or both.",
				"policy");
		}
		if (policy.ingestAllowed() && !policy.supplyingAllowed()) {
			throw DcbProfileRegistrationException.invalid(
				"INGEST_REQUIRES_SUPPLYING",
				"Catalogue ingest requires supplying participation.",
				"policy.ingestAllowed");
		}
	}

	private DcbProfileRegistrationApi.MembershipResponse response(DcbProfileMembership membership) {
		String hostCode = policyText(membership, "hostLmsCode");
		String agencyCode = policyText(membership, "agencyCode");
		UUID libraryId = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Library:" + agencyCode);
		List<DcbProfileRegistrationApi.ObjectBinding> locations = descriptorLocations(membership).stream()
			.map(location -> {
				String code = text(location, "dcbCode");
				return new DcbProfileRegistrationApi.ObjectBinding(
					UUIDUtils.generateLocationId(agencyCode, code), code);
			})
			.toList();
		return new DcbProfileRegistrationApi.MembershipResponse(
			membership.getId(),
			membership.getState().name(),
			membership.getProfileId(),
			membership.getProfileVersion(),
			membership.getApprovedDescriptorHash(),
			new DcbProfileRegistrationApi.ObjectBinding(
				UUIDUtils.generateHostLmsId(hostCode), hostCode),
			new DcbProfileRegistrationApi.ObjectBinding(
				UUIDUtils.generateAgencyId(agencyCode), agencyCode),
			new DcbProfileRegistrationApi.ObjectBinding(libraryId, agencyCode),
			locations,
			connectionMetadata(),
			membership.getNextSyncAt()
		);
	}

	private DcbProfileRegistrationApi.InvitationMetadata invitationMetadata(DcbProfileMembership membership) {
		return new DcbProfileRegistrationApi.InvitationMetadata(
			membership.getId(),
			membership.getProfileId(),
			membership.getProfileVersion(),
			membership.getExpiresAt(),
			membership.getState().name(),
			nodeId(),
			properties.getNodeName(),
			policy(membership)
		);
	}

	private DcbProfileRegistrationApi.DcbConnectionMetadata connectionMetadata() {
		DcbPeerAuthProperties.LocalIdentity identity = peerAuthProperties.getLocalIdentity();
		String baseUrl = properties.getPublicBaseUrl() != null
			? trimSlash(properties.getPublicBaseUrl().toString())
			: "";
		return new DcbProfileRegistrationApi.DcbConnectionMetadata(
			nodeId(),
			properties.getNodeName(),
			baseUrl,
			baseUrl + "/ncip/v2_02",
			identity.getIssuer(),
			identity.getJwksUri() != null ? identity.getJwksUri().toString() : null,
			nodeId(),
			identity.getAudiences().stream().findFirst().orElse("ors-appliance"),
			identity.getSubject(),
			ncipIdentity.getAgencyId()
		);
	}

	private DcbProfileRegistrationApi.InvitationPolicy policy(DcbProfileMembership membership) {
		return new DcbProfileRegistrationApi.InvitationPolicy(
			policyText(membership, "hostLmsCode"),
			policyText(membership, "agencyCode"),
			policyTextOrNull(membership, "expectedSymbol"),
			policyBoolean(membership, "borrowingAllowed"),
			policyBoolean(membership, "supplyingAllowed"),
			policyBoolean(membership, "ingestAllowed"),
			policyTextOrNull(membership, "authProfile"),
			policyInteger(membership, "maxConsortialLoans"),
			policyTextOrNull(membership, "suppressionRulesetName"),
			policyTextOrNull(membership, "itemSuppressionRulesetName")
		);
	}

	private Map<String, Object> policyMap(DcbProfileRegistrationApi.InvitationPolicy policy) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("hostLmsCode", policy.hostLmsCode());
		result.put("agencyCode", policy.agencyCode());
		putIfNotNull(result, "expectedSymbol", policy.expectedSymbol());
		result.put("borrowingAllowed", policy.borrowingAllowed());
		result.put("supplyingAllowed", policy.supplyingAllowed());
		result.put("ingestAllowed", policy.ingestAllowed());
		putIfNotNull(result, "authProfile", policy.authProfile());
		putIfNotNull(result, "maxConsortialLoans", policy.maxConsortialLoans());
		putIfNotNull(result, "suppressionRulesetName", policy.suppressionRulesetName());
		putIfNotNull(result, "itemSuppressionRulesetName", policy.itemSuppressionRulesetName());
		return result;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> descriptorLocations(DcbProfileMembership membership) {
		if (membership.getApprovedDescriptor() == null
			|| !(membership.getApprovedDescriptor().get("locations") instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
			.filter(Map.class::isInstance)
			.map(item -> (Map<String, Object>) item)
			.toList();
	}

	private String policyText(DcbProfileMembership membership, String key) {
		String value = policyTextOrNull(membership, key);
		if (blank(value)) {
			throw new IllegalStateException("Invitation policy is missing " + key);
		}
		return value;
	}

	private String policyTextOrNull(DcbProfileMembership membership, String key) {
		return text(membership.getPolicy(), key);
	}

	private boolean policyBoolean(DcbProfileMembership membership, String key) {
		return bool(membership.getPolicy(), key);
	}

	private Integer policyInteger(DcbProfileMembership membership, String key) {
		Object value = membership.getPolicy().get(key);
		return value instanceof Number number ? number.intValue() : null;
	}

	private String hash(String value) {
		try {
			return java.util.HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not hash DCB invitation", exception);
		}
	}

	private String nodeId() {
		return peerAuthProperties.getLocalIdentity().getId();
	}

	private String normalizeSymbol(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private String origin(String value) {
		URI uri = URI.create(value);
		return uri.getScheme() + "://" + uri.getAuthority();
	}

	private String trimSlash(String value) {
		String result = value;
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private String limit(String value, int maximum) {
		if (value == null || value.length() <= maximum) {
			return value;
		}
		return value.substring(0, maximum);
	}

	private String defaultText(String value, String fallback) {
		return blank(value) ? fallback : value;
	}

	private String text(Map<String, Object> map, String key) {
		Object value = map != null ? map.get(key) : null;
		return value != null ? String.valueOf(value) : null;
	}

	private boolean bool(Map<String, Object> map, String key) {
		Object value = map != null ? map.get(key) : null;
		return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
	}

	private Double decimal(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return null;
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private void putIfNotNull(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}
}
