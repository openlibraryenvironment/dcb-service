package org.olf.dcb.core.model.clustering;

import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.olf.dcb.ingest.model.Author;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Transient;

public interface CoreBibliographicMetadata {
	
	static final Logger log = LoggerFactory.getLogger(CoreBibliographicMetadata.class);
	
	public Map<String, Object> getCanonicalMetadata();

	public default CoreBibliographicMetadata setMetadataValue( @NotNull String key, Object value ) {
		final Map<String, Object> canonicalMetadata = getCanonicalMetadata();
		if (value == null) {
			canonicalMetadata.remove(key);
			return this;
		}
		
		if (!value.equals( canonicalMetadata.get(key) )) {
			canonicalMetadata.put(key, value);
		}
		
		return this;
	}

	public default <T> T getMetadataValue( String key, Class<T> type, ConversionService conversionService ) {
		final Map<String, Object> canonicalMetadata = getCanonicalMetadata();
		
		Object mapVal = canonicalMetadata.get(key);
		if (mapVal == null) return null;
		if (type.isAssignableFrom(mapVal.getClass())) return type.cast(mapVal);
		
		// Create the conversion context to allow us to log any errors in conversion
		ArgumentConversionContext<T> context = ConversionContext.of(Argument.of(type));		
		// Optional<T> value = ConversionService.SHARED.convert(mapVal, context);
		Optional<T> value = conversionService.convert(mapVal, context);
		
		// Print any errors as warnings.
		context.getLastError()
			.ifPresent(error -> {
				log.warn("Error converting to {}, {}. Returning null", type, error.getCause().getMessage());
			});
		
		
		return value.orElseGet(null);
	}
	
	static final String MD_TITLE = "title";
	static final String MD_DATE_OF_PUB = "dateOfPublication";
	static final String MD_PUBLISHER = "publisher";
	static final String MD_PLACE_OF_PUB = "placeOfPublication";
	static final String MD_AUTHOR = "author";
	static final String MD_DERIVED_TYPE = "derivedType";
	static final String MD_RECORD_STATUS = "recordStatus";
	static final String MD_EDITION = "edition";
	static final String MD_LARGE_PRINT = "largePrint";
	
	@Transient
	@Nullable
	public default String getDerivedType(ConversionService conversionService) {
		return getMetadataValue(MD_DERIVED_TYPE, String.class, conversionService);
	}
	
	@Transient
	public default CoreBibliographicMetadata setDerivedType(String derivedType) {
		return setMetadataValue(MD_DERIVED_TYPE, derivedType);
	}

	@Transient
	@Nullable
	public default String getRecordStatus(ConversionService conversionService) {
		return getMetadataValue(MD_RECORD_STATUS, String.class, conversionService);
	}
	
	@Transient
	public default CoreBibliographicMetadata setRecordStatus(String recordStatus) {
		return setMetadataValue(MD_RECORD_STATUS, recordStatus);
	}	
	
	@Transient
	public default CoreBibliographicMetadata setTitle(String title) {
		return setMetadataValue(MD_TITLE, title);
	}

	@Transient
	@Nullable
	public default String getTitle(ConversionService conversionService) {
		return getMetadataValue(MD_TITLE, String.class, conversionService);
	}
	
	@Transient
	@Nullable
	public default Author getAuthor(ConversionService conversionService) {
		return getMetadataValue(MD_AUTHOR, Author.class, conversionService);
	}

	@Transient
	public default CoreBibliographicMetadata setAuthor(Author author) {
		return setMetadataValue(MD_AUTHOR, author);
	}

	@Transient
	@Nullable
	public default String getPlaceOfPublication(ConversionService conversionService) {
		return getMetadataValue(MD_PLACE_OF_PUB, String.class, conversionService);
	}
	
	@Transient
	public default CoreBibliographicMetadata setPlaceOfPublication(String placeOfPublication) {
		return setMetadataValue(MD_PLACE_OF_PUB, placeOfPublication);
	}

	@Transient
	@Nullable
	public default String getPublisher(ConversionService conversionService) {
		return getMetadataValue(MD_PUBLISHER, String.class, conversionService);
	}
	
	@Transient
	public default CoreBibliographicMetadata setPublisher(String publisher) {
		return setMetadataValue(MD_PUBLISHER, publisher);
	}
	
	@Transient
	public default CoreBibliographicMetadata setDateOfPublication(String dateOfPublication) {
		return setMetadataValue(MD_DATE_OF_PUB, dateOfPublication);
	}

	@Transient
	@Nullable
	public default String getDateOfPublication(ConversionService conversionService) {
		return getMetadataValue(MD_DATE_OF_PUB, String.class, conversionService);
	}
	
	@Transient
	@Nullable
	public default String getEdition(ConversionService conversionService) {
		return getMetadataValue(MD_EDITION, String.class, conversionService);
	}
	
	@Transient
	public default CoreBibliographicMetadata setEdition(String edition) {
		return setMetadataValue(MD_EDITION, edition);
	}
		
	@Transient
	@Nullable
	public default boolean isLargePrint(ConversionService conversionService) {
		return getMetadataValue(MD_LARGE_PRINT, Boolean.class, conversionService);
	}
	
	@Transient
	public default CoreBibliographicMetadata setLargePrint(boolean largePrint) {
		return setMetadataValue(MD_LARGE_PRINT, largePrint);
	}
}
