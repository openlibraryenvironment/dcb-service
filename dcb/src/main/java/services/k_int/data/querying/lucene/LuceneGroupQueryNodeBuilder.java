package services.k_int.data.querying.lucene;

import org.apache.lucene.queryparser.flexible.core.nodes.GroupQueryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

public class LuceneGroupQueryNodeBuilder<T> implements JpaQuerySpecificationBuilder<T,GroupQueryNode> {

	private static final Logger log = LoggerFactory.getLogger(LuceneGroupQueryNodeBuilder.class);
	
	@Override
	public QuerySpecification<T> build(GroupQueryNode fieldNode) throws Exception {
		return this.processQueryNode(fieldNode.getChild())
			.findFirst()
			.orElse(null);
	}
}
