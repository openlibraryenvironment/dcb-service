package services.k_int.integration.opensearch.conditon;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.opensearch.client.RestClient;

import io.micronaut.context.annotation.Requires;
import services.k_int.integration.opensearch.OpenSearchearchSettings;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = OpenSearchearchSettings.PREFIX)
@Requires(classes = { RestClient.class })
public @interface RequiresOpenSearch {
}
