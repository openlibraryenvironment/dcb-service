package services.k_int.data.querying.lucene;

import java.util.Map;
import java.util.UUID;

import jakarta.persistence.criteria.JoinType;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.Path;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;
import services.k_int.data.querying.QueryPath;

public class LuceneFieldQueryNodeBuilder<T> implements JpaQuerySpecificationBuilder<T,FieldQueryNode> {

	private static final Logger log = LoggerFactory.getLogger(LuceneFieldQueryNodeBuilder.class);

	/**
	 * Filters whose name is not a property of the entity, and the path each one means.
	 * <p>
	 * These exist because DCB Admin and DCB Admin for Libraries need to filter on
	 * things the entity does not carry directly. Keep the list short: every entry is a
	 * name that only means something because this table says so, and none of them is
	 * discoverable from the schema.
	 *
	 * @see QueryPath
	 */
	public static final Map<String, QueryPath> QUERY_PATHS = Map.of(
		// PatronRequest records the supplier on the supplier request, not on itself
		"supplyingAgencyCode", QueryPath.joining("supplierRequests").matching("localAgency"),

		// PatronRequest carries patronHostlmsCode but not the patron's agency, so callers
		// filtered on the Host LMS - which on a shared system is every co-tenant library
		// rather than one. The agency is resolved onto the requesting identity during
		// patron validation, and that is the only place it is recorded.
		"patronAgencyCode", QueryPath.joining("requestingIdentity", "resolvedAgency").matching("code"),

		// Barcodes are not always stored cleanly enough to compare whole
		"patronBarcode", QueryPath.joining("requestingIdentity").containing("localBarcode"));

	@Override
	public QuerySpecification<T> build(FieldQueryNode fieldNode) throws Exception {
		var fieldName = fieldNode.getFieldAsString();
		var fieldText = fieldNode.getTextAsString();

		log.debug("Field... {}:{}", fieldName, fieldText);

		final var queryPath = QUERY_PATHS.get(fieldName);

		if (queryPath != null) {
			return queryPath.is(fieldText);
		}

		// Default behavior for regular fields
		QuerySpecification<T> cb = (root, query, criteriaBuilder) -> {
			Path<?> path = root.get(fieldName);

			// Filtering on an association -- DCB Admin sends "hostLms: <uuid>" -- has to
			// compare against the association's identifier. Comparing the association path
			// itself asks the framework to turn the text into an entity instance, which
			// fails with "Invalid bean [<uuid>] for type: class ...DataHostLms".
			//
			// The association must be joined before its identifier can be reached, otherwise
			// Micronaut Data raises "An association: [hostLms] needs to be joined before it
			// can be accessed".
			if (path.getJavaType().isAnnotationPresent(MappedEntity.class)) {
				final Path<?> idPath = root.join(fieldName, JoinType.LEFT).get("id");

				return criteriaBuilder.equal(idPath, convertIdentifier(fieldName, fieldText, idPath.getJavaType()));
			}

			return criteriaBuilder.equal(path, fieldText);
		};
		return cb;
	}

	/**
	 * Identifiers are UUIDs throughout DCB, but resolve the target type from the path rather
	 * than assuming it, so a non-UUID key still works.
	 */
	private Object convertIdentifier(String fieldName, String fieldText, Class<?> idType) {
		if (!UUID.class.equals(idType)) {
			return fieldText;
		}

		try {
			return UUID.fromString(fieldText);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
				"Cannot filter on %s: \"%s\" is not a valid identifier".formatted(fieldName, fieldText), e);
		}
	}
}
