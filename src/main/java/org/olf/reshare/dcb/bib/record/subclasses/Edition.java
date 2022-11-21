package org.olf.reshare.dcb.bib.record.subclasses;

import java.util.List;

public class Edition extends Identifier{
   public Edition(String namespace, String value) {
      super(namespace, value);
  }

  public Edition(String namespace, List<String> values) {
      super(namespace, values);
  }
}
