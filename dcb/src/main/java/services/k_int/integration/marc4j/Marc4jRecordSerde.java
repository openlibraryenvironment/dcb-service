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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
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
  // The space is important - some sunfields use " ".
	private static final Pattern REGEX_SUBFIELD = Pattern.compile( "[a-z0-9 ]" );

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
						Optional.ofNullable(root.decodeStringNullable())
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
      throw(e);
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
						tag = controlField.decodeStringNullable();
					}
					case KEY_VALUE_INDICATOR -> {
						data = controlField.decodeStringNullable();
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
						final String value = dataField.decodeStringNullable();
						if ( value != null ) {
							df.setIndicator1(value.length() >= 1 ? value.charAt(0) : ' ');
						}
					}
					case KEY_INDICATOR_2 -> {
						final String value = dataField.decodeStringNullable();
						if ( value != null ) {
							df.setIndicator2(value.length() >= 1 ? value.charAt(0) : ' ');
						}
					}
					
					case KEY_SUBFIELD -> {
						try ( Decoder subfield = dataField.decodeObject() ) {
							Subfield sf = factory.newSubfield();
							
							while ((currentKey = subfield.decodeKey()) != null) {
								switch (currentKey) {
									case KEY_CODE -> {
										final String value = subfield.decodeStringNullable();
										if ( value != null ) {
											sf.setCode( value.length() >= 1 ? value.charAt(0) : ' ');
										}	
									}
								
									case KEY_VALUE_INDICATOR -> {
                    final String value = subfield.decodeStringNullable();
										if ( value != null ) {
										  sf.setData(value);
                    }
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
							String value = field.decodeStringNullable();
							if ( value != null ) {
								record.addVariableField( factory.newControlField(currentKey, value));
							}
						} else if ( REGEX_DATAFIELD.matcher(currentKey).matches() ) {
							// Data field is object.
							final DataField df = factory.newDataField();
              String currentTag = currentKey;
							df.setTag(currentKey);

							try (Decoder fieldData = field.decodeObject()) {

                if ( fieldData == null ) {
                  log.warn("Null field data");
                  fieldData.skipValue();
                }
                else {
  								while ((currentKey = fieldData.decodeKey()) != null) {
  									switch (currentKey) {
  										case KEY_INDICATOR_1 -> {
  											final String value = fieldData.decodeStringNullable();
  											if ( value != null )
  												df.setIndicator1(value.length() >= 1 ? value.charAt(0) : ' ');
  										}
  										case KEY_INDICATOR_2 -> {
  											final String value = fieldData.decodeStringNullable();
  											if ( value != null ) 
  												df.setIndicator2(value.length() >= 1 ? value.charAt(0) : ' ');
  										}
  										case KEY_SUBFIELDS -> {
  											// Decode the subfields.
  											try (Decoder subfields = fieldData.decodeArray()) {
  												while (subfields.hasNextArrayValue()) {
  
                            String last_subfield_info=null;
  													// Each entry is an object
  													try (Decoder subfield = subfields.decodeObject()) {
  
                              if ( subfield == null )
                                log.error("NULL SUBFIELD...");
  
  		  											// Create a subfield per entry
  	  												while ((currentKey = subfield.decodeKey()) != null) {
  
    														String value = subfield.decodeStringNullable();
                                last_subfield_info = value;
    
  						  								// if ( REGEX_SUBFIELD.matcher(currentKey).matches() ) {
  				  											df.addSubfield(factory.newSubfield(currentKey.charAt(0), value));
  			  											// } else {
  		  													// subfield not matched
  	  													// 	subfield.skipValue();
    														// }
   														}
  													}
                            catch ( Exception e ) {
                              log.error("Problem processing subfield ct="+currentTag+" ls="+last_subfield_info+" ck="+currentKey,e);
                              throw e;
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
		Record record = factory.newRecord();
		decodeRecordTree(p.readValueAsTree(), record);
		return record;
	}

	private void decodeRecordTree(JsonNode root, Record record) {
		if (root == null || !root.isObject()) {
			return;
		}

		JsonNode leader = root.get(KEY_LEADER);
		if (leader != null && leader.isTextual() && StringUtils.isNotEmpty(leader.asText())) {
			record.setLeader(factory.newLeader(leader.asText()));
		}

		JsonNode fields = root.get(KEY_FIELDS);
		if (fields != null && fields.isArray()) {
			fields.forEach(field -> decodeFieldTree(field, record));
		}

		decodeRepeatedControlFieldTree(root.get(KEY_CONTROLFIELD), record);
		decodeRepeatedDataFieldTree(root.get(KEY_DATAFIELD), record);
	}

	private void decodeFieldTree(JsonNode field, Record record) {
		if (field == null || !field.isObject()) {
			return;
		}

		field.properties().forEach(entry -> {
			String tag = entry.getKey();
			JsonNode value = entry.getValue();

			if (REGEX_CTRLFIELD.matcher(tag).matches() && value != null && !value.isNull()) {
				record.addVariableField(factory.newControlField(tag, value.asText()));
			} else if (REGEX_DATAFIELD.matcher(tag).matches() && value != null && value.isObject()) {
				record.addVariableField(dataFieldFromTree(tag, value));
			}
		});
	}

	private void decodeRepeatedControlFieldTree(JsonNode controlField, Record record) {
		if (controlField == null || !controlField.isObject()) {
			return;
		}

		JsonNode tag = controlField.get(KEY_TAG);
		JsonNode data = controlField.get(KEY_VALUE_INDICATOR);
		if (tag != null && tag.isTextual()) {
			record.addVariableField(factory.newControlField(tag.asText(), data == null || data.isNull() ? null : data.asText()));
		}
	}

	private void decodeRepeatedDataFieldTree(JsonNode dataField, Record record) {
		if (dataField == null || !dataField.isObject()) {
			return;
		}

		JsonNode tag = dataField.get(KEY_TAG);
		if (tag != null && tag.isTextual()) {
			record.addVariableField(dataFieldFromTree(tag.asText(), dataField));
		}
	}

	private DataField dataFieldFromTree(String tag, JsonNode fieldData) {
		DataField df = factory.newDataField();
		df.setTag(tag);

		JsonNode indicator1 = fieldData.get(KEY_INDICATOR_1);
		if (indicator1 != null && indicator1.isTextual()) {
			df.setIndicator1(firstCharOrSpace(indicator1.asText()));
		}

		JsonNode indicator2 = fieldData.get(KEY_INDICATOR_2);
		if (indicator2 != null && indicator2.isTextual()) {
			df.setIndicator2(firstCharOrSpace(indicator2.asText()));
		}

		JsonNode subfields = fieldData.get(KEY_SUBFIELDS);
		if (subfields != null && subfields.isArray()) {
			subfields.forEach(subfield -> decodeSubfieldTree(subfield, df));
		}

		JsonNode repeatedSubfield = fieldData.get(KEY_SUBFIELD);
		if (repeatedSubfield != null && repeatedSubfield.isObject()) {
			JsonNode code = repeatedSubfield.get(KEY_CODE);
			JsonNode data = repeatedSubfield.get(KEY_VALUE_INDICATOR);
			if (code != null && code.isTextual()) {
				df.addSubfield(factory.newSubfield(firstCharOrSpace(code.asText()), data == null || data.isNull() ? null : data.asText()));
			}
		}

		return df;
	}

	private void decodeSubfieldTree(JsonNode subfield, DataField df) {
		if (subfield == null || !subfield.isObject()) {
			return;
		}

		subfield.properties().forEach(entry -> {
			String code = entry.getKey();
			if (StringUtils.isNotEmpty(code)) {
				JsonNode value = entry.getValue();
				df.addSubfield(factory.newSubfield(code.charAt(0), value == null || value.isNull() ? null : value.asText()));
			}
		});
	}

	private char firstCharOrSpace(String value) {
		return StringUtils.isNotEmpty(value) ? value.charAt(0) : ' ';
	}
}
