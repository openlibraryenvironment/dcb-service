package org.olf.dcb.core.interaction.polaris;

import org.marc4j.*;
import org.marc4j.marc.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class MarcConverter {
	private static final Logger log = LoggerFactory.getLogger(MarcConverter.class);
	static Record convertToMarcRecord(String xmlString) {
		try {
			ByteArrayInputStream inputStream = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8));
			MarcReader reader = new MarcXmlReader(inputStream);
			if (reader.hasNext()) {
				org.marc4j.marc.Record marcRecord = reader.next();
//				log.debug("returning marc4j record: {}", marcRecord);
				return marcRecord;
			} else {
//				log.debug("returning null. has next: {} from {}", reader.hasNext(), xmlString);
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
