package org.olf.reshare.dcb.bib.record.subclasses;

import java.util.List;

public class Description extends Identifier{
   public Description(String namespace, String value) {
      super(namespace, value);
  }

  public Description(String namespace, List<String> values) {
      super(namespace, values);
  }
}
