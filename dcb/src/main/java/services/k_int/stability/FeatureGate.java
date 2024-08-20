package services.k_int.stability;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;

@Retention(RUNTIME)
@Target(TYPE)
@Requires(condition = FeatureGate.FeatureGateOpen.class)
public @interface FeatureGate {

	String value();

	public static final class FeatureGateOpen implements Condition {

		public FeatureGateOpen() {
			System.out.println("Evaluated");
		}

		@Override
		public boolean matches(@SuppressWarnings("rawtypes") ConditionContext context) {

			AnnotationMetadataProvider annProvider = context.getComponent();
			AnnotationMetadata ann = annProvider.getAnnotationMetadata();

			if (!ann.hasDeclaredAnnotation(FeatureGate.class))
				return true;

			String gateName = ann.stringValue(FeatureGate.class, "value").get();
			boolean active = context.getProperty("features.%s.enabled".formatted(gateName), Boolean.class, false);

			if (!active) {
				context.fail("Required feature gate [%s] is not explicitly enabled".formatted(gateName));
			}
			
			return active;
		}

	}
}
