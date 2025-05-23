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

// Building on existing examples to try and provide support for Lucene range queries
// See https://lucene.apache.org/core/9_9_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#range-searches-heading
// This will enable range searches from DCB Admin that look like this "latitude: [38 TO 50]"
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
					// In the future, we may wish to implement handling for date/time range filtering or text-based ranges.
					// Until that happens, we should disallow this and keep it numeric-only.
					log.warn("Date/time range queries are not yet implemented for field {}", fieldName);
					return null;
				} else {
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
}
