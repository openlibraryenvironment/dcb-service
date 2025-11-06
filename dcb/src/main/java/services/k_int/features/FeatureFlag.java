package services.k_int.features;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;

@Retention(RUNTIME)
@Target({TYPE, PACKAGE})
@Requires(condition = FeatureFlag.FeatureFlagSet.class)
public @interface FeatureFlag {

	String value();

	public static final class FeatureFlagSet implements Condition {

		@Override
		public boolean matches(@SuppressWarnings("rawtypes") ConditionContext context) {

			AnnotationMetadataProvider annProvider = context.getComponent();
			AnnotationMetadata ann = annProvider.getAnnotationMetadata();

			if (!ann.hasDeclaredAnnotation(FeatureFlag.class))
				return true;
			
			Features features = context.getBean(Features.class);
			String flagName = ann.stringValue(FeatureFlag.class, "value").get();
			
			boolean active = features.isEnabled(flagName);

			if (!features.isEnabled(flagName)) {
				context.fail("Required feature flag [%s] is not explicitly set".formatted(flagName));
			}
			
			return active;
		}

	}
}
