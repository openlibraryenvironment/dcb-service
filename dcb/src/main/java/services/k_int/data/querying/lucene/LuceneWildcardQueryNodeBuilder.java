package services.k_int.data.querying.lucene;

import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.lucene.queryparser.flexible.standard.nodes.WildcardQueryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.Path;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

public class LuceneWildcardQueryNodeBuilder<T> implements JpaQuerySpecificationBuilder<T,WildcardQueryNode> {

	private static final Logger log = LoggerFactory.getLogger(LuceneWildcardQueryNodeBuilder.class);

	private static final Pattern REGEX_SINGLE_CHAR_WILDCARD = Pattern.compile("\\?");
	private static final Pattern REGEX_MULTI_CHAR_WILDCARD = Pattern.compile("\\*");
	
	@Override
	public QuerySpecification<T> build(WildcardQueryNode wildcardNode) throws Exception {
		// Replace the text 
		var fieldText = Optional.of(wildcardNode.getTextAsString())
				.map(REGEX_SINGLE_CHAR_WILDCARD::matcher)
				.map(m -> m.replaceAll("_"))
				.map(REGEX_MULTI_CHAR_WILDCARD::matcher)
				.map(m -> m.replaceAll("%"))
				.get();
		
		var fieldName = wildcardNode.getFieldAsString();
		
		log.info("Wildcard... {}:{}", fieldName, fieldText);
		QuerySpecification<T> cb = (root, query, criteriaBuilder) -> {
			Path<String> path = root.get(fieldName);
			return criteriaBuilder.like(path, fieldText);
		};
		return cb;
	}
}
