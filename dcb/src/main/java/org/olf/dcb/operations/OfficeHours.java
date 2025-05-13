package org.olf.dcb.operations;

import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Start and End are assumed to be UTC.
 * If no data is present for either start/end then always assume outside hours. 
 * 
 * If ONLY start or end is supplied the other value defaults to day start/end 
 * 
 * @author Steve Osguthorpe
 */
@Data
@Slf4j
@ConfigurationProperties("dcb.officehours")
public class OfficeHours {

	private static final OffsetTime DEFAULT_START = OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC);
	private static final OffsetTime DEFAULT_END = OffsetTime.of(23, 59, 59, 999_999_999, ZoneOffset.UTC);
	
	private OffsetTime asUTC(LocalTime utcTime) {
		return utcTime.atOffset(ZoneOffset.UTC);
	}
	
	@ConfigurationInject
	public OfficeHours( @Nullable	String start, @Nullable String end ) {
		this.start = Optional.ofNullable(start)
				.map( LocalTime::parse )
				.map( this::asUTC );
		
		this.end = Optional.ofNullable( end )
				.map( LocalTime::parse )
				.map( this::asUTC );
	}
	
	@Nullable
	private final Optional<OffsetTime> start;
	
	@Nullable
	private final Optional<OffsetTime> end;

	public boolean isInsideHours( @NonNull @NotNull OffsetTime time ) {
		if ( bothValuesNotSupplied() ) {
			log.debug("No office hours data, assuming outside hours.");
			return false;
		}
		
		OffsetTime timeToCompare = time.withOffsetSameInstant(ZoneOffset.UTC);
		
		// To do this comparison we'll use default values for null to allow none present start/end to pass.
		OffsetTime timeStart = getStart().orElse(DEFAULT_START);
		OffsetTime timeEnd = getEnd().orElse(DEFAULT_END);
		
		int startComp = timeToCompare.compareTo(timeStart);		
		int endComp = timeToCompare.compareTo(timeEnd);
		
		boolean inside = startComp > -1 && endComp < 1;

		log.debug("[{}] Evaluated as {} office hours [{} -> {}]", timeToCompare, (inside ? "inside" : "ouside"), timeStart, timeEnd);
		
		return startComp > -1 && endComp < 1;
	}
	
	public boolean isOutsideHours( @NonNull @NotNull OffsetTime time ) {
		return !isInsideHours(time);
	}
	
	public boolean isInsideHours() {
		return isInsideHours(OffsetTime.now());
	}
	
	public boolean isOutsideHours() {
		return isOutsideHours(OffsetTime.now());
	}
	
	private boolean bothValuesNotSupplied() {
		return getStart().isEmpty() && getEnd().isEmpty();
	}
	
}
