package services.k_int.serde;

import java.io.IOException;
import java.util.List;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

@Prototype
public class DefaultPageSerializer implements Serializer<Page<Object>> {

	@Override
	public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Page<Object>> type, Page<Object> page) throws IOException {
		try (Encoder e = encoder.encodeObject(type)) {

			e.encodeKey("content");
	
			@SuppressWarnings("unchecked")
			Argument<List<Object>> contentType = Argument.listOf((Argument<Object>) type.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
			context.findSerializer(contentType)
				.createSpecific(context, contentType)
				.serialize(e, context, contentType, page.getContent());
	
			e.encodeKey("pageable");
			Argument<Pageable> pageable = Argument.of(Pageable.class);
			context.findSerializer(pageable)
				.createSpecific(context, pageable)
				.serialize(e, context, pageable, page.getPageable());
	
			e.encodeKey("totalSize");
			e.encodeLong(page.getTotalSize());
		}

	}
}
