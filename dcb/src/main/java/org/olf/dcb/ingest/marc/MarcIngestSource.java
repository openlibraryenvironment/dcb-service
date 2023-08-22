package org.olf.dcb.ingest.marc;

import static services.k_int.integration.marc4j.Marc4jRecordUtils.concatSubfieldData;
import static services.k_int.integration.marc4j.Marc4jRecordUtils.extractOrderedSubfields;
import static services.k_int.integration.marc4j.Marc4jRecordUtils.typeFromLeader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.validation.constraints.NotEmpty;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.model.Identifier;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.dcb.processing.matching.goldrush.GoldrushKey;
import org.olf.dcb.storage.RawSourceRepository;
import org.olf.dcb.utils.DCBStringUtilities;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import services.k_int.integration.marc4j.Marc4jRecordUtils;

public interface MarcIngestSource<T> extends IngestSource {

	public static final String NS_GOLDRUSH = "GOLDRUSH";
	final static Pattern REGEX_NAMESPACE_ID_PAIR = Pattern.compile("^((\\(([^)]+)\\))|(([^:]+):))(.*)$");
	final static Pattern REGEX_LINKAGE_245_880 = Pattern.compile("^880-(\\d+)");
	final static Pattern REGEX_REMOVE_PUNCTUATION = Pattern.compile("\\p{Punct}");

	static Logger log = LoggerFactory.getLogger(MarcIngestSource.class);

	default IngestRecordBuilder populateRecordFromMarc(final IngestRecordBuilder ingestRecord, final Record marcRecord) {

		// Leader fields
		enrichWithLeaderInformation(ingestRecord, marcRecord);

		// Title(s)
		enrichWithTitleInformation(ingestRecord, marcRecord);

		// Identifiers
		enrichWithIdentifiers(ingestRecord, marcRecord);

		// Author(s)
		enrichWithAuthorInformation(ingestRecord, marcRecord);

		enrichWithGoldrush(ingestRecord, marcRecord);

		enrichWithCanonicalRecord(ingestRecord, marcRecord);
		
		enrichWithMetadataScore(ingestRecord, marcRecord);

		return ingestRecord;
	}

	@NonNull
	String getDefaultControlIdNamespace();

	default IngestRecordBuilder enrichWithLeaderInformation(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {

		// This page has useful info for how to convert leader character position 06
		// into a value that can be used to interpret 008 fields
		// https://www.itsmarc.com/crs/mergedprojects/helptop1/helptop1/directory_and_leader/idh_leader_06_bib.htm

//		var typeCode = marcRecord.getLeader().getTypeOfRecord();

		ingestRecord.recordStatus(String.valueOf(marcRecord.getLeader().getRecordStatus()));
		ingestRecord.typeOfRecord(String.valueOf(marcRecord.getLeader().getTypeOfRecord()));
		// marcRecord.getLeader.getImplDefined1() will give us 2 characters needed to
		// work out kind of resource - Marc variant specific
		// In marc21 07 is Bibliographic Level
		ingestRecord.derivedType(typeFromLeader(marcRecord.getLeader()));
		return ingestRecord;
	}

	default IngestRecordBuilder enrichWithAuthorInformation(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {

		// II: This block was adding author names as identifiers. Whilst we do want to extract author names,
		// I don't think we want them in identifiers, so commenting out for now.
		/*
		Stream.of("100", "110", "700", "710").map(tag -> marcRecord.getVariableField(tag)).filter(Objects::nonNull)
				.map(DataField.class::cast).map(df -> {

					String authorName = switch (df.getTag()) {
					case "100", "700" -> concatSubfieldData(marcRecord, df.getTag(), "abc", ", ").findFirst().orElse(null);
					default -> df.getSubfieldsAsString("a");
					};

					// Build an ID
					return StringUtils.isEmpty(authorName) ? null : Identifier.build(id -> {
						id.value(authorName).namespace(Objects.requireNonNullElse(df.getSubfieldsAsString("0"), "M" + df.getTag()));
					});
				}).filter(Objects::nonNull).forEach(ingestRecord::addIdentifiers);
		 */

		return ingestRecord;
	}

	default IngestRecordBuilder enrichWithTitleInformation(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {
		// Initial title.
		final String title = Stream.of("245", "243", "240", "246", "222", "210", "240", "247", "130")
				.filter(Objects::nonNull).flatMap(tag -> concatSubfieldData(marcRecord, tag, "abc"))
				.filter(StringUtils::isNotEmpty).reduce(ingestRecord.build().getTitle(), (current, item) -> {
					if (StringUtils.isEmpty(current)) {
						ingestRecord.title(item);
						ingestRecord.addIdentifier(id -> {
							// This allows us to add in important discriminators into the blocking title - edition being
							// the most obvious one for now. Ideally we would normalised this tho into a canonical string
//							List<String> qualifiers = new ArrayList();
//							String edition = null;
//							DataField edition_field = (DataField) marcRecord.getVariableField("250");
//							if ( edition_field != null ) {
//								qualifiers.add(edition_field.getSubfieldsAsString("a"));
//							}
							
							List<String> qualifiers = Marc4jRecordUtils.concatSubfieldData(marcRecord, "250", "a").toList();
							
							// The old style blocking titles arranged words alphabetically, removed duplicates and didn't
							// suffer with double spacing, so using that here as it provides cleaner matching.
							id.namespace("BLOCKING_TITLE").value(
								DCBStringUtilities.generateBlockingString(item, qualifiers)
							);
						});
						return item;
					}

					ingestRecord.otherTitle(item);


					// Keep returning the first title that was set.
					return current;
				});

		log.debug("Title used: {}", title);
		return ingestRecord;
	}

	default IngestRecordBuilder handleControlNumber(final IngestRecordBuilder ingestRecord, final Record marcRecord) {
		// Grab the pair of 001 and 003. These contain the identifier value and
		// namespace respectively

		Optional.ofNullable(marcRecord.getControlNumber()).filter(StringUtils::isNotEmpty).flatMap(cn -> {
			final String cnAuthority = extractControlData(marcRecord, "003").findFirst()
					.orElse(getDefaultControlIdNamespace());

			return Optional.ofNullable(StringUtils.isEmpty(cnAuthority) ? null : Identifier.build(id -> {
				id.namespace(cnAuthority).value(cn);
			}));
		}).ifPresent(ingestRecord::addIdentifiers);

		return ingestRecord;
	}

	default IngestRecordBuilder handleSystemControlNumber(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {
		concatSubfieldData(marcRecord, "035", "a").forEach(val -> {
			final Matcher matcher = REGEX_NAMESPACE_ID_PAIR.matcher(val);

			if (matcher.matches()) {
				ingestRecord.addIdentifier(id -> {
					id.namespace(Objects.requireNonNullElse(matcher.group(3), matcher.group(5))).value(matcher.group(6));
				});
			}
		});

		return ingestRecord;
	}

	static final Map<String, String> IDENTIFIER_FIELD_NAMESPACE = Map.of("010", "LCCN", "020", "ISBN", "022", "ISSN",
			"027", "STRN");

	default IngestRecordBuilder enrichWithIdentifiers(final IngestRecordBuilder ingestRecord, final Record marcRecord) {

		handleControlNumber(ingestRecord, marcRecord);
		handleSystemControlNumber(ingestRecord, marcRecord);

		IDENTIFIER_FIELD_NAMESPACE.keySet().stream().flatMap(tag -> marcRecord.getVariableFields(tag).stream())
				.filter(Objects::nonNull).map(DataField.class::cast).forEach(df -> {
					Optional.ofNullable(df.getSubfieldsAsString("a")).filter(StringUtils::isNotEmpty).ifPresent(sfs -> {
						ingestRecord.addIdentifier(id -> {
							id.namespace(IDENTIFIER_FIELD_NAMESPACE.get(df.getTag())).value(sfs);
						});
					});
				});

		return ingestRecord;
	}

	default IngestRecordBuilder enrichWithGoldrush(final IngestRecordBuilder ingestRecord, final Record marcRecord) {
		final GoldrushKey grk = getGoldrushKey(marcRecord);
		
		return Optional.of(grk.getText())
			.map( StringUtils::trimToNull )
			.map( grVal -> Identifier.build(id -> id.namespace(NS_GOLDRUSH).value(grk.getText())))
			.map( ingestRecord::addIdentifiers )
			.orElse( ingestRecord )
		;
	}


		private static Stream<String> extractControlData(final Record marcRecord, @NotEmpty final String tag) {

		return marcRecord.getVariableFields(tag).stream().filter(Objects::nonNull).map(ControlField.class::cast)
				.map(field -> field.getData());
	}

	Publisher<T> getResources(Instant since);

	IngestRecordBuilder initIngestRecordBuilder(T resource);

	Record resourceToMarc(T resource);

	@Override
	public default Publisher<IngestRecord> apply(Instant since) {

		log.info("Read from the marc source and publish a stream of IngestRecords");

		return Flux.from(getResources(since)).flatMap(this::saveRawAndContinue)
				.doOnError(throwable -> log.warn("ONERROR saving raw record", throwable))
				.flatMap(resource -> {
					return Mono.just(initIngestRecordBuilder(resource))
							.zipWith(Mono.just( resourceToMarc(resource) ) )
									// .map( this::createMatchKey ))
							.map(TupleUtils.function(( ir, marcRecord ) -> {
								return populateRecordFromMarc(ir, marcRecord).build();
							}));
				});
	}

	RawSourceRepository getRawSourceRepository();

	@Transactional
	public default Mono<T> saveRawAndContinue(T resource) {
		log.debug("Save raw {}", resource);
		return Mono.just(resource).zipWhen(res -> Mono.just(resourceToRawSource(res)))
				.flatMap(TupleUtils.function((res, raw) -> {
					return Mono.from(getRawSourceRepository().saveOrUpdate(raw)).thenReturn(res);
				}));
	}

	RawSource resourceToRawSource(T resource);

	private static void parseFromSingleSubfield(Record marcRecord, String fieldTag, char subField,
			Consumer<String> consumer) {
		extractOrderedSubfields((DataField) marcRecord.getVariableField(fieldTag), "" + subField).limit(1).findFirst()
				.ifPresent(consumer);
	}

	public default GoldrushKey getGoldrushKey(final Record marcRecord) {
		GoldrushKey grk = new GoldrushKey();

		// Start with 245. If there is a linkage to a 880, then swap to that instead.
		final DataField field245 = (DataField) marcRecord.getVariableField("245");
		final DataField fieldForTitle = extractOrderedSubfields(field245, "6")
			.map( REGEX_LINKAGE_245_880::matcher )
			.filter( Matcher::matches )
			.findFirst()
			.map( match -> match.group(1) )
			.flatMap( occNum -> {
				final Pattern linked880 = Pattern.compile("^245-" + occNum);
				return marcRecord.getVariableFields("880")
						.stream()
						.map( DataField.class::cast )
						.filter(field880 ->
							extractOrderedSubfields(field880, "6")
								.anyMatch(linked880.asMatchPredicate()))
						.findFirst();
			})
			.orElseGet(() -> field245);
		
		List<String> fields = extractOrderedSubfields(fieldForTitle, "ab").limit(2)
				.toList();

		if (fields.size() > 0) {
			grk.parseTitle(fields.get(0), fields.size() > 1 ? fields.get(1) : null);
		}

		parseFromSingleSubfield(marcRecord, "245", 'h', grk::parseMediaDesignation);
		parseFromSingleSubfield(marcRecord, "260", 'c', grk::parsePubYear);
		parseFromSingleSubfield(marcRecord, "300", 'a', grk::parsePagination);
		parseFromSingleSubfield(marcRecord, "250", 'a', grk::parseEdition);
		parseFromSingleSubfield(marcRecord, "260", 'b', grk::parsePublisher);
		parseFromSingleSubfield(marcRecord, "245", 'p', grk::parseTitlePart);
		parseFromSingleSubfield(marcRecord, "245", 'n', grk::parseTitleNumber);

		char type = marcRecord.getLeader().getTypeOfRecord();
		grk.setRecordType(type);
//		System.out.println( grk.toString() );
//		System.out.println( grk.getText() );
		return grk;
	}

	public default IngestRecordBuilder enrichWithCanonicalRecord(final IngestRecordBuilder irb, final Record marcRecord) {
		Map<String,Object> canonical_metadata = new HashMap<>();
		IngestRecord ir = irb.build();
		canonical_metadata.put("title",ir.getTitle());
		canonical_metadata.put("identifiers",ir.getIdentifiers());
		canonical_metadata.put("derivedType",ir.getDerivedType());
		canonical_metadata.put("recordStatus",ir.getRecordStatus());
		// canonical_metadata.put("typeOfRecord",ir.getTypeOfRecord());
		// canonical_metadata.put("bibLevel",ir.getBibLevel());
		// canonical_metadata.put("materialType",ir.getMaterialType());
		canonical_metadata.put("author",ir.getAuthor());
		canonical_metadata.put("otherAuthors",ir.getOtherAuthors());

		DataField publisher = (DataField) marcRecord.getVariableField("260");
		if ( publisher != null ) {
			setIfSubfieldPresent(publisher,'a',canonical_metadata,"placeOfPublication");
			setIfSubfieldPresent(publisher,'b',canonical_metadata,"publisher");
			setIfSubfieldPresent(publisher,'c',canonical_metadata,"dateOfPublication");
		}

		// Extract some subject metadata
		addToCanonicalMetadata("subjects", "600", "personal-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "610", "corporate-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "611", "meeting-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "630", "uniform-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "647", "named-event", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "648", "chronological-term", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "650", "topical-term", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "653", "index-term-uncontrolled", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "654", "faceted", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "662", "hierarchial-place-name", marcRecord, canonical_metadata);

		addToCanonicalMetadata("agents", "100", "name-personal", marcRecord, canonical_metadata);
		addToCanonicalMetadata("agents", "110", "name-corporate", marcRecord, canonical_metadata);
		addToCanonicalMetadata("agents", "111", "name-meeting", marcRecord, canonical_metadata);

		// addToCanonicalMetadata("agents", "130", "uniform-title", marcRecord, canonical_metadata);

		addToCanonicalMetadata("physical-description", "300", null, marcRecord, canonical_metadata);
		addToCanonicalMetadata("content-type", "336", null, marcRecord, canonical_metadata);
		addToCanonicalMetadata("media-type", "337", null, marcRecord, canonical_metadata);

		DataField edition_field = (DataField) marcRecord.getVariableField("250");
		if (edition_field != null) {
			canonical_metadata.put("edition", tidy(edition_field.getSubfieldsAsString("a")));
		}
		
		return irb.canonicalMetadata(canonical_metadata);
	}

	public default IngestRecordBuilder enrichWithMetadataScore(final IngestRecordBuilder irb, final Record marcRecord) {
		int score = 0;
		Map<String, Object> canonical_metadata = irb.build().getCanonicalMetadata();
		
		if (canonical_metadata != null) {
			
			// Total the counts of the properties in the List
			score = Stream.of("subjects", "agents")
				.map(canonical_metadata::get)
				.filter(Objects::nonNull)
				.map( List.class::cast )
				.mapToInt( List::size )
				.sum();
			
			// Record has metadata - have a bonus point!
			score++;
		}
		
		return irb.metadataScore(score);
	}

	private void setIfSubfieldPresent(DataField f, char subfield, Map<String, Object> target, String key) {
		Subfield subfield_v = f.getSubfield(subfield);
		if ( subfield_v != null ) {
			target.put(key,tidy(subfield_v.getData()));
		}
	}

	private String tidy(String inputstr) {
		String result = null;
		if ( inputstr != null )
			result = REGEX_REMOVE_PUNCTUATION.matcher(inputstr).replaceAll("");
		return result;
	}

	private void addToCanonicalMetadata(String property, String tag, String subtype, Record marcRecord,
			Map<String, Object> canonical_metadata) {

		@SuppressWarnings("unchecked")
		List<Object> the_values = (List<Object>) canonical_metadata.get(property);

		if (the_values == null)
			the_values = new ArrayList<>();

		for (VariableField vf : marcRecord.getVariableFields(tag)) {
			DataField df = (DataField) vf;
			Map<String, String> the_entry = new HashMap<>();
			if (subtype != null)
				the_entry.put("subtype", subtype);
			the_entry.put("label", df.getSubfieldsAsString("abcdefg"));
			the_values.add(the_entry);
		};

		if (the_values.size() > 0) {
			canonical_metadata.put(property, the_values);
		}
	}
}
