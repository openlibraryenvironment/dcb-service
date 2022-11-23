package org.olf.reshare.dcb.bib.marc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;
import org.olf.reshare.dcb.bib.BibRecordService;
import org.olf.reshare.dcb.bib.record.Author;
import org.olf.reshare.dcb.bib.record.Description;
import org.olf.reshare.dcb.bib.record.Edition;
import org.olf.reshare.dcb.bib.record.Identifier;
import org.olf.reshare.dcb.bib.record.ImportedRecord;
import org.olf.reshare.dcb.bib.record.ImportedRecordBuilder;
import static org.olf.reshare.dcb.bib.record.ImportedRecordBuilder.ImportedRecord;
import org.olf.reshare.dcb.bib.record.PublicationInformation;
import org.olf.reshare.dcb.bib.record.Title;
import org.olf.reshare.dcb.bib.record.*;

import com.oracle.truffle.api.instrumentation.Tag;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

/*
The MarcImportService is a concrete class that "does the work". 
*/
@Singleton
public class MarcImportService{

   private final BibRecordService bibRecordService;

   public MarcImportService(BibRecordService bibRecordService) {
      this.bibRecordService = bibRecordService;
   }


   /*
    * Simply returns the full marc file as type MarcReader
    */
   private MarcReader getMarcFile(String fileName) throws Exception {
      InputStream  input  = new FileInputStream(new File(fileName));
      MarcReader marcFile = new MarcStreamReader(input);
      return marcFile;
   }


   /*
    * We want Marc reader to be an iterator
    */
   private Iterable<Record> convertToIterable(String fileName) throws Exception {
      final MarcReader reader = getMarcFile(fileName);
      final Iterable<Record> iterableMarcStream = new Iterable<>() {
            @Override
            public Iterator<Record> iterator() {   
               return new Iterator<Record>() {

               // Delegate to the reader. We made it final so we can reference from nested scopes.
               @Override
               public boolean hasNext() {
                  return reader.hasNext();
               }

               @Override
               public Record next() {
                  return reader.next();
               }
            };
         }
      };
      return iterableMarcStream;
   }


   /*
   * Get all occurences of a particular field 
   * 0(n)
   */
   private List<String> getDataFromAllOccurerncesOfField( List<VariableField> allFields, String subfield ){
      List<String> allData = new ArrayList<>();

      for ( int i = 0; i < allFields.size(); i++ ) {
         DataField field = ( DataField ) allFields.get( i );
         if( field != null && field.getSubfieldsAsString( subfield ) != null) {
            allData.add( field.getSubfieldsAsString( subfield ) );
         }
      }

      if ( allData.isEmpty() ) { return null; }
      return allData;
   }


   /*
   * The identifiers we want
   */
   private List<Identifier> getAllIdentifers( Record record ){
      List<Identifier> identifiers = new ArrayList<>();

      String controlNumberValue = Objects.toString( record.getControlNumber(), null );
      Identifier controlNumber = new Identifier("Control Number", controlNumberValue);
      if ( controlNumberValue != null ) { identifiers.add( controlNumber ); }

      ControlField field003 = ( ControlField ) record.getVariableField("003");
      String value003 = null;
      if( field003 != null ) { value003 = field003.getData(); }
      Identifier ControlNumberIdentifier = new Identifier("Control Number Identifer", value003);
      if ( value003 != null ) { identifiers.add( ControlNumberIdentifier ); }

      DataField field010 = ( DataField ) record.getVariableField("010");
      String value010a = null;
      if( field010 != null ) { value010a = Objects.toString( field010.getSubfieldsAsString("a"), null ); }
      Identifier ICCN = new Identifier("Library of Congress Control Number", value010a);
      if ( value010a != null ) { identifiers.add( ICCN ); }

      List<VariableField> all020 = record.getVariableFields("020");
      List<String> values020a = getDataFromAllOccurerncesOfField(all020, "a");
      Identifier ISBN = new Identifier("International Standard Book Number", values020a);
      if ( values020a != null ) { identifiers.add( ISBN ); }

      DataField field022 = ( DataField )  record.getVariableField("022");
      String value022a = null;
      if( field022 != null ) { value022a = Objects.toString( field022.getSubfieldsAsString("a") ); }
      Identifier ISSN = new Identifier("International Standard Serial Number", value022a);
      if ( value022a != null ) { identifiers.add( ISSN ); }

      if ( identifiers.isEmpty() ) { return null; }
      return identifiers;
   }


   private Author getMainAuthor( Record record ){
      DataField field100 = (DataField) record.getVariableField("100");
      Author mainAuthor = (Author) new Author();
      String value1000a;

      if(field100 != null) {
         if( field100.getSubfieldsAsString("a") != null ) { 
            value1000a = Objects.toString( field100.getSubfieldsAsString("a") ); 
            mainAuthor.setName(value1000a);
         }

         String authorIdentifier = Objects.toString( field100.getSubfieldsAsString("0"), null );
         if ( authorIdentifier != null ) { mainAuthor.setIdentifier( new Identifier( "Authority record control number or standard number (R)", authorIdentifier ) ); }
      }
 
      return mainAuthor;
   }


   private List<Author> getOtherAuthors( Record record ){
      List<Author> otherAuthors = new ArrayList<>();

      DataField field700 = (DataField) record.getVariableField("700");  
      String value700a;
      if(field700 != null) {
         if( field700.getSubfieldsAsString("a") != null ) {
            Author author = (Author) new Author();
            value700a = Objects.toString( field700.getSubfieldsAsString("a") ); 
            author.setName(value700a);
            String authorIdentifier = Objects.toString( field700.getSubfieldsAsString("0"), null );
            if ( authorIdentifier != null ) { 
               author.setIdentifier( new Identifier( "Authority record control number or standard number (R)", authorIdentifier ) ); 
            }
            otherAuthors.add(author);
         }
      }

      DataField field110 = (DataField) record.getVariableField("110");
      String value110a;
      if(field110 != null) {
         if( field110.getSubfieldsAsString("a") != null ) {
            Author author = (Author) new Author();
            value110a = Objects.toString( field110.getSubfieldsAsString("a") ); 
            author.setName(value110a);
            String authorIdentifier = Objects.toString( field110.getSubfieldsAsString("0"), null );
            if ( authorIdentifier != null ) { 
               author.setIdentifier( new Identifier( "Authority record control number or standard number (R)", authorIdentifier ) ); 
            }
            otherAuthors.add(author);
         }
      }
      return otherAuthors;
   }


   private List<Title> getTitleInformation( Record record ){
      List<Title> titleInformation = new ArrayList<>();

      List<VariableField> all245b = record.getVariableFields("245");
      List<String> values245b = getDataFromAllOccurerncesOfField(all245b,"b");
      Title field245b = new Title( values245b );
      if ( values245b != null ) { titleInformation.add( field245b ); }

      List<VariableField> all245c = record.getVariableFields("245");
      List<String> values245c = getDataFromAllOccurerncesOfField(all245c, "c");
      Title field245c = new Title( values245c );
      if ( values245c != null ) { titleInformation.add( field245c ); }
      
      return titleInformation;
   }


   private List<PublicationInformation> getPublicationInformation( Record record ){
      List<PublicationInformation> publicationInformation = new ArrayList<>();

      List<VariableField> all260a = record.getVariableFields("260");
      List<String> values260a = getDataFromAllOccurerncesOfField(all260a, "a");
      PublicationInformation field260a = new PublicationInformation( values260a );
      if ( values260a != null ) { publicationInformation.add( field260a ); }

      List<VariableField> all260b = record.getVariableFields("260");
      List<String> values260b = getDataFromAllOccurerncesOfField(all260b, "b");
      PublicationInformation field260b = new PublicationInformation(values260b );
      if ( values260b != null ) { publicationInformation.add( field260b ); }

      List<VariableField> all260c = record.getVariableFields("260");
      List<String> values260c = getDataFromAllOccurerncesOfField(all260c, "c");
      PublicationInformation field260c = new PublicationInformation(values260c );
      if ( values260c != null ) { publicationInformation.add( field260c ); }

      List<VariableField> all260e = record.getVariableFields("260");
      List<String> values260e = getDataFromAllOccurerncesOfField(all260e, "e");
      PublicationInformation field260e = new PublicationInformation(values260e );
      if ( values260e != null ) { publicationInformation.add( field260e ); }

      List<VariableField> all260f = record.getVariableFields("260");
      List<String> values260f = getDataFromAllOccurerncesOfField(all260f, "f");
      PublicationInformation field260f = new PublicationInformation(values260f );
      if ( values260f != null ) { publicationInformation.add( field260f ); }
      
      return publicationInformation;
   }


   private List<Description> getDescriptions( Record record ){
      List<Description> descriptions = new ArrayList<>();

      List<VariableField> all300a = record.getVariableFields("300");
      List<String> values300a = getDataFromAllOccurerncesOfField(all300a, "a");
      Description field300a = new Description(values300a );
      if ( values300a != null ) { descriptions.add( field300a ); }

      List<VariableField> all300b = record.getVariableFields("300");
      List<String> values300b = getDataFromAllOccurerncesOfField(all300b, "b");
      Description field300b = new Description(values300b );
      if ( values300b != null ) { descriptions.add( field300b ); }

      List<VariableField> all300c = record.getVariableFields("300");
      List<String> values300c = getDataFromAllOccurerncesOfField(all300c, "c");
      Description field300c = new Description(values300c );
      if ( values300c != null ) { descriptions.add( field300c ); }

      List<VariableField> all300e = record.getVariableFields("300");
      List<String> values300e = getDataFromAllOccurerncesOfField(all300e, "e");
      Description field300e = new Description(values300e );
      if ( values300e != null ) { descriptions.add( field300e ); }

      List<VariableField> all300f = record.getVariableFields("300");
      List<String> values300f = getDataFromAllOccurerncesOfField(all300f, "f");
      Description field300f = new Description(values300f );
      if ( values300f != null ) { descriptions.add( field300f ); }

      List<VariableField> all520a = record.getVariableFields("520");
      List<String> values520a = getDataFromAllOccurerncesOfField(all520a, "a");
      Description field520a = new Description(values520a );
      if ( values520a != null ) { descriptions.add( field520a ); }

      List<VariableField> all520b = record.getVariableFields("520");
      List<String> values520b = getDataFromAllOccurerncesOfField(all520b, "b");
      Description field520b = new Description(values520b );
      if ( values520b != null ) { descriptions.add( field520b ); }

      List<VariableField> all520c = record.getVariableFields("520");
      List<String> values520c = getDataFromAllOccurerncesOfField(all520c, "c");
      Description field520c = new Description(values520c );
      if ( values520c != null ) { descriptions.add( field520c ); }

      List<VariableField> all520u = record.getVariableFields("520");
      List<String> values520u = getDataFromAllOccurerncesOfField(all520u, "u");
      Description field520u = new Description(values520u );
      if ( values520u != null ) { descriptions.add( field520u ); }

      List<VariableField> all5202 = record.getVariableFields("520");
      List<String> values5202 = getDataFromAllOccurerncesOfField(all5202, "2");
      Description field5202 = new Description(values5202 );
      if ( values5202 != null ) { descriptions.add( field5202 ); }

      List<VariableField> all650a = record.getVariableFields("650");
      List<String> values650a = getDataFromAllOccurerncesOfField(all650a, "a");
      Description field650a = new Description(values650a );
      if ( values650a != null ) { descriptions.add( field650a ); }

      List<VariableField> all650b = record.getVariableFields("650");
      List<String> values650b = getDataFromAllOccurerncesOfField(all650b, "b");
      Description field650b = new Description(values650b );
      if ( values650b != null ) { descriptions.add( field650b ); }

      List<VariableField> all650c = record.getVariableFields("650");
      List<String> values650c = getDataFromAllOccurerncesOfField(all650c, "c");
      Description field650c = new Description(values650c );
      if ( values650c != null ) { descriptions.add( field650c ); }

      List<VariableField> all650d = record.getVariableFields("650");
      List<String> values650d = getDataFromAllOccurerncesOfField(all650d, "d");
      Description field650d = new Description(values650d );
      if ( values650d != null ) { descriptions.add( field650d ); }
      
      return descriptions;
   }


   private Title getTitle( Record record ){
      DataField field245 = (DataField) record.getVariableField("245");
      String values245a = field245.getSubfieldsAsString("a");
      return new Title( values245a );
   }


   private Edition getEdition( Record record ){
      DataField field250 = ( DataField ) record.getVariableField("250");
      String values250a = null;
      if ( field250 != null && field250.getSubfieldsAsString("a") != null ) { values250a = field250.getSubfieldsAsString("a"); }
      return new Edition( values250a );
   }

   
   // Using flux to transform records into "ImportedRecord" type
   public void fluxOfRecords(String fileName) throws Exception {
      Flux<Record> fluxOfListOfRecord = Flux.fromIterable(convertToIterable(fileName));
      fluxOfListOfRecord.subscribe(record -> {

         ImportedRecord importedRecord = ImportedRecordBuilder.builder()
            .id( UUID.randomUUID() )
            .identifiers( getAllIdentifers( record ) )
            .mainAuthor( getMainAuthor( record ) )
            .otherAuthors( getOtherAuthors( record ) )
            .title( getTitle( record ) )
            .titleInformation( getTitleInformation( record ) )
            .edition( getEdition( record ) )
            .publicationInformation( getPublicationInformation( record ) )
            .descriptions( getDescriptions( record ) )
            .build();

         bibRecordService.addBibRecord( importedRecord );
      });
   }
}
