package org.olf.reshare.dcb.bib.marc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.BibRecordService;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

/*
The MarcImportService is a concrete class that "does the work". 
*/
@Singleton
public class MarcImportService{

   int recordCount = 0;

   private final BibRecordService bibRecordService;

   public MarcImportService(BibRecordService bibRecordService) {
      this.bibRecordService = bibRecordService;
   }

   // Simply returns the full marc file
   private MarcReader getMarcFile(String fileName) throws Exception {
      InputStream  input  = new FileInputStream(new File(fileName));
      MarcReader marcFile = new MarcStreamReader(input);
      return marcFile;
   }

   // TODO: transform to ArrayList more efficiently
   public List<Record> read(String fileName) throws Exception {
      InputStream in = new FileInputStream(fileName);
      MarcReader reader = new MarcStreamReader(in);
    
      List<Record> records = new ArrayList<>();
      while (reader.hasNext()) {
        Record marc4jRecord = reader.next();
        records.add(marc4jRecord);
      }
      return records;
    }

   // Add each ImportedRecord without flux
   public void doImport(String marcFileName) throws Exception {
      MarcReader reader = getMarcFile(marcFileName);
      
      while (reader.hasNext()) {
         Record record = reader.next();
         
         // Get desired fields from marc record
         ControlField controlNumber = (ControlField) record.getVariableField("001");
         ControlField controlNumberIdentifier = (ControlField) record.getVariableField("003");
         DataField title = (DataField) record.getVariableField("245");
         DataField itemType = (DataField) record.getVariableField("075");

         // parse into ImportedRecord type
         ImportedRecord importedRecord = new ImportedRecord(UUID.randomUUID(), controlNumber, controlNumberIdentifier, title, itemType);
         bibRecordService.addBibRecord(importedRecord);

         // How many records?
         recordCount++;
      }
      System.out.print("Processed " + recordCount + " records.");
      recordCount=0;
    }
    
   // Attempt at using flux
   public void recordsFlux() throws Exception {
      Flux<Record> fluxOfListOfRecord = Flux.fromIterable(read("../test-data/SGCLsample1.mrc"));
      Flux<Record> fluxOfImportedRecords = fluxOfListOfRecord
            .mapNotNull(record -> {
               ImportedRecord importedRecord = new ImportedRecord(UUID.randomUUID(), 
                  (ControlField) ((Record) record).getVariableField("001"),
                  (ControlField) ((Record) record).getVariableField("003"),
                  (DataField) ((Record) record).getVariableField("245"),
                  (DataField) ((Record) record).getVariableField("075"));
               bibRecordService.addBibRecord(importedRecord);
               return null;
            });
      fluxOfImportedRecords.subscribe();
   }
}









