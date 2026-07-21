package services.k_int.integration.marc4j;

import java.util.regex.Pattern;

import org.marc4j.marc.Record;
import org.marc4j.marc.impl.MarcFactoryImpl;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

@Requires(classes = Record.class)
@Singleton
public class Marc4jRecordJackson3Deserializer extends StdDeserializer<Record> {
	private static final MarcFactoryImpl FACTORY = new MarcFactoryImpl();

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

	private static final Pattern REGEX_CTRLFIELD = Pattern.compile("00[0-9]");
	private static final Pattern REGEX_DATAFIELD = Pattern.compile("((0[1-9])|[1-9][0-9])[0-9]");

	public Marc4jRecordJackson3Deserializer() {
		super(Record.class);
	}

	@Override
	public Record deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
		Record record = FACTORY.newRecord();
		decodeRecordTree(ctxt.readTree(p), record);
		return record;
	}

	private void decodeRecordTree(JsonNode root, Record record) {
		if (root == null || !root.isObject()) {
			return;
		}

		JsonNode leader = root.get(KEY_LEADER);
		if (leader != null && leader.isTextual() && !leader.asText().isEmpty()) {
			record.setLeader(FACTORY.newLeader(leader.asText()));
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
				record.addVariableField(FACTORY.newControlField(tag, value.asText()));
			} else if (REGEX_DATAFIELD.matcher(tag).matches() && value != null && value.isObject()) {
				record.addVariableField(dataFieldFromTree(tag, value));
			}
		});
	}

	private void decodeRepeatedControlFieldTree(JsonNode controlField, Record record) {
		if (controlField != null && controlField.isArray()) {
			controlField.forEach(field -> decodeRepeatedControlFieldTree(field, record));
			return;
		}

		if (controlField == null || !controlField.isObject()) {
			return;
		}

		JsonNode tag = controlField.get(KEY_TAG);
		JsonNode data = controlField.get(KEY_VALUE_INDICATOR);
		if (tag != null && tag.isTextual()) {
			record.addVariableField(FACTORY.newControlField(tag.asText(), data == null || data.isNull() ? null : data.asText()));
		}
	}

	private void decodeRepeatedDataFieldTree(JsonNode dataField, Record record) {
		if (dataField != null && dataField.isArray()) {
			dataField.forEach(field -> decodeRepeatedDataFieldTree(field, record));
			return;
		}

		if (dataField == null || !dataField.isObject()) {
			return;
		}

		JsonNode tag = dataField.get(KEY_TAG);
		if (tag != null && tag.isTextual()) {
			record.addVariableField(dataFieldFromTree(tag.asText(), dataField));
		}
	}

	private org.marc4j.marc.DataField dataFieldFromTree(String tag, JsonNode value) {
		char ind1 = firstCharOrSpace(value.get(KEY_INDICATOR_1));
		char ind2 = firstCharOrSpace(value.get(KEY_INDICATOR_2));
		org.marc4j.marc.DataField dataField = FACTORY.newDataField(tag, ind1, ind2);
		decodeSubfieldTree(value.get(KEY_SUBFIELDS), dataField);
		decodeSubfieldTree(value.get(KEY_SUBFIELD), dataField);
		return dataField;
	}

	private void decodeSubfieldTree(JsonNode subfields, org.marc4j.marc.DataField dataField) {
		if (subfields == null || subfields.isNull()) {
			return;
		}

		if (subfields.isArray()) {
			subfields.forEach(subfield -> decodeSubfieldTree(subfield, dataField));
			return;
		}

		if (!subfields.isObject()) {
			return;
		}

		JsonNode code = subfields.get(KEY_CODE);
		JsonNode data = subfields.get(KEY_VALUE_INDICATOR);
		if (code != null && code.isTextual()) {
			dataField.addSubfield(FACTORY.newSubfield(firstCharOrSpace(code), data == null || data.isNull() ? null : data.asText()));
			return;
		}

		subfields.properties().forEach(entry -> dataField.addSubfield(FACTORY.newSubfield(
			firstCharOrSpace(entry.getKey()), entry.getValue() == null || entry.getValue().isNull() ? null : entry.getValue().asText())));
	}

	private char firstCharOrSpace(JsonNode node) {
		if (node == null || !node.isTextual()) {
			return ' ';
		}

		return firstCharOrSpace(node.asText());
	}

	private char firstCharOrSpace(String value) {
		return value == null || value.isEmpty() ? ' ' : value.charAt(0);
	}
}
