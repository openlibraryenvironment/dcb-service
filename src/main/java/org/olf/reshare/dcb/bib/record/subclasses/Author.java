package org.olf.reshare.dcb.bib.record.subclasses;

import java.util.List;

public class Author extends Identifier{

    public Author(String namespace, String value) {
        super(namespace, value);
    }

    public Author(String namespace, List<String> values) {
        super(namespace, values);
    }
}
