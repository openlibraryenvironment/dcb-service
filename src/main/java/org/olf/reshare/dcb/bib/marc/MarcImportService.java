package org.olf.reshare.dcb.bib.marc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.UUID;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.BibRecordService;

import jakarta.inject.Singleton;

/*
The MarcImportService is a concrete class that "does the work". 
*/
@Singleton
public class MarcImportService {

   private final BibRecordService bibService;

   public MarcImportService(BibRecordService bibService) {
      this.bibService = bibService;
   }

   // Simply returns the full marc file
   private MarcReader getMarcFile(String fileName) throws Exception {
      InputStream  input  = new FileInputStream(new File(fileName));
      MarcReader marcFile = new MarcStreamReader(input);
      return marcFile;
   }

   // Add each ImportedRecord
   public void doImport(String marcFileName) throws Exception {
      MarcReader reader = getMarcFile(marcFileName);
      
      while (reader.hasNext()) {
         Record record = reader.next();
         
         // Get desired fields from marc record
         ControlField controlNumber = (ControlField) record.getVariableField("001");
         ControlField controlNumberIdentifier = (ControlField) record.getVariableField("003");
         DataField title = (DataField) record.getVariableField("245");
         DataField itemType = (DataField) record.getVariableField("075");

         ImportedRecord importedRecord = new ImportedRecord(UUID.randomUUID(), controlNumber, controlNumberIdentifier, title, itemType);

         // fetch data and parse into ImportedRecord
         bibService.addBibRecord(importedRecord);
      }
    }
}









