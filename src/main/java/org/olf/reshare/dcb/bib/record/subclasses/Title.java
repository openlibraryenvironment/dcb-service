package org.olf.reshare.dcb.bib.record.subclasses;

import java.util.List;

public class Title extends Identifier {

   public Title(String namespace, String value) {
      super(namespace, value);
  }

  public Title(String namespace, List<String> values) {
      super(namespace, values);
  }
}
