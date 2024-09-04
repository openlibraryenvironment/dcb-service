package services.k_int.features;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.util.ArrayUtils;

@Retention(RUNTIME)
@Documented
@Target({ ElementType.ANNOTATION_TYPE })
@Requires( condition = StabilityLevel.StabilityProfileActive.class )
public @interface StabilityLevel {
	public enum Level {
		ALPHA, BETA
	}

	Level value() default Level.ALPHA;

	static class StabilityProfileActive implements Condition {

		private static boolean hasEnv(@SuppressWarnings("rawtypes") ConditionContext context, Level[] levels) {
			BeanContext beanContext = context.getBeanContext();
			if (!ApplicationContext.class.isAssignableFrom(beanContext.getClass()))
				return true;
			ApplicationContext applicationContext = (ApplicationContext) beanContext;
			Environment environment = applicationContext.getEnvironment();
			Set<String> activeNames = environment.getActiveNames();

			boolean active = Arrays.stream(levels)
					.map(Objects::toString)
					.map(String::toLowerCase)
					.anyMatch(activeNames::contains);

			if (!active) {
				context.fail("None of the required stability profiles are active [%s]".formatted(ArrayUtils.toString(levels)));
			}

			return active;
		}

		@Override
		public boolean matches(@SuppressWarnings("rawtypes") ConditionContext context) {
			
			AnnotationMetadataProvider annProvider = context.getComponent();
			AnnotationMetadata ann = annProvider.getAnnotationMetadata();

			if (!ann.hasStereotype(StabilityLevel.class))
				return true;

			Level[] levels = ann.enumValues(StabilityLevel.class, "value", Level.class);
			return hasEnv(context, levels);
		}
	}
	
	@Retention(RUNTIME)
	@Documented
	@Target({ ElementType.PACKAGE, ElementType.TYPE })
	@StabilityLevel(Level.ALPHA)
	public static @interface Alpha {  }
	
	@Retention(RUNTIME)
	@Documented
	@Target({ ElementType.PACKAGE, ElementType.TYPE })
	@StabilityLevel(Level.BETA)
	public static @interface Beta {  }

}
