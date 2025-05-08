package services.k_int.utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

import org.olf.dcb.core.Constants.UUIDs;

import jakarta.validation.constraints.NotNull;

public class UUIDUtils {

	private static final char KEY_SEPARATOR = ':';
	private static final Charset UTF8 = Charset.forName("UTF-8");
	public static final UUID ZERO_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
	public static final UUID NAMESPACE_DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
	public static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
	public static final UUID NAMESPACE_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8");
	public static final UUID NAMESPACE_X500 = UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8");

	public static UUID dnsUUID(@NotNull String name) {
        return nameUUIDFromNamespaceAndString(NAMESPACE_DNS, name);
    }

	/**
	 * Checks if the passed in uuid is null or equal to "00000000-0000-0000-0000-000000000000" and if so returns true
	 * @param uuid the uuid to be checked
	 * @return true if the uuid is null or matches "00000000-0000-0000-0000-000000000000" otherwise false
	 */
	public static final boolean isEmpty(UUID uuid) {
		return((uuid == null) || ZERO_UUID.equals(uuid));
	}
	
	/**
	 * Generates an id for a reference mapping
	 * 
	 * @param fromContext the from context for the reference mapping
	 * @param fromCategory the from category for the reference mapping
	 * @param fromValue the from value for the reference mapping
	 * @param toContext the to context for the reference mapping
	 * @param toCategory the to category for the reference mapping
	 * @return The generated id
	 */
	public static UUID generateReferenceMappingId(
		String fromContext,
		String fromCategory,
		String fromValue,
		String toContext,
		String toCategory
	) {
		String key = new StringBuilder(fromContext)
			.append(KEY_SEPARATOR)
			.append(fromCategory)
			.append(KEY_SEPARATOR)
			.append(fromValue)
			.append(KEY_SEPARATOR)
			.append(toContext)
			.append(KEY_SEPARATOR)
			.append(toCategory)
			.toString();
		return nameUUIDFromNamespaceAndString(UUIDs.NAMESPACE_MAPPINGS, key);
	}

	/**
	 * Generates an id for a range mapping 
	 * @param context the context for the range mapping
	 * @param domain the domain for the range mapping
	 * @param lowerBound the lower bound value for the range mapping
	 * @return The generated id
	 */
	public static UUID generateRangeMappingId(
		String context,
		String domain,
		Long lowerBound
	) {
		String key = new StringBuilder(context)
			.append(KEY_SEPARATOR)
			.append(domain)
			.append(KEY_SEPARATOR)
			.append(lowerBound)
			.toString();
		return nameUUIDFromNamespaceAndString(UUIDs.NAMESPACE_MAPPINGS, key);
	}

	/**
	 * Generates an id for a location 
	 * @param agencyCode The code for the agency that the location belongs to
	 * @param locationCode the code for the location
	 * @return The generated id
	 */
	public static UUID generateLocationId(
		String agencyCode,
		String locationCode
	) {
		String key = new StringBuilder(agencyCode)
			.append(KEY_SEPARATOR)
			.append(locationCode)
			.toString();
		
		// The scripts used the mappings namespace, so to ensure we create the same UUID have kept it the same even through it seems wrong
		return nameUUIDFromNamespaceAndString(UUIDs.NAMESPACE_MAPPINGS, key);
	}

	/**
	 * Generates an id for an agency
	 * @param agencyCode the code for the agency
	 * @return The generated id
	 */
	public static UUID generateAgencyId(
		String agencyCode
	) {
		return nameUUIDFromNamespaceAndString(UUIDs.NAMESPACE_AGENCIES, agencyCode);
	}

	public static UUID generateAlarmId(String alarmCode) {
		return nameUUIDFromNamespaceAndString(UUIDs.NAMESPACE_ALARMS, alarmCode);
	}

	/**
	 * Generates an id for a host lms
	 * @param code the code for the host lms
	 * @return The generated id
	 */
	public static UUID generateHostLmsId(
		String code
	) {
		return nameUUIDFromNamespaceAndString(UUIDs.NAMESPACE_HOSTLMS, code);
	}

	/**
	 * Create a UUID V5 from the supplied namespace UUID and a string
	 *
	 * @param namespace UUID namespace
	 * @param name      The string data
	 * @return A Version 5 compliant UUID
	 */
	public static UUID nameUUIDFromNamespaceAndString(@NotNull UUID namespace, @NotNull String name) {
		return nameUUIDFromNamespaceAndBytes(namespace, name.getBytes(UTF8));
	}

	/**
	 * Create a UUID V5 from the supplied namespace UUID and an array of bytes
	 *
	 * @param namespace UUID namespace
	 * @param nameBytes The byte data
	 * @return A Version 5 compliant UUID
	 */
	public static UUID nameUUIDFromNamespaceAndBytes(@NotNull UUID namespace, @NotNull byte[] nameBytes) {
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException ex) {
			throw new InternalError("SHA-1 not supported");
		}

		// Create a bytebuffer.
		final ByteBuffer buffer = ByteBuffer.wrap(new byte[16 + nameBytes.length]);
		buffer.putLong(Objects.requireNonNull(namespace, "Namespace is null").getMostSignificantBits());
		buffer.putLong(namespace.getLeastSignificantBits());
		buffer.put(nameBytes);

		md.update(buffer.array());

		// Tweak the bytes to add variant information
		final byte[] sha1Bytes = md.digest();
		sha1Bytes[6] &= 0x0f; /* clear version */
		sha1Bytes[6] |= 0x50; /* set to version 5 */
		sha1Bytes[8] &= 0x3f; /* clear variant */
		sha1Bytes[8] |= 0x80; /* set to IETF variant */

		return fromBytes(sha1Bytes);
	}

	/**
	 * Construct UUID directly from Bytes.
	 *
	 * @param data The byte data
	 * @return A UUID
	 */
	public static UUID fromBytes(final byte[] data) {
		// Based on the private UUID(bytes[]) constructor
		long msb = 0;
		long lsb = 0;
		assert data.length >= 16;

		// First 8 bytes (64 bits)
		for (int i = 0; i < 8; i++)
			msb = (msb << 8) | (data[i] & 0xff);

		// Next 8 bytes (64 bits). Truncating if longer than 128 bits total
		for (int i = 8; i < 16; i++)
			lsb = (lsb << 8) | (data[i] & 0xff);

		return new UUID(msb, lsb);
	}
}
