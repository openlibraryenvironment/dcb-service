package org.olf.dcb.rules;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.olf.dcb.core.error.DcbError;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.json.tree.JsonObject;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Slf4j
@ToString
@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
//@AllArgsConstructor(onConstructor_ = @JsonCreator())
@MappedEntity
@EachProperty("dcb.rulesets")
public class ObjectRuleset implements Predicate<AnnotatedObject> {

	public static enum Type {
		DISJUNCTIVE, CONJUNCTIVE
	}

	@Id
	private final String name;
	
	@NotNull
	private Type type;

	@Creator
	public ObjectRuleset(@Parameter("name") String name) {
		this.name = name;
	}

	@Nullable
	@Transient
	@JsonIgnore
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.PACKAGE)
	private ObjectMapper objectMapper;

	@NotNull
	@NotEmpty
	@TypeDef(type = DataType.JSON)
	List<ObjectRulesetCondition> conditions;

	@EachProperty(value = "conditions", list = true)
	void setConditions(List<ObjectRulesetCondition> conditions) {
		this.conditions = conditions;
	}

	@Override
	public boolean test( @NonNull AnnotatedObject t ) {
		return switch ( type ) {
			case CONJUNCTIVE -> conjunctiveTest( t );
			case DISJUNCTIVE -> disjunctiveTest( t );
		};
	}
	
	private boolean conjunctiveTest( AnnotatedObject subject ) {
		
		for ( int i=0; i<conditions.size(); i++ ) {
			var cond = conditions.get(i);
			if (!cond.test(subject.getT(), objectMapper)) {

				if (log.isDebugEnabled()) {
					log.debug( "Condition #[{}] of conjunctive ruleset [{}] failed for [{}]. No further evaluation necessary, return false", i+1, name, subject);
				}
				
				return false;
			}
		}
		
		if (log.isDebugEnabled()) {
			log.debug( "All conditions of conjunctive ruleset [{}] passed for [{}]. Return true", name, subject);
		}
		return true;
	}
	
	private boolean disjunctiveTest( AnnotatedObject subject ) {
		
		for ( int i=0; i<conditions.size(); i++ ) {
			var cond = conditions.get(i);
			if (cond.test(subject.getT(), objectMapper)) {
				if (log.isDebugEnabled()) {
					log.debug( "Condition #[{}] of disjunctive ruleset [{}] passed for [{}]. No further evaluation necessary, return true", i+1, name, subject);
				}
				return true;
			}
		}
		
		if (log.isDebugEnabled()) {
			log.debug( "All conditions of disjunctive ruleset [{}] passed for [{}]. Return false", name, subject);
		}
		return false;
	}

	@Serdeable
	@Getter
	@Setter
	@NoArgsConstructor(onConstructor_ = @JsonCreator())
	@EachProperty(value = "conditions", list = true)
	@ToString
	protected static class ObjectRulesetCondition {
		
		private static final Pattern REGEX_DOT = Pattern.compile("\\.");

		public static enum Operation {
			propertyPresent, propertyValueAnyOf
		}
		
		private Operation operation; // Operation Type.
		private String property; // Must resolve otherwise filter fails.
		private boolean negated = false;
		private String documentation; // Allow an explanation in the json
		private List<String> values;

		public boolean test(Object t, ObjectMapper objectMapper) {
			var result = doOp(t, objectMapper);

			if (log.isTraceEnabled()) {
				log.trace("Evaluating [{}] against operation [{}] resulted in [{}]", t, this, result);
			}
			
			if (negated) {
				
				result = !result;
				if (log.isTraceEnabled()) {
					log.trace("Needs negating, new result [{}]", result); 
				}
			}
			
			return result;
		}
		
		private boolean doOp (Object t, ObjectMapper objectMapper) {
			var val = resolvePropertyPath( t, property, String.class, objectMapper);
			if (operation == Operation.propertyPresent) return val.isPresent();
			
			// propertyValueAnyOf...
			
			// Quick return if the property does not exist
			if (val.isEmpty()) {
				if (log.isTraceEnabled()) {
					log.trace("Property [{}] of [{}] does not exist returning false", property, t);
				}
				return false;
			}
			
			// Grab the value
			String strValue = val
					.map(String::trim)
					.get();
			
			// Perform the match
			boolean matches = strValue
				.matches("^(\\Q" + values
					.stream()
					.collect(Collectors.joining("\\E|\\Q")) + "\\E)$");

			if (log.isTraceEnabled()) {
				log.trace("Testing property [{}] of [{}] with value [{}] for value equalling any of [{}] returned [{}]", property, t, strValue, values, matches);
			}
			
			return matches;
		}
		
		private static <T> Optional<T> resolvePropertyPath(@Nullable Object t, @NonNull String path, Class<T> type, ObjectMapper objectMapper) { 
			return Optional.ofNullable(t)
				.flatMap( obj -> resolvePropertyPath(obj, path, objectMapper) )
				.map( ObjectRulesetCondition::unwrapJsonScalarValues )
				.map( obj -> ConversionService.SHARED.convertRequired(obj, type) );
		}
		
		private static Optional<Object> resolvePropertyPath(@NonNull Object t, @NonNull String path, ObjectMapper objectMapper) {
			
			var parts = REGEX_DOT.split(path);
			
			var context = resolvePropertyPart(t, parts[0], objectMapper );
			
			// Single level...
			if (parts.length == 1) return context;
			
			// More than 1 level of nesting
			for (int i = 1; i<parts.length; i++) {
				final String contextPath = parts[i];
				context = context
					.flatMap(obj -> {
						var found = resolvePropertyPart( obj, contextPath, objectMapper );
						if (log.isTraceEnabled()) {
							if (found.isPresent()) {
								log.trace("Found value [{}]", found.get());
							} else {
								log.trace("Null or empty");
							}
						}
						return found;
					});
			}
			
			return context;
		}
		
		private static Object unwrapJsonScalarValues ( Object obj ) {
			if (!JsonNode.class.isAssignableFrom(obj.getClass())) return obj;
			
			JsonNode jsonObj = (JsonNode) obj;
			
			if (jsonObj.isValueNode()) {
				if (log.isTraceEnabled()) {
					var unwrapped = jsonObj.getValue();
					log.trace("Unwrapping JSON scalar resulted in [{}]", unwrapped);
					return unwrapped;
				}
			}
			return obj;
		}
		
		private static Optional<Object> resolvePropertyPart( Object subject, String part, ObjectMapper objectMapper ) {

			if (log.isTraceEnabled()) {
				log.trace("Attempting to resolve property [{}] against [{}]", part, subject);
			}
			if ( isCollectionLike(subject) ) {
				// Filter each item to the allowed Map-like types and return the first
				// with a key that is equal to the part. i.e. the object has a property
				// with the value of <part> (return the first match)
				
				// No match, then empty optional to differentiate that we have no match rather
				// than can't attempt to match.
				
				return Optional.ofNullable( getValueFromCollection( subject, part ) );
			}

			// Check Subject is Map-like and, if so, 'get' the property and return the value.
			if ( isMapLike(subject) ) {
				// Check for a key a that is equal to the part. i.e. the object has a property
				// with the value of <part> (return the first match)
				
				// No match, then empty optional to differentiate that we have no match rather
				// than can't attempt to match.
				
				return Optional.ofNullable( getValueFromMap( subject, part ) );
			}
			
			// Try and convert into an actionable type
			var converted = tryConvertToSupportedStructure( subject, objectMapper );
			
			// Recurse this method with the result
			return resolvePropertyPart( converted, part, objectMapper );
		}
		
		private static Object tryConvertToSupportedStructure (Object obj, ObjectMapper objectMapper) {
			if (log.isTraceEnabled()) {
				log.trace("Try and get Introspected BeanMap of object [{}@{}]", obj.getClass().getName(), obj.hashCode());
			}
			
			// Attempt to fetch an introspected Map.
			if ( BeanIntrospector.SHARED.findIntrospection(obj.getClass()).isPresent() ) {
				return BeanMap.of(obj);
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Could not get Introspected BeanMap");
			}
			
			if (objectMapper != null) {
			
				log.trace("Try to convert object [{}@{}] to JsonNode", obj.getClass().getName(), obj.hashCode());
				try { 
					return objectMapper.writeValueToTree(obj);
	 			} catch (Exception e) {
	 				// NOOP.
	 			}
			} else {
				log.warn("No object converter set on the RuleSet.");
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Could not convert to JsonNode");
			}
			
			throw new DcbError("Could not convert [%s] into map like structure for interospection. "
					+ "Either annotate the class with @Introspected or add a Json Seriualizer for the object.".formatted(obj)); 
		}
		
		private static Object getValueFromMap ( Object mapLike, String key ) {
			
			if (JsonObject.class.isAssignableFrom(mapLike.getClass())) {
				JsonObject jobj = (JsonObject) mapLike;
				return jobj.get(key);
			}
			
			if (Map.class.isAssignableFrom(mapLike.getClass())) {
				Map<?, ?> coll = (Map<?, ?>) mapLike;
				return getWithStringKey ( key, coll );
			}
			
			// Return null
			return null;
		}
		
		private static Object getWithStringKey ( String keyToFind, Map<?, ?> data ) {
			// Do a string comparison of the map key and property to ensure int 23 matches "23" etc.
			if (log.isTraceEnabled()) {
				log.trace("Looking for key [{}] in map structure", keyToFind);
			}
			for (Object objKey : data.keySet()) {
				if (log.isTraceEnabled()) {
					log.trace("Compare [{}] with [{}]", objKey, keyToFind);
				}
				
				var match = Optional.ofNullable(objKey)
					.map( k -> "" + k )
					.filter( keyToFind::equals );
				
				if (match.isPresent()) {
					// Return the value
					return data.get(objKey);
				}
			}
			
			return null;
		}
		
		private static Object getValueFromCollection ( Object collectionLike, String key ) {
			
			if (JsonArray.class.isAssignableFrom(collectionLike.getClass())) {
				JsonArray jarr = (JsonArray) collectionLike;
				for ( var node : jarr.values()) {
					Object val = node.get(key);
					if ( val != null ) return val;
				}
			}
			
			if (Collection.class.isAssignableFrom(collectionLike.getClass())) {
				@SuppressWarnings("unchecked")
				Collection<Object> coll = (Collection<Object>) collectionLike;
				for ( var node : coll ) {
					Object val = getValueFromMap( node, key);
					if ( val != null ) return val;
				}
			}
			
			// Return null by default.
			return null;
		}
		
		private static boolean isCollectionLike (Object found) {
			return Collection.class.isAssignableFrom(found.getClass())
					|| JsonArray.class.isAssignableFrom(found.getClass());
		}
		
		private static boolean isMapLike (Object found) {
			return Map.class.isAssignableFrom(found.getClass())
					|| JsonObject.class.isAssignableFrom(found.getClass());
		}		
	}
}
