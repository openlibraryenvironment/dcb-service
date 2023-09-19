package services.k_int.data.querying;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.config.StandardQueryConfigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.inject.Singleton;
import lombok.Setter;
import services.k_int.data.querying.lucene.JpaSpecificationQueryParser;

@Setter
@ConfigurationProperties("services.k-int.data.querying")
@Singleton
public class QueryService {
	
	private boolean allowLeadingWildcard = true;
	
	private final Map<Class<?>, String> defaultPropertyCache = new ConcurrentHashMap<>();
	
	private static final Logger log = LoggerFactory.getLogger(QueryService.class);

	private <T> QuerySpecification<T> parse(String q, Class<T> entityClass) throws QueryNodeException {
		log.debug("parse({})", q);
		JpaSpecificationQueryParser<T> qpHelper = new JpaSpecificationQueryParser<>();
		qpHelper.getQueryConfigHandler().set(StandardQueryConfigHandler.ConfigurationKeys.ALLOW_LEADING_WILDCARD, allowLeadingWildcard);
		QuerySpecification<T> query = qpHelper.parse(q, getDefaultFieldForType( entityClass ));
		log.debug("returning: {}", Objects.toString(query, null));
		return query;
	}
	
	private <T> String getDefaultFieldForType( Class<T> entityClass ) {
		
		// Check the cache first
		return Optional.ofNullable( defaultPropertyCache.get(entityClass) )
			.orElseGet(() -> {
		  	BeanIntrospection<T> intro = BeanIntrospection.getIntrospection(entityClass);
		  	var allProperties = intro.getBeanProperties();
		  	
		  	if (allProperties.size() < 1) {
		  		throw new IllegalStateException("No properties defined for entityclass " + entityClass.getSimpleName());
		  	}
		  	
		  	BeanProperty<T,?> currentCandidate = null;
		  	String firstEncountedProperty = null;
		  	
		  	var propertyIterator = allProperties.iterator();
		  	while ( propertyIterator.hasNext() ) {
		  		var property = propertyIterator.next();
		  		
		  		if (property.hasStereotype(DefaultQueryField.class)) {
		  			log.debug( "Default field {} chosen for {} as annotated with {}" , property.getName(), entityClass.getSimpleName(), DefaultQueryField.class.getSimpleName());
		  			currentCandidate = property;
		  			break; // Stop.
		  		}
		  		
		  		firstEncountedProperty = property.getName();
		  		
		  		// Not explicitly annotated property, check for ID annotation.
		  		// This is the winner if not explicitly chosen.
		  		if (property.hasStereotype(Id.class)) {
		  			currentCandidate = property;
		  		}
		  		
		  		// First "required" property used otherwise
		  		if (currentCandidate == null && !property.isNullable()) {
		  			currentCandidate = property;
		  		}
		  	}
		  	
				final String propName =  currentCandidate != null ? currentCandidate.getName() : firstEncountedProperty;		  	
		  	log.debug( "Using {} as default property for entity {}", propName, entityClass.getSimpleName());

		  	// Cache.
		  	defaultPropertyCache.put(entityClass, propName);
				return propName;
		  });
	}

	public <T> QuerySpecification<T> evaluate(String q, Class<T> c) throws Exception {
		log.debug("evaluate({},{},...)", q, c);
		try {
			QuerySpecification<T> parsed_query = parse(q, c);
			return parsed_query;

		} catch (QueryNodeException qne) {
			log.error("Problem parsing query");
			throw qne;
		}
	}
}
