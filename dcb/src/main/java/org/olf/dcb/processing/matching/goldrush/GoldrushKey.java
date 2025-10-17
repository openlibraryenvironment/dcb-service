package org.olf.dcb.processing.matching.goldrush;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.utils.DCBStringUtilities;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;

@Data
@MappedEntity
@Setter(AccessLevel.PRIVATE)
public class GoldrushKey {
	
	public static final int TITLE_LENGTH = 60;
	public static final int TITLE_SINGLE_CHAR_THRESHOLD = 45;
	
	private static final Pattern AMPERSAND = Pattern.compile("(\\s+)?&(\\s+)?");
	private static final Pattern SPLIT_CHARS = Pattern.compile("(^|\\s+)(a|an|the)((\\s+)|$)|(\\s|[\\W])+", Pattern.CASE_INSENSITIVE);
	private static final Pattern GMD = Pattern.compile("^([a-zA-Z0-9]{1,5})");
	private static final Pattern PUB_YEAR = Pattern.compile("\\b(c?\\d{4})\\b");
	private static final Pattern PAGINATION = Pattern.compile("^.*?(\\d{1,4})");
	private static final Pattern EDITION_NUM = Pattern.compile("^.*?(\\d{1,3})");
	private static final Pattern EDITION_CHARS = Pattern.compile("^.*?([a-z]{1,3})", Pattern.CASE_INSENSITIVE);
	private static final Pattern PUBLISHER = Pattern.compile("^.*?([a-z\\d\\s]{1,2})", Pattern.CASE_INSENSITIVE);
	private static final Pattern TITLE_PART = Pattern.compile("^.*?([a-z\\d\\s]{1,20})", Pattern.CASE_INSENSITIVE);
	private static final Pattern TITLE_NUMBER = Pattern.compile("^.*?([a-z\\d\\s]{1,10})", Pattern.CASE_INSENSITIVE);
	
	@Nullable
	@Size(max=60)
	String title;

	@Nullable
	@Size(max=5)
	String mediaDesignation;
	
	@Nullable
	Integer pubYear;
	
	@Nullable
	Integer pagination;

	@Nullable
	@Size(max=3)
	String edition;
	
	@Nullable
	@Size(max=2)
	String publisher;
	
	@Nullable
	@Setter(AccessLevel.PUBLIC)
	Character recordType;
	
	@Nullable
	@Size(max=20)
	String titlePart;
	
	@Nullable
	@Size(max=10)
	String titleNumber;
	
	public static GoldrushKey parseGoldrushKey( final String goldrushKey ) {
		GoldrushKey gr = new GoldrushKey();
		int offset = 0;
		for (PartEntry part :  gr.getPaddedParts().toList()) {
			
			final int end = offset + part.padding;
			String value = goldrushKey.substring(offset, end);

			// Padding on the right... Lets just remove the right space.
			part.setValue( value.stripTrailing() );
			
			offset = end;
		}
		
		return gr;
	}
	
	public GoldrushKey parseTitle( final String fielda, @NonNull final String fieldb, @NonNull final String fieldc ) {
		
		// Strip specials '{}
		// Clean fieldA first, as we may not need field b at all.
		
		final StringBuilder buff = new StringBuilder(TITLE_LENGTH);
		
		final Spliterator<String> words = Stream.of(
		  fielda,
		  fieldb,
		  fieldc
		)
			.filter( StringUtils::isNotEmpty )
			.map( text -> AMPERSAND.matcher(text).replaceAll("$1and$2") )
			.map( SPLIT_CHARS::split )
			.flatMap( Stream::of )
			.sequential()
			.filter( StringUtils::isNotEmpty )
			.map( String::trim )
			.spliterator();
		
		boolean exhausted = false;
		while (buff.length() < TITLE_SINGLE_CHAR_THRESHOLD && !exhausted) {
			exhausted = !words.tryAdvance( buff::append );
		}
		
		// If we have tipped the threshold we should now switch to adding the whole word
		// incrementing the insertion point by one until we hit the max or 
		final AtomicInteger insertIndex = new AtomicInteger( buff.length() );
		while (insertIndex.get() < TITLE_LENGTH && !exhausted) {
			exhausted = !words.tryAdvance(word -> {
				final int currentLength = insertIndex.getAndIncrement();
				buff.insert(currentLength, word);
				
				// Necessary as the word we are inserting may be shorter than the last
				buff.setLength(currentLength + word.length());
			});
		}
		
		// If the current length is too long. Truncate.
		if (buff.length() > TITLE_LENGTH) {
			// Truncate
			buff.setLength(TITLE_LENGTH);
		}
		
		this.title = buff.toString();
		
		return this;
	}
	
	public GoldrushKey parseMediaDesignation( final String source ) {
		Optional.ofNullable( DCBStringUtilities.toNoneDiacriticAlphaNumeric( source ) )
			.filter( StringUtils::isNotEmpty )
			.map(text -> GMD.matcher(text))
			.map( regex -> regex.find() ? regex.group(1) : null )
			.ifPresent( this::setMediaDesignation );
		
		return this;
	}
	
	public GoldrushKey parsePubYear( final String source ) {
		Stream.ofNullable( DCBStringUtilities.toNoneDiacriticAlphaNumeric( source ) )
			.filter( StringUtils::isNotEmpty )
			.flatMap(text -> PUB_YEAR.matcher(text).results())
			.map( m -> m.group(1))
			.sequential()
			.sorted()
			.findFirst()
			.map( yearStr -> {
				String year = yearStr;
				if (year.length() > 4) {
					year = year.substring(1);
				}
				return Integer.parseInt(year);
			})
			.ifPresent(this::setPubYear);
		;
		
		return this;
	}
	
	public GoldrushKey parsePagination( final String source ) {
		Optional.ofNullable( DCBStringUtilities.toNoneDiacriticAlphaNumeric( source ) )
			.filter( StringUtils::isNotEmpty )
			.map(text -> PAGINATION.matcher(text))
			.map( regex -> regex.find() ? regex.group(1) : null )
			.map(Integer::parseInt)
			.ifPresent( this::setPagination );
		
		return this;
	}

	public GoldrushKey parseEdition( final String source ) {
		Optional.ofNullable( DCBStringUtilities.toNoneDiacriticAlphaNumeric( source ) )
			.filter( StringUtils::isNotEmpty )
			.map(text -> {
				Matcher m = EDITION_NUM.matcher(text);
				if (m.find()) return m.group(1);
				
				// No number match test for alpha characters
				m = EDITION_CHARS.matcher(text);
				
				return m.find() ? m.group(1) : null;
			})
			.ifPresent( this::setEdition );
		
		return this;
	}
	
	public GoldrushKey parsePublisher( final String source ) {
		Optional.ofNullable( DCBStringUtilities.toNoneDiacriticAlphaNumeric( source ) )
			.filter( StringUtils::isNotEmpty )
			.map(text -> PUBLISHER.matcher(text))
			.map( regex -> regex.find() ? regex.group(1) : null )
			.ifPresent( this::setPublisher );
	
		return this;
	}
	
	public GoldrushKey parseTitlePart( final String source ) {
		Optional.ofNullable( DCBStringUtilities.toNoneDiacriticAlphaNumeric( source ) )
			.filter( StringUtils::isNotEmpty )
			.map(text -> TITLE_PART.matcher(text))
			.map( regex -> regex.find() ? regex.group(1) : null )
			.ifPresent( this::setTitlePart );
	
		return this;
	}
	
	public GoldrushKey parseTitleNumber( final String source ) {
		Optional.ofNullable( DCBStringUtilities.toNoneDiacriticAlphaNumeric( source ) )
			.filter( StringUtils::isNotEmpty )
			.map(text -> TITLE_NUMBER.matcher(text))
			.map( regex -> regex.find() ? regex.group(1) : null )
			.ifPresent( this::setTitleNumber );
	
		return this;
	}
	
	
	private static Integer _numberOfParts;
	public static int getNumberOfParts() {
		if (_numberOfParts != null) return _numberOfParts;
		
		_numberOfParts = (int) new GoldrushKey().getPaddedParts().count();
		return _numberOfParts;
	}
	
	private static String padToLength( final Object val, int desiredLength) {
		return services.k_int.utils.StringUtils.padRightToLength(Objects.toString(val, ""), desiredLength);
	}
	
	private Stream<PartEntry> getPaddedParts() {
		
		final GoldrushKey gr = this;
		
		return Stream.of(
				part("Title", gr::getTitle, gr::setTitle, 60),
				part("MediaDesignation", gr::getMediaDesignation, gr::setMediaDesignation, 5),
				part("PubYear", gr::getPubYear, str -> gr.setPubYear( Integer.valueOf(str) ) , 4),
				part("Pagination", gr::getPagination, str -> gr.setPagination(Integer.valueOf(str)) , 4),
				part("Edition", gr::getEdition, gr::setEdition, 3),
				part("Publisher", gr::getPublisher, gr::setPublisher, 2),
				part("RecordType", gr::getRecordType, str -> gr.setRecordType( str.charAt(0) ), 1),
				part("TitlePart", gr::getTitlePart, gr::setTitlePart, 20),
				part("TitleNumber", gr::getTitleNumber, gr::setTitleNumber, 10))
				.sequential();
	}
	
	public Map<String, String> getParts() {
		
		final Map<String, String> theParts = new LinkedHashMap<>();
		getPaddedParts()
			.forEach(pe -> {
				theParts.computeIfAbsent(pe.name(), _name -> pe.getValue());
			});

		return theParts;
	}
	
	public String getText() {
		return getPaddedParts()
			.map( entry -> padToLength( entry.supplier().get(), entry.padding() ) )
			.collect(Collectors.joining(""))
			.toLowerCase();
	}
	
	private static PartEntry part(String name, Supplier<Serializable> supplier, Consumer<String> consumer, int padding) {
		return new PartEntry(name, supplier, consumer, padding);
	}
	
	public static final record PartEntry(
			String name,
			Supplier<Serializable> supplier,
			Consumer<String> consumer,
			int padding
		) {
		String getValue() {
			return Objects.toString( supplier.get(), null);
		}
		
		void setValue(String val) {
			consumer.accept(val);
		}
	}

}
