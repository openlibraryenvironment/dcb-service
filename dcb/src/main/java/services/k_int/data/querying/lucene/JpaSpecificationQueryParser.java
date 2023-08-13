package services.k_int.data.querying.lucene;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.QueryParserHelper;
import org.apache.lucene.queryparser.flexible.standard.config.StandardQueryConfigHandler;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.queryparser.flexible.standard.processors.StandardQueryNodeProcessorPipeline;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;

public class JpaSpecificationQueryParser<T> extends QueryParserHelper {
		public JpaSpecificationQueryParser() {
	    super(
        new StandardQueryConfigHandler(),
        new StandardSyntaxParser(),
        new StandardQueryNodeProcessorPipeline(null),
        new LuceneJpaQuerySpecificationBuilder<T>());
	    
	}

	@SuppressWarnings("unchecked")
	@Override
	public QuerySpecification<T> parse(String query, String defaultField) throws QueryNodeException {
		return (QuerySpecification<T>) super.parse(query, defaultField);
	}
}
