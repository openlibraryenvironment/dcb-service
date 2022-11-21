package org.olf.reshare.dcb.bib.record.subclasses;

import java.util.List;

public class PublicationInformation extends Identifier {
   public PublicationInformation(String namespace, String value) {
      super(namespace, value);
  }

  public PublicationInformation(String namespace, List<String> values) {
      super(namespace, values);
  }
}
