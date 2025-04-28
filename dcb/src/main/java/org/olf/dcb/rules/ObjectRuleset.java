package org.olf.dcb.rules;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
			if (!cond.test(subject.getT())) {

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
			if (cond.test(subject.getT())) {
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
	protected static class ObjectRulesetCondition implements Predicate<Object> {
		
		private static final Pattern REGEX_DOT = Pattern.compile("\\.");

		public static enum Operation {
			propertyPresent, propertyValueAnyOf
		}
		
		private Operation operation; // Operation Type.
		private String property; // Must resolve otherwise filter fails.
		private boolean negated = false;
		private String documentation; // Allow an explanation in the json
		private List<String> values;

		@Override
		public boolean test(Object t) {		
			var result = doOp(t);
			if (negated) {
				if (log.isTraceEnabled()) {
					log.trace("Needs negating"); 
				}
				
				result = !result;
			}
			if (log.isTraceEnabled()) {
				log.trace("Evaluating [{}] against operation [{}] resulted in [{}]", t, this, result);
			}
			
			return result;
		}
		
		private boolean doOp (Object t) {
			var val = resolvePropertyPath( t, property, String.class );
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
				log.trace("Testing property [{}] of [{}] for value equalling any of [{}]", property, strValue, values);
			}
			
			return matches;
		}
		
		private <T> Optional<T> resolvePropertyPath(@Nullable Object t, @NonNull String path, Class<T> type) { 
			return Optional.ofNullable(t)
				.flatMap( obj -> resolvePropertyPath(obj, path) )
				.map( obj -> ConversionService.SHARED.convertRequired(obj, type));
		}
		
		private Optional<Object> resolvePropertyPath(@NonNull Object t, @NonNull String path) {
			
			var parts = REGEX_DOT.split(path);
			
			var context = Optional.of( expandValue(t) )
				.map( map -> map.get(parts[0]) );
			
			// Single level...
			if (parts.length == 1) return context;
			
			// More than 1 level of nesting
			for (int i = 1; i<parts.length; i++) {
				final String contextPath = parts[i];
				context = context
					.map( this::expandValue )
					.map(map -> {
						if (log.isTraceEnabled()) {
							log.trace("Attempting to resolve property [{}] against [{}]", contextPath, map);
						}
						var val = map.get(contextPath);
						if (log.isTraceEnabled()) {
							if (val != null) {
								log.trace("Found value [{}]", val);
							} else {
								log.trace("Null or empty");
							}
						}
						return val;
					});
			}
			
			return context;
			
		}
		
		@SuppressWarnings("unchecked")
		private Map<String, Object> expandValue(Object found) {

			// Check for Map
			if (found instanceof Map) {
				
				return ((Map<Object,Object>) found).entrySet().stream()
						.collect(Collectors.toMap(
								entry -> entry.getKey().toString(),
								Entry::getValue));
			}
			// Otherwise, expand the object into properties (after all, the user asked for
			// an expanded parameter)
			return BeanMap.of(found);
		}
		
	}
}
