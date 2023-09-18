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
	    
		// this.getQueryConfigHandler().setAllowLeadingWildcard(true);
		// https://lucene.apache.org/core/9_7_0/queryparser/org/apache/lucene/queryparser/flexible/standard/processors/AllowLeadingWildcardProcessor.html
		this.getQueryConfigHandler().set(StandardQueryConfigHandler.ConfigurationKeys.ALLOW_LEADING_WILDCARD,true);
		// Looking for a way to allow leading *
		//
		// https://lucene.apache.org/core/9_1_0/queryparser/org/apache/lucene/queryparser/flexible/standard/parser/StandardSyntaxParser.html
		// https://lucene.apache.org/core/9_1_0/queryparser/org/apache/lucene/queryparser/flexible/standard/StandardQueryParser.html
		// var config =  this.getQueryConfigHandler();
		// config.setAllowLeadingWildcard(true);
	}

	@SuppressWarnings("unchecked")
	@Override
	public QuerySpecification<T> parse(String query, String defaultField) throws QueryNodeException {
		return (QuerySpecification<T>) super.parse(query, defaultField);
	}
}
