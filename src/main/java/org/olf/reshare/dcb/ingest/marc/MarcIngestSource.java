package org.olf.reshare.dcb.ingest.marc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.olf.reshare.dcb.ingest.model.Identifier;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import reactor.core.publisher.Flux;

public abstract class MarcIngestSource implements IngestSource {
	
	private final static String REGEX_NAMESPACE_ID_PAIR = "^((\\(([^)]+)\\))|(([^:]+):))(.*)$";

	private static Logger log = LoggerFactory.getLogger(MarcIngestSource.class);

	protected abstract Publisher<Record> getRecords(Instant since);

	protected IngestRecord marcRecordToIngestRecord(final Record marcRecord) {
		return IngestRecord.build(ir -> {
			
			ir.uuid( getUUID5ForId(marcRecord.getId() + "") );
			
			// Title(s)
			enrichWithTitleInformation(ir, marcRecord);
			
			// Identifiers
			enrichWithIdentifiers(ir, marcRecord);
			
			// Author(s)
			enrichWithAuthorInformation(ir, marcRecord);
			
		});
	}
	
	@NonNull
	@NotNull
	protected abstract String getDefaultControlIdNamespace();
	
	protected IngestRecord.Builder enrichWithAuthorInformation ( final IngestRecord.Builder ingestRecord, final Record marcRecord ) {
		
		Stream.of( "100", "110", "700", "710")
			.map( tag -> marcRecord.getVariableField(tag) )
			.filter( Objects::nonNull )
			.map( DataField.class::cast )
			.map( df -> {
				String authorName;
				switch (df.getTag()) {
					case "100":
					case "700":
						authorName = extractSubfieldData(marcRecord, df.getTag(), "abc", ", ").findFirst().orElse(null);
						break;
					default: 
						authorName = df.getSubfieldsAsString("a");
				}
				
				// Build an ID
				return StringUtils.isEmpty(authorName) ? null : Identifier.build(id -> {
					id.value(authorName)
					.namespace(
							Objects.requireNonNullElse(df.getSubfieldsAsString("0"), "M" + df.getTag()) );
				});
			})
			.filter( Objects::nonNull )
			.forEach( ingestRecord::addIdentifiers );
		
		return ingestRecord;
	}
	
	protected IngestRecord.Builder enrichWithTitleInformation ( final IngestRecord.Builder ingestRecord, final Record marcRecord ) {
		// Initial title.
		final String title = Stream.of( "245", "243", "240", "246", "222", "210", "240", "247", "130" )
			.filter( Objects::nonNull )
			.flatMap( tag -> extractSubfieldData(marcRecord, tag, "abc") )
			.filter( StringUtils::isNotEmpty )
			.reduce( ingestRecord.build().title(), ( current, item ) -> {
				if (StringUtils.isEmpty( current )) {
					ingestRecord.title(item);
					return item;
				}
				
				// Keep returning the first title that was set. 
				ingestRecord.addOtherTitles(item);
				return current;
			});
		
		log.debug("Title used: {}", title);
		return ingestRecord;
	}
	
	protected IngestRecord.Builder handleControlNumber( final IngestRecord.Builder ingestRecord, final Record marcRecord ) {
		// Grab the pair of 001 and 003. These contain the identifier value and namespace respectively
		
		Optional.ofNullable(marcRecord.getControlNumber())
			.filter( StringUtils::isNotEmpty )
			.ifPresent( cn -> {
				final String cnAuthority = extractControlData(marcRecord, "003")
						.findFirst()
						.orElse(getDefaultControlIdNamespace());
				
				if (StringUtils.isNotEmpty(cnAuthority)) {
					ingestRecord.addIdentifiers(id -> {
						id.namespace(cnAuthority)
							.value(cn);
					});
				}
			});
		
		return ingestRecord;
	}
	
	protected IngestRecord.Builder handleSystemControlNumber( final IngestRecord.Builder ingestRecord, final Record marcRecord  ) {
		extractSubfieldData(marcRecord, "035", "a")
			.forEach( val -> {
				final Pattern pattern = Pattern.compile(REGEX_NAMESPACE_ID_PAIR);
        final Matcher matcher = pattern.matcher(val);
        
        if (matcher.matches()) {
        	ingestRecord.addIdentifiers(id -> {
        		id.namespace( Objects.requireNonNullElse(matcher.group(3), matcher.group(5)) )
        			.value(matcher.group(6));
        	});
        }
			});
		
		return ingestRecord;
	}
	

	
	private static final Map<String, String> IDENTIFIER_FIELD_NAMESPACE = Map.of(
		"010", "LCCN",
		"020", "ISBN",
		"022", "ISSN",
		"027", "STRN"
	);
	
	protected IngestRecord.Builder enrichWithIdentifiers( final IngestRecord.Builder ingestRecord, final Record marcRecord ) {
		
		handleControlNumber(ingestRecord, marcRecord);
		handleSystemControlNumber(ingestRecord, marcRecord);
		
		IDENTIFIER_FIELD_NAMESPACE.keySet().stream()
			.flatMap( tag -> marcRecord.getVariableFields(tag).stream() )
			.filter( Objects::nonNull )
			.map( DataField.class::cast )
			.forEach(df -> {
				
				Optional.ofNullable(df.getSubfieldsAsString("a"))
					.ifPresent(sfs -> {
						ingestRecord.addIdentifiers(id -> {
							id.namespace(IDENTIFIER_FIELD_NAMESPACE.get(df.getTag()))
								.value(sfs);
						});
					});
			});
		
		return ingestRecord;
	}
	
	private static Stream<String> extractControlData( final Record marcRecord, @NotEmpty final String tag ) {
		
		return marcRecord.getVariableFields(tag).stream()
			.filter( Objects::nonNull )
			.map( ControlField.class::cast )
			.map( field -> field.getData() );
	}
	
	private static Stream<String> extractSubfieldData(final Record marcRecord, @NotEmpty final String tag, @NotEmpty final String subfields) {
		return extractSubfieldData(marcRecord, tag, subfields, " ");
	}
	
	private static Stream<String> extractSubfieldData(final Record marcRecord, @NotEmpty final String tag, @NotEmpty final String subfields, @NotNull final String delimiter) {
		
		final HashMap<Character, Integer> sortValues = new HashMap<>(subfields.length());
		subfields.chars()
			.forEach(cint -> {
				sortValues.put((char)cint, sortValues.size());
			});
		
	
		return marcRecord.getVariableFields(tag).stream()
			.filter( Objects::nonNull )
			.map( DataField.class::cast )
			.map( field -> field.getSubfields(subfields)
					.stream()
					// Ensure requested order is maintained, then order defined.
					.sorted(( sf1, sf2 ) -> {
						int weight1 = Objects.requireNonNullElse(sortValues.get(sf1.getCode()), sortValues.size());
						int weight2 = Objects.requireNonNullElse(sortValues.get(sf2.getCode()), sortValues.size());
						
						return Integer.compare(weight1, weight2);
					})
					.map( sf -> sf.getData() )
					.collect(Collectors.joining(delimiter)));
	}

	@Override
	public Publisher<IngestRecord> apply(Instant since) {

		log.info("Read from the marc source and publish a stream of IngestRecords");

		return Flux.from(getRecords(since)).map(this::marcRecordToIngestRecord);
	}
}
