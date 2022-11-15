package services.k_int.test.mockserver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE })
@ExtendWith(MockServerMicronautJunit5Extension.class)
@MicronautTest(transactional = false)
public @interface MockServerMicronautTest {
}
