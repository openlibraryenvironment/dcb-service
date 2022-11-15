package services.k_int.test.mockserver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.condition.TestActiveCondition;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE })
@ExtendWith(MockServerMicronautJunit5Extension.class)
@MicronautTest
@Factory
@Inherited
@Requires(condition = TestActiveCondition.class)
@Executable
public @interface MockServerMicronautTest {
}
