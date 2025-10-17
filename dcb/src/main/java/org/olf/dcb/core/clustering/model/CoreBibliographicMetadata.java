package org.olf.dcb.core.clustering.model;

import java.util.Map;
import java.util.Optional;

import org.olf.dcb.ingest.model.Author;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import jakarta.validation.constraints.NotNull;
import services.k_int.micronaut.StaticHelpers;

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
	
	private ConversionService getConversionService() {
		
		return StaticHelpers.get().getConversionService();
		
//		return Application.getCurrentContext().getConversionService();
	}

	public default <T> T getMetadataValue( String key, Class<T> type ) {
		final Map<String, Object> canonicalMetadata = getCanonicalMetadata();
		
		Object mapVal = canonicalMetadata.get(key);
		if (mapVal == null) return null;
		if (type.isAssignableFrom(mapVal.getClass())) return type.cast(mapVal);
		
		// Create the conversion context to allow us to log any errors in conversion
		ArgumentConversionContext<T> context = ConversionContext.of(Argument.of(type));
		
		// Optional<T> value = ConversionService.SHARED.convert(mapVal, context);
		Optional<T> value = getConversionService().convert(mapVal, context);
		
		// Print any errors as warnings.
		context.getLastError()
			.ifPresent(error -> {
				log.warn("Error converting to {}, {}. Returning null", type, error.getCause().getMessage());
			});
		
		
		return value.orElse(null);
	}
	
	public default <T> T getMetadataValue( String key, Class<T> type, T defaultValue ) {
		return Optional.ofNullable(getMetadataValue( key, type ))
			.orElse(defaultValue);
	}
	
	static final String MD_TITLE = "title";
	static final String MD_DATE_OF_PUB = "dateOfPublication";
	static final String MD_PUBLISHER = "publisher";
	static final String MD_PLACE_OF_PUB = "placeOfPublication";
	static final String MD_AUTHOR = "author";
	static final String MD_DERIVED_TYPE = "derivedType";
	static final String MD_DERIVED_FORM_OF_ITEM = "derivedFormOfItem";
	static final String MD_FORM_OF_ITEM = "formOfItem";
	static final String MD_RECORD_STATUS = "recordStatus";
	static final String MD_EDITION = "edition";
	static final String MD_LARGE_PRINT = "largePrint";
	
	@Nullable
	public default String getDerivedType() {
		return getMetadataValue(MD_DERIVED_TYPE, String.class);
	}

	public default CoreBibliographicMetadata setDerivedType(String derivedType) {
		return setMetadataValue(MD_DERIVED_TYPE, derivedType);
	}

  @Nullable
  public default String getDerivedFormOfItem() {
    return getMetadataValue(MD_DERIVED_FORM_OF_ITEM, String.class);
  }

  public default CoreBibliographicMetadata setDerivedFormOfItem(String derivedFormOfItem) {
    return setMetadataValue(MD_DERIVED_FORM_OF_ITEM, derivedFormOfItem);
  }

  @Nullable
  public default String getFormOfItem() {
    return getMetadataValue(MD_FORM_OF_ITEM, String.class);
  }

	public default CoreBibliographicMetadata setFormOfItem(String formOfItem) {
		return setMetadataValue(MD_FORM_OF_ITEM, formOfItem);
	}
	
	@Nullable
	public default String getRecordStatus() {
		return getMetadataValue(MD_RECORD_STATUS, String.class);
	}
	
	public default CoreBibliographicMetadata setRecordStatus(String recordStatus) {
		return setMetadataValue(MD_RECORD_STATUS, recordStatus);
	}	
	
	public default CoreBibliographicMetadata setTitle(String title) {
		return setMetadataValue(MD_TITLE, title);
	}

	@Nullable
	public default String getTitle() {
		return getMetadataValue(MD_TITLE, String.class);
	}
	
	@Nullable
	public default Author getAuthor() {
		return getMetadataValue(MD_AUTHOR, Author.class);
	}

	public default CoreBibliographicMetadata setAuthor(Author author) {
		return setMetadataValue(MD_AUTHOR, author);
	}

	@Nullable
	public default String getPlaceOfPublication() {
		return getMetadataValue(MD_PLACE_OF_PUB, String.class);
	}
	
	public default CoreBibliographicMetadata setPlaceOfPublication(String placeOfPublication) {
		return setMetadataValue(MD_PLACE_OF_PUB, placeOfPublication);
	}

	@Nullable
	public default String getPublisher() {
		return getMetadataValue(MD_PUBLISHER, String.class);
	}
	
	public default CoreBibliographicMetadata setPublisher(String publisher) {
		return setMetadataValue(MD_PUBLISHER, publisher);
	}
	
	public default CoreBibliographicMetadata setDateOfPublication(String dateOfPublication) {
		return setMetadataValue(MD_DATE_OF_PUB, dateOfPublication);
	}

	@Nullable
	public default String getDateOfPublication() {
		return getMetadataValue(MD_DATE_OF_PUB, String.class);
	}
	
	@Nullable
	public default String getEdition() {
		return getMetadataValue(MD_EDITION, String.class);
	}
	
	public default CoreBibliographicMetadata setEdition(String edition) {
		return setMetadataValue(MD_EDITION, edition);
	}
		
	public default boolean isLargePrint() {
		// Return type of boolean is incompatible with null, supply default.
		return getMetadataValue(MD_LARGE_PRINT, Boolean.class, false);
	}
	
	public default CoreBibliographicMetadata setLargePrint(boolean largePrint) {
		return setMetadataValue(MD_LARGE_PRINT, largePrint);
	}
}
