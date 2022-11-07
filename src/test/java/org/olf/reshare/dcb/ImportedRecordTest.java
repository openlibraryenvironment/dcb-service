package org.olf.reshare.dcb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ImportedRecord;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;


@MicronautTest
public class ImportedRecordTest {

   @Test
   void testImportedRecord() throws FileNotFoundException {

      String marcFileName = "../SGCLsample1.mrc";
      InputStream input = new FileInputStream(new File(marcFileName));
      MarcReader marcFile = new MarcStreamReader(input);

      // record 1
      Record record = marcFile.next();
      ControlField controlNumber = (ControlField) record.getVariableField("001");
      ControlField controlNumberIdentifier = (ControlField) record.getVariableField("003");
      DataField title = (DataField) record.getVariableField("245");
      DataField itemType = (DataField) record.getVariableField("075");
      ImportedRecord result = new ImportedRecord(UUID.randomUUID(), controlNumber, controlNumberIdentifier, title, itemType);

      assertNotNull(result);
      assertEquals(ImportedRecord.class, result.getClass());

      // record 2
      Record record2 = marcFile.next();
      ControlField controlNumber2 = (ControlField) record2.getVariableField("001");
      ControlField controlNumberIdentifier2 = (ControlField) record2.getVariableField("003");
      DataField title2 = (DataField) record2.getVariableField("245");
      DataField itemType2 = (DataField) record2.getVariableField("075");
      ImportedRecord result2 = new ImportedRecord(UUID.randomUUID(), controlNumber2, controlNumberIdentifier2, title2, itemType2);

      assertNotNull(result2);
      assertEquals(ImportedRecord.class, result2.getClass());

      assertNotEquals(result.identifier(), result2.identifier());
      assertNotEquals(result.controlNumber().getData(), result2.controlNumber().getData());
   }
   
}
