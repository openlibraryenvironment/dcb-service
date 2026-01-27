package services.k_int.data.querying.lucene;

import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.RangeQueryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

// Building on existing examples to try and provide support for Lucene range queries
// See https://lucene.apache.org/core/9_9_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#range-searches-heading
// This will enable range searches from DCB Admin that look like this:
// For a range: "latitude: [38 TO 50]"
// For open upper bound:  "dateCreated:[2025-11-03T11:02:13.719454Z TO *]"
// For open lower bound: "dateCreated:[* TO 2025-11-03T11:02:13.719454Z]"
public class LuceneRangeQueryNodeBuilder<T> implements JpaQuerySpecificationBuilder<T, RangeQueryNode> {

	private static final Logger log = LoggerFactory.getLogger(LuceneRangeQueryNodeBuilder.class);

	@Override
	public QuerySpecification<T> build(RangeQueryNode rangeNode) throws Exception {
		String fieldName = rangeNode.getField().toString();

		// Get the actual text values
		String lowerTerm = rangeNode.getLowerBound() instanceof FieldQueryNode ?
			((FieldQueryNode) rangeNode.getLowerBound()).getTextAsString() :
			rangeNode.getLowerBound().toString();

		String upperTerm = rangeNode.getUpperBound() instanceof FieldQueryNode ?
			((FieldQueryNode) rangeNode.getUpperBound()).getTextAsString() :
			rangeNode.getUpperBound().toString();

		boolean lowerInclusive = rangeNode.isLowerInclusive();
		boolean upperInclusive = rangeNode.isUpperInclusive();

		log.debug("Range... {}:[{} TO {}]", fieldName, lowerTerm, upperTerm);

		return (root, query, criteriaBuilder) -> {
			try {
				Path<?> path = root.get(fieldName);
				Class<?> fieldType = path.getJavaType();

				if (Number.class.isAssignableFrom(fieldType)) {
					return buildNumericRangePredicate(
						path, criteriaBuilder, fieldType,
						lowerTerm, upperTerm, lowerInclusive, upperInclusive
					);
				} else if (isDateTimeType(fieldType)) {
					return buildDateTimeRangePredicate(
						path, criteriaBuilder, fieldType,
						lowerTerm, upperTerm, lowerInclusive, upperInclusive
					);
				} else {
					// There is scope to extend this further.
					// But at present it's probably best to keep it numeric and date range only
					log.warn("Field {} is not a supported range type. Type: {}", fieldName, fieldType.getName());
					return null;
				}
			} catch (Exception e) {
				log.error("Error creating range predicate for field {}", fieldName, e);
				return null;
			}
		};
	}

	private boolean isDateTimeType(Class<?> type) {
		return Date.class.isAssignableFrom(type) ||
			Instant.class.isAssignableFrom(type) ||
			LocalDate.class.isAssignableFrom(type) ||
			LocalDateTime.class.isAssignableFrom(type);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Predicate buildNumericRangePredicate(
		Path<?> path,
		CriteriaBuilder cb,
		Class<?> fieldType,
		String lowerTerm,
		String upperTerm,
		boolean lowerInclusive,
		boolean upperInclusive) {

		Path<Comparable> comparablePath = (Path<Comparable>) path;

		// Handle open-ended ranges with "*" wildcard
		boolean isLowerOpen = lowerTerm.equals("*") || lowerTerm.isBlank();
		boolean isUpperOpen = upperTerm.equals("*") || upperTerm.isBlank();

		// Parse values only if they're not the wildcard AND they are not empty
		Comparable lowerValue = !isLowerOpen ? parseNumericValue(lowerTerm, fieldType) : null;
		Comparable upperValue = !isUpperOpen ? parseNumericValue(upperTerm, fieldType) : null;

		// If both bounds are null after parsing (invalid values or both wildcards)
		if (lowerValue == null && upperValue == null) {
			if (isLowerOpen && isUpperOpen) {
				// Match everything (no constraints)
				return cb.isTrue(cb.literal(true));
			}
			// Invalid values provided
			log.warn("Both lower and upper bounds are invalid for range query");
			return cb.isFalse(cb.literal(true)); // Return a "false" predicate to match nothing
		}

		// Handle the various range combinations
		if (lowerValue != null && upperValue != null) {
			// Bounded range (both lower and upper bounds)
			if (lowerInclusive && upperInclusive) {
				return cb.between(comparablePath, lowerValue, upperValue);
			} else {
				// At least one bound is exclusive
				Predicate lower = lowerInclusive ?
					cb.greaterThanOrEqualTo(comparablePath, lowerValue) :
					cb.greaterThan(comparablePath, lowerValue);

				Predicate upper = upperInclusive ?
					cb.lessThanOrEqualTo(comparablePath, upperValue) :
					cb.lessThan(comparablePath, upperValue);

				return cb.and(lower, upper);
			}
		} else if (lowerValue != null) {
			// Only lower bound (greater than/equal to query) - "*" TO upperValue
			return lowerInclusive ?
				cb.greaterThanOrEqualTo(comparablePath, lowerValue) :
				cb.greaterThan(comparablePath, lowerValue);
		} else {
			// Only upper bound (less than/equal to query) - lowerValue TO "*"
			return upperInclusive ?
				cb.lessThanOrEqualTo(comparablePath, upperValue) :
				cb.lessThan(comparablePath, upperValue);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Predicate buildDateTimeRangePredicate(
		Path<?> path,
		CriteriaBuilder cb,
		Class<?> fieldType,
		String lowerTerm,
		String upperTerm,
		boolean lowerInclusive,
		boolean upperInclusive) {

		Path<Comparable> comparablePath = (Path<Comparable>) path;

		// Similar principle to numeric predicate
		// Handle ranges with upper/lower bounds open with "*" wildcard as before
		boolean isLowerOpen = lowerTerm.equals("*") || lowerTerm.isBlank();
		boolean isUpperOpen = upperTerm.equals("*") || upperTerm.isBlank();

		Comparable lowerValue = null;
		Comparable upperValue = null;

		// First parse the lower bound of the range
		// It could come in various formats
		if (!isLowerOpen) {
			try {
				if (Instant.class.isAssignableFrom(fieldType)) {
					lowerValue = parseInstantBound(lowerTerm, true);
				} else if (LocalDateTime.class.isAssignableFrom(fieldType)) {
					lowerValue = parseLocalDateTimeBound(lowerTerm, true);
				} else if (LocalDate.class.isAssignableFrom(fieldType)) {
					lowerValue = LocalDate.parse(lowerTerm); // No special handling needed
				} else if (Date.class.isAssignableFrom(fieldType)) {
					// Fallback for legacy java.util.Date
					lowerValue = Date.from(parseInstantBound(lowerTerm, true));
				}
			} catch (Exception e) {
				log.warn("Failed to parse lower bound date '{}' for field type {}", lowerTerm, fieldType.getName(), e);
			}
		}

		// Same with the upper bound
		if (!isUpperOpen) {
			try {
				// Check if the user supplied a date-only string (e.g., "2024-01-31")
				boolean isDateOnly = isLocalDateString(upperTerm);

				if (Instant.class.isAssignableFrom(fieldType)) {
					upperValue = parseInstantBound(upperTerm, false);
					// If it was a date-only string and inclusive, e.g., "... TO 2024-01-31]"
					// our parser returned "2024-02-01T00:00:00Z". We must make the query
					// exclusive (<) to correctly include all of 2024-01-31.
					// end of day / start of next day paradox. Rest is the same
					if (isDateOnly && upperInclusive) {
						upperInclusive = false;
					}
				} else if (LocalDateTime.class.isAssignableFrom(fieldType)) {
					upperValue = parseLocalDateTimeBound(upperTerm, false);
					if (isDateOnly && upperInclusive) {
						upperInclusive = false;
					}
				} else if (LocalDate.class.isAssignableFrom(fieldType)) {
					upperValue = LocalDate.parse(upperTerm); // No special handling needed
				} else if (Date.class.isAssignableFrom(fieldType)) {
					upperValue = Date.from(parseInstantBound(upperTerm, false));
					if (isDateOnly && upperInclusive) {
						upperInclusive = false;
					}
				}
			} catch (Exception e) {
				log.warn("Failed to parse upper bound date '{}' for field type {}", upperTerm, fieldType.getName(), e);
			}
		}

		// If both bounds are null after parsing (invalid values or both wildcards)
		if (lowerValue == null && upperValue == null) {
			if (isLowerOpen && isUpperOpen) {
				// Match everything (no constraints)
				return cb.isTrue(cb.literal(true));
			}
			// Invalid values provided
			log.warn("Both lower and upper date bounds are invalid for range query");
			return cb.isFalse(cb.literal(true)); // Return a "false" predicate to match nothing
		}

		// Handle the various range combinations
		if (lowerValue != null && upperValue != null) {
			// Bounded range (both lower and upper bounds)
			if (lowerInclusive && upperInclusive) {
				return cb.between(comparablePath, lowerValue, upperValue);
			} else {
				// At least one bound is exclusive
				Predicate lower = lowerInclusive ?
					cb.greaterThanOrEqualTo(comparablePath, lowerValue) :
					cb.greaterThan(comparablePath, lowerValue);

				Predicate upper = upperInclusive ?
					cb.lessThanOrEqualTo(comparablePath, upperValue) :
					cb.lessThan(comparablePath, upperValue);

				return cb.and(lower, upper);
			}
		} else if (lowerValue != null) {
			// Only lower bound (greater than/equal to query)
			return lowerInclusive ?
				cb.greaterThanOrEqualTo(comparablePath, lowerValue) :
				cb.greaterThan(comparablePath, lowerValue);
		} else {
			// Only upper bound (less than/equal to query)
			return upperInclusive ?
				cb.lessThanOrEqualTo(comparablePath, upperValue) :
				cb.lessThan(comparablePath, upperValue);
		}
	}

	private Comparable<?> parseNumericValue(String value, Class<?> targetType) {
		try {
			if (Integer.class.isAssignableFrom(targetType)) {
				return Integer.parseInt(value);
			} else if (Long.class.isAssignableFrom(targetType)) {
				return Long.parseLong(value);
			} else if (Double.class.isAssignableFrom(targetType)) {
				return Double.parseDouble(value);
			} else if (Float.class.isAssignableFrom(targetType)) {
				return Float.parseFloat(value);
			} else if (Short.class.isAssignableFrom(targetType)) {
				return Short.parseShort(value);
			} else if (Byte.class.isAssignableFrom(targetType)) {
				return Byte.parseByte(value);
			} else if (Number.class.isAssignableFrom(targetType)) {
				return Long.parseLong(value);
			}
		} catch (NumberFormatException e) {
			log.error("Failed to parse value '{}' to type {}", value, targetType.getName());
		}
		return null;
	}

	/**
	 * Parses a string term into a LocalDateTime, handling date-only strings.
	 *
	 * @param term         The string value from the query
	 * @param isLowerBound true if this is the lower bound, false for upper
	 * @return A parsed LocalDateTime
	 */
	private LocalDateTime parseLocalDateTimeBound(String term, boolean isLowerBound) {
		try {
			// Try to parse as a full LocalDateTime first
			return LocalDateTime.parse(term);
		} catch (DateTimeParseException e) {
			// If it fails, assume it's a LocalDate (e.g., "2024-01-01")
			// If that fails this will be caught but it could be done more cleanly
			LocalDate ld = LocalDate.parse(term);
			if (isLowerBound) {
				// For a lower bound, use the start of that day
				return ld.atStartOfDay();
			} else {
				// For an upper bound, return the START of the *next* day.
				return ld.plusDays(1).atStartOfDay();
			}
		}
	}

	/**
	 * Checks if a string is a simple YYYY-MM-DD date.
	 * This is a simple regex check and can be made more robust if needed.
	 *
	 */
	private boolean isLocalDateString(String term) {
		if (term == null) return false;
		return term.matches("^\\d{4}-\\d{2}-\\d{2}$");
	}

	/**
	 * Parses a string term into an Instant, handling date-only strings.
	 *
	 * @param term         The string value from the query
	 * @param isLowerBound true if this is the lower bound, false for upper
	 * @return A parsed Instant
	 */
	private Instant parseInstantBound(String term, boolean isLowerBound) {
		try {
			// Try to parse as a full Instant first
			return Instant.parse(term);
		} catch (DateTimeParseException e) {
			// If it fails, assume it's a LocalDate (e.g., "2024-01-01")
			LocalDate ld = LocalDate.parse(term);
			if (isLowerBound) {
				// For a lower bound, use the start of that day (UTC)
				return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
			} else {
				// For an upper bound, return the START of the *next* day.
				// This is used to create an exclusive "less than" query.
				return ld.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
		}
	}
}
