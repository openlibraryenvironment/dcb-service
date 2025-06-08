package org.olf.dcb.core.model.clustering;

import java.util.UUID;

import org.olf.dcb.core.Constants;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import services.k_int.utils.UUIDUtils;

@Data
@Serdeable
@MappedEntity
@Builder(toBuilder = true)
@ExcludeFromGeneratedCoverageReport
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Accessors(chain = true)
public class MatchPoint {
	
	@Creator
	public MatchPoint(UUID id, @NonNull UUID value, @NonNull UUID bibId, @Nullable String domain) {
    // id, value, bib, domain, sourceValue, valueHint
		this(id, value, bibId, domain, null, null);
	}

	public MatchPoint(UUID id, @NonNull UUID value, @NonNull UUID bibId) {
		this(id, value, bibId, null, null, null);
	}

	private static final String PREFIX_VALUE = "MatchPoint";
	 
	public static MatchPoint buildFromString ( String input, boolean is_development ) {
		return buildFromString(input, "UNKNOWN", is_development);
	}

	public static MatchPoint buildFromString ( String input, String domain, boolean is_development ) {
		
		final String valueStr = PREFIX_VALUE + ":" + input;
    int input_length = input.length();
		final String valueHint = input_length > 15 
      ? ".."+input.substring(input_length-13,input_length)
      : input;

		final UUID value = UUIDUtils.nameUUIDFromNamespaceAndString(Constants.UUIDs.NAMESPACE_DCB, valueStr);
		
		return builder()
			.sourceValue(valueStr)
			.value(value)
			.sourceValueHint(valueHint)
			.domain(domain)
			.build();
	}
	
	@Id
	@AutoPopulated
	@TypeDef(type = DataType.UUID)
	private final UUID id;

	@NonNull
	@TypeDef(type = DataType.UUID)
	private final UUID value;
	
	@NonNull
	@TypeDef(type = DataType.UUID)
	private UUID bibId;
	
	// The domain tells us what kind of match point was generated - it is nullable to allow
	// incremental improvements of matchpoints over time. As records are touched they will
	// acquire these values.
	@Nullable
	private final String domain;

	@Nullable
	@Transient
	@Getter(onMethod_ = @Transient)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String sourceValue;

  // Value hint exists to give us something to display in the admin UI that will aid librarians in diagnosing
  // bad cluster records. Previously, we could only show the encoded UUID which left people questioning the provenance
  // of match records. We need to be able to explain our reasoning to users with matching, and this helps us
  // do that.
  @Nullable
  private final String sourceValueHint;
}
