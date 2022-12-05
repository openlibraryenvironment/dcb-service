package org.olf.reshare.dcb.bib.marc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;
import org.olf.reshare.dcb.bib.BibRecordService;
import org.olf.reshare.dcb.bib.record.Author;
import org.olf.reshare.dcb.bib.record.AuthorBuilder;
import org.olf.reshare.dcb.bib.record.Description;
import org.olf.reshare.dcb.bib.record.DescriptionBuilder;
import org.olf.reshare.dcb.bib.record.Edition;
import org.olf.reshare.dcb.bib.record.EditionBuilder;
import org.olf.reshare.dcb.bib.record.Identifier;
import org.olf.reshare.dcb.bib.record.IdentifierBuilder;
import org.olf.reshare.dcb.bib.record.ImportedRecord;
import org.olf.reshare.dcb.bib.record.ImportedRecordBuilder;
import org.olf.reshare.dcb.bib.record.PublicationInformation;
import org.olf.reshare.dcb.bib.record.PublicationInformationBuilder;
import org.olf.reshare.dcb.bib.record.Title;
import org.olf.reshare.dcb.bib.record.TitleBuilder;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
   * Get all occurences of a specified field 
   */
   private List<String> getDataFromAllOccurerncesOfField( List<VariableField> allFields, String subfield ){
      List<String> allData = new ArrayList<>();

      Mono<List<VariableField>> mono = Mono.just(allFields);
      Flux<VariableField> flux = mono.flatMapMany(Flux::fromIterable);

      flux.subscribe( field -> {
         DataField dataField = ( DataField ) field;
         if( field != null && dataField.getSubfieldsAsString( subfield ) != null) {
            allData.add( dataField.getSubfieldsAsString( subfield ) );
         }
      });

      if ( allData.isEmpty() ) { return null; }
      return allData;
   }

   /*
   * identifiers
   */
   private List<Identifier> getAllIdentifers( Record record ){

      List<Identifier> identifiers = new ArrayList<>();
      String controlNumberValue = record.getControlNumber();
      ControlField field003 = ( ControlField ) record.getVariableField("003");
      DataField field010 = ( DataField ) record.getVariableField("010");
      List<VariableField> all020 = record.getVariableFields("020");
      DataField field022 = ( DataField )  record.getVariableField("022");
      String value003 = null, value010a = null, value022a = null;

      final IdentifierBuilder controlNumber = IdentifierBuilder.builder()
         .namespace("Control Number")
         .value(controlNumberValue);
      if ( controlNumberValue != null ) { identifiers.add( controlNumber.id(UUID.randomUUID()).build() ); }

      if( field003 != null ) { value003 = field003.getData(); }
      final IdentifierBuilder ControlNumberIdentifier = IdentifierBuilder.builder()
         .namespace("Control Number Identifer")
         .value(value003);
      if ( value003 != null ) {  identifiers.add( ControlNumberIdentifier.id(UUID.randomUUID()).build() ); }
      
      if( field010 != null ) { value010a = field010.getSubfieldsAsString("a"); }
      final IdentifierBuilder ICCN = IdentifierBuilder.builder()
         .namespace("Library of Congress Control Number")
         .value(value010a);
      if ( value010a != null ) { identifiers.add( ICCN.id(UUID.randomUUID()).build() ); }
      
      List<String> values020a = getDataFromAllOccurerncesOfField(all020, "a");
      final IdentifierBuilder ISBN = IdentifierBuilder.builder()
         .namespace("International Standard Book Number")
         .values(values020a);
      if ( values020a != null ) { identifiers.add( ISBN.id(UUID.randomUUID()).build() ); }

      if( field022 != null ) { value022a = Objects.toString( field022.getSubfieldsAsString("a") ); }
      final IdentifierBuilder ISSN = IdentifierBuilder.builder()
         .namespace("International Standard Serial Number")
         .value(value022a);
      if ( value022a != null ) { identifiers.add( ISSN.id(UUID.randomUUID()).build() ); }


      if ( identifiers.isEmpty() ) { return null; }
      return identifiers;
   }

   /*
   * main author
   */
   private Author getMainAuthor( Record record ){
      DataField field100 = (DataField) record.getVariableField("100");
      final AuthorBuilder mainAuthor = AuthorBuilder.builder();
      Boolean valuesAdded = false;

      if(field100 != null) {
         String value100a = field100.getSubfieldsAsString("a");
         if( value100a != null ) { 
            mainAuthor.name(value100a);
            valuesAdded = true;
         }

         String authorIdentifier = field100.getSubfieldsAsString("0");
         if ( authorIdentifier != null ) { 
            mainAuthor.identifier( IdentifierBuilder.builder()
               .id(UUID.randomUUID())
               .namespace("Authority record control number or standard number (R)")
               .value(authorIdentifier)
               .build() );
            valuesAdded = true;
         }
      }

      if ( valuesAdded == false ) { return null; }
      return mainAuthor.id(UUID.randomUUID()).build();
   }


   /*
   * other authors
   */
   private List<Author> getOtherAuthors( Record record ){
      List<Author> otherAuthors = new ArrayList<>();
      DataField field700 = (DataField) record.getVariableField("700");
      DataField field110 = (DataField) record.getVariableField("110");
      
      if(field700 != null) {
         final AuthorBuilder author = AuthorBuilder.builder();
         Boolean valuesAdded = false;

         if( field700.getSubfieldsAsString("a") != null ) {
            String value700a = field700.getSubfieldsAsString("a"); 
            author.name(value700a);
            valuesAdded = true;
         }
         if ( field700.getSubfieldsAsString("0") != null ) {
            author.identifier( IdentifierBuilder.builder()
               .namespace("Authority record control number or standard number (R)")
               .value(field700.getSubfieldsAsString("0"))
               .build() );
            valuesAdded = true;
         }
         if ( valuesAdded == true ) { otherAuthors.add(author.build()); }
      }
      if(field110 != null) {
         final AuthorBuilder author = AuthorBuilder.builder();
         Boolean valuesAdded = false;

         if( field110.getSubfieldsAsString("a") != null ) {
            String value110a = field110.getSubfieldsAsString("a"); 
            author.name(value110a);
            valuesAdded = true;
         }
         if ( field110.getSubfieldsAsString("0") != null ) {
            author.identifier( IdentifierBuilder.builder()
               .namespace("Authority record control number or standard number (R)")
               .value(field110.getSubfieldsAsString("0"))
               .build() );
            valuesAdded = true;
         }
         if ( valuesAdded == true ) { otherAuthors.add( author.id(UUID.randomUUID()).build() ); }
      }
      if ( otherAuthors.isEmpty() ) { return null; }
      return otherAuthors;
   }

   /*
   * descriptions
   */
   private List<Description> getDescriptions( Record record ){
      final List<Description> descriptions = new ArrayList<>();

      Flux<String> tags = Flux.just("300", "520", "650");
      Flux<String> subfields300 = Flux.just("a", "b", "c", "d", "e", "f");
      Flux<String> subfields520 = Flux.just("a", "b", "c", "u", "2", "f");
      Flux<String> subfields650 = Flux.just("a", "b", "c", "d");

      tags.subscribe( tag -> {
         subfields300.subscribe( subfield -> {         
            List<VariableField> subfields = record.getVariableFields(tag);
            List<String> values = getDataFromAllOccurerncesOfField(subfields, subfield);
            final DescriptionBuilder description = DescriptionBuilder.builder().descriptions(values);
            if ( values != null ) { descriptions.add( description.id(UUID.randomUUID()).build() ); }
         });
         subfields520.subscribe( subfield -> {           
            List<VariableField> subfields = record.getVariableFields(tag);
            List<String> values = getDataFromAllOccurerncesOfField(subfields, subfield);
            final DescriptionBuilder description = DescriptionBuilder.builder().descriptions(values);
            if ( values != null ) { descriptions.add( description.id(UUID.randomUUID()).build() ); }
         });
         subfields650.subscribe( subfield -> {          
            List<VariableField> subfields = record.getVariableFields(tag);
            List<String> values = getDataFromAllOccurerncesOfField(subfields, subfield);
            final DescriptionBuilder description = DescriptionBuilder.builder().descriptions(values);
            if ( values != null ) { descriptions.add( description.id(UUID.randomUUID()).build() ); }
         });
      });

      if ( descriptions.isEmpty() ) { return null; }
      return descriptions;
   }

   /*
   * edition
   */
   private Edition getEdition( Record record ){
      DataField field250 = ( DataField ) record.getVariableField("250");
      String values250a = null;
      if ( field250 != null ) { values250a = field250.getSubfieldsAsString("a"); }
      if(values250a == null ) { return null; }
      return EditionBuilder.builder()
               .id(UUID.randomUUID())
               .edition(values250a)
               .build();
   }

   /*
   * publication information
   */
   private List<PublicationInformation> getPublicationInformation( Record record ){
      final List<PublicationInformation> publicationInformation = new ArrayList<>();
      Flux<String> subfields = Flux.just("a", "b", "c", "e", "f");
      List<VariableField> fields = record.getVariableFields("260");
      subfields.subscribe( subfield -> {         
         List<String> values = getDataFromAllOccurerncesOfField(fields, subfield);
         final PublicationInformationBuilder builder = PublicationInformationBuilder.builder().allPublicationInformation(values);
         if ( values != null ) { publicationInformation.add( builder.id(UUID.randomUUID()).build() ); }
      });

      if ( publicationInformation.isEmpty() ) { return null; }
      return publicationInformation;
   }

   /*
   * title
   */
   private Title getTitle( Record record ){
      DataField field245 = (DataField) record.getVariableField("245");
      String values245a = field245.getSubfieldsAsString("a");

      if(values245a == null ) { return null; }
      return TitleBuilder.builder()
               .id(UUID.randomUUID())
               .title(values245a)
               .build();
   }

   /*
   * title information
   */
   private List<Title> getTitleInformation( Record record ){
      List<Title> titleInformation = new ArrayList<>();
      List<VariableField> fields = record.getVariableFields("245");
      Flux<String> subfields = Flux.just("b", "c");

      subfields.subscribe( subfield -> {
         List<String> values = getDataFromAllOccurerncesOfField(fields, subfield);
         final TitleBuilder builder = TitleBuilder.builder().otherTitleInformation(values);
         if ( values != null ) { titleInformation.add( builder.id(UUID.randomUUID()).build() ); }
      });
      if ( titleInformation.isEmpty() ) { return null; }
      return titleInformation;
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
