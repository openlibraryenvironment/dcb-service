package services.k_int.integration.marc4j;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;
import org.marc4j.marc.impl.MarcFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.annotation.PostConstruct;

@Requires(classes = Record.class)
@Prototype
public class Marc4jRecordSerde implements Serde<Record> {
	
	// Instanciate the default class here. Works better with native compilation.
	private static final MarcFactory factory = new MarcFactoryImpl();

	private static Logger log = LoggerFactory.getLogger(Marc4jRecordSerde.class);

	private static final String KEY_LEADER = "leader";
	private static final String KEY_INDICATOR_1 = "ind1";
	private static final String KEY_INDICATOR_2 = "ind2";

	private static final String KEY_FIELDS = "fields";
	private static final String KEY_SUBFIELDS = "subfields";
//  private static final String KEY_CONTROLFIELD = "controlfield";
//  private static final String KEY_DATAFIELD = "datafield";
//  private static final String KEY_SUBFIELD = "subfield";

	private static final Pattern REGEX_CTRLFIELD = Pattern.compile( "00[0-9]" );
	private static final Pattern REGEX_DATAFIELD = Pattern.compile( "((0[1-9])|[1-9][0-9])[0-9]" );
	private static final Pattern REGEX_SUBFIELD = Pattern.compile( "[a-z0-9]" );

	@Override
	public void serialize(Encoder enc, EncoderContext context, Argument<? extends Record> type, final Record record)
		throws IOException {
				
		try (Encoder root = enc.encodeObject(type)) {
			root.encodeKey(KEY_LEADER);
			root.encodeString(record.getLeader().toString());
			
			root.encodeKey(KEY_FIELDS);
			try (Encoder fields = root.encodeArray(Argument.of(VariableField.class))) {
				
				// Control fields.
				for (var fieldData : record.getControlFields()) {
					try (Encoder field = fields.encodeObject(Argument.of(ControlField.class))) {
						field.encodeKey(fieldData.getTag());
						field.encodeString(fieldData.getData());
					}
				}

				// Data fields.
				for (var fieldData : record.getDataFields()) {
					try (Encoder field = fields.encodeObject(Argument.of(VariableField.class))) {
						
						field.encodeKey(fieldData.getTag());
						try (Encoder dataField = fields.encodeObject(Argument.of(DataField.class))) {
							
							dataField.encodeKey(KEY_INDICATOR_1);
							dataField.encodeString(fieldData.getIndicator1() + "");
							
							dataField.encodeKey(KEY_INDICATOR_2);
							dataField.encodeString(fieldData.getIndicator2() + "");
							
							dataField.encodeKey(KEY_SUBFIELDS);
							try (Encoder subFields = root.encodeArray(Argument.of(Subfield.class))) {
								for (var subFieldData : fieldData.getSubfields()) {
									
									try (Encoder subField = subFields.encodeObject(Argument.of(Subfield.class))) {
										subField.encodeKey(subFieldData.getCode() + "");
										subField.encodeString(subFieldData.getData());
									}
									
								}
							}
						}
					}
				}
				
			}

		}
	}

	@Override
	public Record deserialize(Decoder dec, DecoderContext context, Argument<? super Record> type) throws IOException {

		// Create our Record
		Record record = factory.newRecord();
		// Should start with an object
		try (Decoder root = dec.decodeObject()) {

			String currentKey;
			while ((currentKey = root.decodeKey()) != null) {
				switch (currentKey) {
					case KEY_LEADER -> {
						Optional.ofNullable(root.decodeString())
							.filter(StringUtils::isNotEmpty)
							.map(factory::newLeader)
							.ifPresent(record::setLeader);
					}
					case KEY_FIELDS -> {
						// Decode the fields.
						decodeFieldsArray(root, record);
					}
					default -> {
						// Unknown root property
						root.skipValue();
					}
				}
			}
		} catch (Exception e) {
			log.error("Error decoding MARC JSON", e);
		}

		return record;
	}

	protected void decodeFieldsArray(final Decoder dec, final Record record) throws IOException {

		try (Decoder fields = dec.decodeArray()) {
			while (fields.hasNextArrayValue()) {

				// Each entry is an object
				try (Decoder field = fields.decodeObject()) {
					// Create our Record
					String currentKey;
					while ((currentKey = field.decodeKey()) != null) {
						if ( REGEX_CTRLFIELD.matcher(currentKey).matches() ) {
							record.addVariableField(
								factory.newControlField(currentKey, field.decodeString()));

						} else if ( REGEX_DATAFIELD.matcher(currentKey).matches() ) {
							// Data field is object.
							final DataField df = factory.newDataField();
							df.setTag(currentKey);

							try (Decoder fieldData = field.decodeObject()) {
								while ((currentKey = fieldData.decodeKey()) != null) {
									switch (currentKey) {
										case KEY_INDICATOR_1 -> {
											final String value = fieldData.decodeString();
											df.setIndicator1(value.length() >= 1 ? value.charAt(0) : ' ');
										}
										case KEY_INDICATOR_2 -> {
											final String value = fieldData.decodeString();
											df.setIndicator2(value.length() >= 1 ? value.charAt(0) : ' ');
										}
										case KEY_SUBFIELDS -> {
											// Decode the subfields.
											try (Decoder subfields = fieldData.decodeArray()) {
												while (subfields.hasNextArrayValue()) {

													// Each entry is an object
													try (Decoder subfield = subfields.decodeObject()) {

														// Create a subfield per entry
														while ((currentKey = subfield.decodeKey()) != null) {
															if ( REGEX_SUBFIELD.matcher(currentKey).matches() ) {
																df.addSubfield(
																	factory.newSubfield(currentKey.charAt(0), subfield.decodeString()));
															} else {
																// subfield not matched
																subfield.skipValue();
															}
														}
													}
												}
											}
										}
										default -> {
											// Unknown field data property
											fieldData.skipValue();
										}
									}
								}
								record.addVariableField(df);
							}

						} else {
							// Unknown field.
							field.skipValue();
						}
					}
				}
			}
		}

	}

}
