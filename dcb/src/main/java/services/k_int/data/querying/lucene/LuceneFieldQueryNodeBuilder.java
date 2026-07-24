package services.k_int.data.querying.lucene;

import java.util.UUID;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.Path;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

public class LuceneFieldQueryNodeBuilder<T> implements JpaQuerySpecificationBuilder<T,FieldQueryNode> {

	private static final Logger log = LoggerFactory.getLogger(LuceneFieldQueryNodeBuilder.class);

	@Override
	public QuerySpecification<T> build(FieldQueryNode fieldNode) throws Exception {
		var fieldName = fieldNode.getFieldAsString();
		var fieldText = fieldNode.getTextAsString();

		log.debug("Field... {}:{}", fieldName, fieldText);

		// Handle special fields - carefully - to provide filtering for them in DCB Admin / Admin for Libraries
		// This is only for fields we cannot handle normally and should not be overused.
		if ("supplyingAgencyCode".equals(fieldName)) {
			return buildSupplyingAgencyCodeQuery(fieldText);
		}
		if ("patronBarcode".equals(fieldName)) {
			return buildPatronBarcodeQuery(fieldText);
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

	private QuerySpecification<T> buildSupplyingAgencyCodeQuery(String agencyCode) {
		return (root, query, criteriaBuilder) -> {
			// Join with SupplierRequest
			Join<Object, Object> supplierRequestJoin = root.join("supplierRequests", JoinType.LEFT);
			// May need ordering or selection to ensure we get the correct supplier request
			return criteriaBuilder.equal(supplierRequestJoin.get("localAgency"), agencyCode);
		};
	}

	private QuerySpecification<T> buildPatronBarcodeQuery(String barcode) {
		return (root, query, criteriaBuilder) -> {
			Join<Object, Object> identityJoin = root.join("requestingIdentity", JoinType.LEFT);
			// LIKE used because barcodes are sometimes a little weird. To be reviewed
			return criteriaBuilder.like(identityJoin.get("localBarcode"), "%" + barcode + "%");		};
	}
}
