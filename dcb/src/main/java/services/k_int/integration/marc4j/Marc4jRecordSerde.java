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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.Serde;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.jackson.JacksonDecoder;
import jakarta.inject.Singleton;

@Requires(classes = Record.class)
@Singleton
public class Marc4jRecordSerde extends JsonDeserializer<org.marc4j.marc.Record> implements Serde<Record> {
	
	// Instanciate the default class here. Works better with native compilation.
	private static final MarcFactory factory = new MarcFactoryImpl();
	
	private final SerdeConfiguration serdeConfiguration;
	private final SerdeRegistry registry;

	private static Logger log = LoggerFactory.getLogger(Marc4jRecordSerde.class);

	private static final String KEY_LEADER = "leader";
	private static final String KEY_INDICATOR_1 = "ind1";
	private static final String KEY_INDICATOR_2 = "ind2";

	private static final String KEY_FIELDS = "fields";
	private static final String KEY_SUBFIELDS = "subfields";

	private static final String KEY_VALUE_INDICATOR = "";
  private static final String KEY_TAG = "tag";
  private static final String KEY_CONTROLFIELD = "controlfield";
  private static final String KEY_DATAFIELD = "datafield";
  private static final String KEY_SUBFIELD = "subfield";
  private static final String KEY_CODE = "code";

	private static final Pattern REGEX_CTRLFIELD = Pattern.compile( "00[0-9]" );
	private static final Pattern REGEX_DATAFIELD = Pattern.compile( "((0[1-9])|[1-9][0-9])[0-9]" );
	private static final Pattern REGEX_SUBFIELD = Pattern.compile( "[a-z0-9]" );

	public Marc4jRecordSerde(@NonNull SerdeConfiguration serdeConfiguration, @NonNull SerdeRegistry registry) {
		this.serdeConfiguration = serdeConfiguration;
		this.registry = registry;
	}
	
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
						final String val = fieldData.getData();
						if (val == null) {
							log.info("Field {} had a null value", fieldData.getTag());
							field.encodeNull();
						} else {
							
							field.encodeString(fieldData.getData());
						}
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
										final String val = subFieldData.getData();
										if (val == null) {
											log.info("SubField {} of {} had a null value", subFieldData.getCode(), fieldData.getTag());
											subField.encodeNull();
										} else {
											
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

					case KEY_CONTROLFIELD -> {
						decodeRepeatedControlField(root, record);
					}
					
					case KEY_DATAFIELD -> {
						decodeRepeatedDataField(root, record);
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

	protected void decodeRepeatedControlField( final Decoder dec, final Record record ) throws IOException {
		try (Decoder controlField = dec.decodeObject()) {
			
			String tag = null, data = null;
			
			String currentKey;
			while ((currentKey = controlField.decodeKey()) != null) {
				switch (currentKey) {
					case KEY_TAG -> {
						tag = controlField.decodeString();
					}
					case KEY_VALUE_INDICATOR -> {
						data = controlField.decodeString();
					}
					
					default -> {
						// Unknown root property
						controlField.skipValue();
					}
				}
			}
			
			if (tag != null) {
				record.addVariableField(
					factory.newControlField(tag, data));
			}
		}
	}
	
	protected void decodeRepeatedDataField( final Decoder dec, final Record record ) throws IOException {
		try (Decoder dataField = dec.decodeObject()) {
			
			DataField df = factory.newDataField();
			
			String currentKey;
			while ((currentKey = dataField.decodeKey()) != null) {
				switch (currentKey) {
					case KEY_TAG -> {
						df.setTag(dataField.decodeString());
					}
				
					case KEY_INDICATOR_1 -> {
						final String value = dataField.decodeString();
						df.setIndicator1(value.length() >= 1 ? value.charAt(0) : ' ');
					}
					case KEY_INDICATOR_2 -> {
						final String value = dataField.decodeString();
						df.setIndicator2(value.length() >= 1 ? value.charAt(0) : ' ');
					}
					
					case KEY_SUBFIELD -> {
						try ( Decoder subfield = dataField.decodeObject() ) {
							Subfield sf = factory.newSubfield();
							
							while ((currentKey = subfield.decodeKey()) != null) {
								switch (currentKey) {
									case KEY_CODE -> {
										final String value = subfield.decodeString();
										sf.setCode( value.length() >= 1 ? value.charAt(0) : ' ');
									}
								
									case KEY_VALUE_INDICATOR -> {
										sf.setData(subfield.decodeString());
									}
									default -> {
										subfield.skipValue();
									}
								}
							}
							
							df.addSubfield(sf);
						}
					}	
						
					default -> {
						dataField.skipValue();
					}
				}
			}
			record.addVariableField(df);
		}
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

                              String value = subfield.decodeString();

															if ( ( REGEX_SUBFIELD.matcher(currentKey).matches() ) && ( value != null ) ) {
																df.addSubfield(
																	factory.newSubfield(currentKey.charAt(0), value));
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

	@Override
	public Record deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
		
		Decoder decoder = JacksonDecoder.create(p, LimitingStream.limitsFromConfiguration(serdeConfiguration));
		final Argument<Record> type = Argument.of(Record.class);
		return this.deserialize(decoder, registry.newDecoderContext(Record.class), type);
	}
}
