package org.olf.reshare.dcb.ingest.marc;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.olf.reshare.dcb.ingest.model.Identifier;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.reshare.dcb.ingest.model.RawSource;
import org.olf.reshare.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

public interface MarcIngestSource<T> extends IngestSource {
	
	final static String REGEX_NAMESPACE_ID_PAIR = "^((\\(([^)]+)\\))|(([^:]+):))(.*)$";

	static Logger log = LoggerFactory.getLogger(MarcIngestSource.class);
	
	static Comparator<Subfield> createSubFieldComparator(final HashMap<Character, Integer> sortValues) {
		return ( sf1, sf2 ) -> {
			int weight1 = Objects.requireNonNullElse(sortValues.get(sf1.getCode()), sortValues.size());
			int weight2 = Objects.requireNonNullElse(sortValues.get(sf2.getCode()), sortValues.size());
			
			return Integer.compare(weight1, weight2);
		};
	}

	default IngestRecordBuilder populateRecordFromMarc ( final IngestRecordBuilder ingestRecord, final Record marcRecord ) {

		// Leader fields
		enrichWithLeaderInformation(ingestRecord, marcRecord);
			
		// Title(s)
		enrichWithTitleInformation(ingestRecord, marcRecord);
		
		// Identifiers
		enrichWithIdentifiers(ingestRecord, marcRecord);
		
		// Author(s)
		enrichWithAuthorInformation(ingestRecord, marcRecord);
		
		return ingestRecord;
	}
	
	@NonNull
	@NotNull
	String getDefaultControlIdNamespace();
	
	default IngestRecordBuilder enrichWithLeaderInformation ( final IngestRecordBuilder ingestRecord, final Record marcRecord ) {

		// This page has useful info for how to convert leader character position 06 into a value that can be used to interpret 008 fields
		// https://www.itsmarc.com/crs/mergedprojects/helptop1/helptop1/directory_and_leader/idh_leader_06_bib.htm
		
//		var typeCode = marcRecord.getLeader().getTypeOfRecord();
		
		
		// ingestRecord.recordStatus(marcRecord.getLeader().getRecordStatus())
		// ingestRecord.typeOfRecord(marcRecord.getLeader().getTypeOfRecord())
		return ingestRecord;
   }

	default IngestRecordBuilder enrichWithAuthorInformation ( final IngestRecordBuilder ingestRecord, final Record marcRecord ) {
		
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
	
	default IngestRecordBuilder enrichWithTitleInformation ( final IngestRecordBuilder ingestRecord, final Record marcRecord ) {
		// Initial title.
		final String title = Stream.of( "245", "243", "240", "246", "222", "210", "240", "247", "130" )
			.filter( Objects::nonNull )
			.flatMap( tag -> extractSubfieldData(marcRecord, tag, "abc") )
			.filter( StringUtils::isNotEmpty )
			.reduce( ingestRecord.build().getTitle(), ( current, item ) -> {
				if (StringUtils.isEmpty( current )) {
					ingestRecord.title(item);
					return item;
				}
				
				ingestRecord.otherTitle(item);
				
				// Keep returning the first title that was set. 
				return current;
			});
		
		log.debug("Title used: {}", title);
		return ingestRecord;
	}
	
	default IngestRecordBuilder handleControlNumber( final IngestRecordBuilder ingestRecord, final Record marcRecord ) {
		// Grab the pair of 001 and 003. These contain the identifier value and namespace respectively
		
		Optional.ofNullable(marcRecord.getControlNumber())
			.filter( StringUtils::isNotEmpty )
			.flatMap( cn -> {
				final String cnAuthority = extractControlData(marcRecord, "003")
						.findFirst()
						.orElse(getDefaultControlIdNamespace());
				
				return Optional.ofNullable( StringUtils.isEmpty(cnAuthority) ? null : Identifier.build(id -> {
						id.namespace(cnAuthority)
							.value(cn);
					}));
			})
			.ifPresent(ingestRecord::addIdentifiers);
		
		return ingestRecord;
	}
	
	default IngestRecordBuilder handleSystemControlNumber( final IngestRecordBuilder ingestRecord, final Record marcRecord ) {
		extractSubfieldData(marcRecord, "035", "a")
			.forEach( val -> {
				final Pattern pattern = Pattern.compile(REGEX_NAMESPACE_ID_PAIR);
        final Matcher matcher = pattern.matcher(val);
        
        if (matcher.matches()) {
        	ingestRecord.addIdentifier(id -> {
        		id.namespace( Objects.requireNonNullElse(matcher.group(3), matcher.group(5)) )
        			.value(matcher.group(6));
        	});
        }
			});
		
		return ingestRecord;
	}
	
	static final Map<String, String> IDENTIFIER_FIELD_NAMESPACE = Map.of(
		"010", "LCCN",
		"020", "ISBN",
		"022", "ISSN",
		"027", "STRN"
	);
	
	default IngestRecordBuilder enrichWithIdentifiers( final IngestRecordBuilder ingestRecord, final Record marcRecord ) {
		
		handleControlNumber(ingestRecord, marcRecord);
		handleSystemControlNumber(ingestRecord, marcRecord);
		
		IDENTIFIER_FIELD_NAMESPACE.keySet().stream()
			.flatMap( tag -> marcRecord.getVariableFields(tag).stream() )
			.filter( Objects::nonNull )
			.map( DataField.class::cast )
			.forEach(df -> {
				Optional.ofNullable(df.getSubfieldsAsString("a"))
					.filter( StringUtils::isNotEmpty )
					.ifPresent(sfs -> {
						ingestRecord.addIdentifier(id -> {
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
			.forEachOrdered(cint -> {
				sortValues.put((char)cint, sortValues.size());
			});
	
		return marcRecord.getVariableFields(tag).stream()
			.filter( Objects::nonNull )
			.map( DataField.class::cast )
			.map( field -> field.getSubfields(subfields)
					.stream()
					// Ensure requested order is maintained, then order defined.
					.sorted(createSubFieldComparator(sortValues))
					.map( sf -> sf.getData() )
					.collect(Collectors.joining(delimiter)));
	}
	
	Publisher<T> getResources( Instant since );
	IngestRecordBuilder initIngestRecordBuilder ( T resource );
	Record resourceToMarc( T resource );

	@Override
	public default Publisher<IngestRecord> apply( Instant since ) {

		log.info("Read from the marc source and publish a stream of IngestRecords");

		return Flux.from(getResources(since))
			.flatMap(this::saveRawAndContinue)
			.flatMap(resource -> {
				return Mono.just( initIngestRecordBuilder( resource ) )
					.map( ir -> {
						Record marcRecord = resourceToMarc( resource );
						return populateRecordFromMarc(ir, marcRecord).build();
					});
			});
	}
	
	RawSourceRepository getRawSourceRepository();
	
	@Transactional
	public default Mono<T> saveRawAndContinue(T resource) {
		return Mono.just(resource)
			.zipWhen(res -> Mono.just(resourceToRawSource(res)))
			.flatMap(TupleUtils.function(( res, raw ) -> {
				return Mono.from(getRawSourceRepository().saveOrUpdate(raw))
					.thenReturn(res);
			}));
	}

	RawSource resourceToRawSource(T resource);
}
