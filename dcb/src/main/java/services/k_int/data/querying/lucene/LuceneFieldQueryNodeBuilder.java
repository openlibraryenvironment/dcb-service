package services.k_int.data.querying.lucene;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
			Path<String> path = root.get(fieldName);
			return criteriaBuilder.equal(path, fieldText);
		};
		return cb;
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
