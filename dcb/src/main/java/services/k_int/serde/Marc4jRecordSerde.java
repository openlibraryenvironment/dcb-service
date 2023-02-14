package services.k_int.serde;

import java.io.IOException;
import java.util.Optional;

import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.impl.MarcFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.inject.Singleton;

@Singleton
public class Marc4jRecordSerde implements Serde<Record> {

	// Instanciate the default class here. Works better with native compilation.
	private final MarcFactory factory = new MarcFactoryImpl();

	private static Logger log = LoggerFactory.getLogger(Marc4jRecordSerde.class);

	private static final String KEY_LEADER = "leader";
	private static final String KEY_INDICATOR_1 = "ind1";
	private static final String KEY_INDICATOR_2 = "ind2";

	private static final String KEY_FIELDS = "fields";
	private static final String KEY_SUBFIELDS = "subfields";
//  private static final String KEY_CONTROLFIELD = "controlfield";
//  private static final String KEY_DATAFIELD = "datafield";
//  private static final String KEY_SUBFIELD = "subfield";

	private static final String REGEX_CTRLFIELD = "00[0-9]";
	private static final String REGEX_DATAFIELD = "((0[1-9])|[1-9][0-9])[0-9]";
	private static final String REGEX_SUBFIELD = "[a-z0-9]";

	@Override
	public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Record> type, Record value)
		throws IOException {

		// NOOP.
		encoder.encodeNull();
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
						if (currentKey.matches(REGEX_CTRLFIELD)) {
							record.addVariableField(
								factory.newControlField(currentKey, field.decodeString()));

						} else if (currentKey.matches(REGEX_DATAFIELD)) {
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
															if (currentKey.matches(REGEX_SUBFIELD)) {
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
