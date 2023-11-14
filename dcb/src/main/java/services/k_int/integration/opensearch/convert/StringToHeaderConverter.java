package services.k_int.integration.opensearch.convert;

import java.util.Optional;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.opensearch.client.RestClientBuilder;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

@Singleton
@Requires(classes = RestClientBuilder.class)
public class StringToHeaderConverter implements TypeConverter<CharSequence, Header> {

	@Override
	public Optional<Header> convert(CharSequence object, Class<Header> targetType, ConversionContext context) {
		String header = object.toString();
		if (header.contains(":")) {
			String[] nameAndValue = header.split(":");
			return Optional.of(new BasicHeader(nameAndValue[0], nameAndValue[1]));
		} else {
			return Optional.empty();
		}
	}
}
