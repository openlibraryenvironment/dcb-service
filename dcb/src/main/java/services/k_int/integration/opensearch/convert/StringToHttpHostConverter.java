package services.k_int.integration.opensearch.convert;

import java.net.URI;
import java.util.Optional;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClientBuilder;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

@Singleton
@Requires(classes = RestClientBuilder.class)
public class StringToHttpHostConverter implements TypeConverter<CharSequence, HttpHost> {

	@Override
	public Optional<HttpHost> convert(CharSequence object, Class<HttpHost> targetType, ConversionContext context) {
		String uriString = object.toString();
		URI uri = URI.create(uriString);
		return Optional.of(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()));
	}
}
