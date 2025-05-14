package services.k_int.data.querying.lucene;

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
		QuerySpecification<T> cb = (root, query, criteriaBuilder) -> {
			Path<String> path = root.get(fieldName);
			return criteriaBuilder.equal(path, fieldText);
		};
		return cb;
	}
}
