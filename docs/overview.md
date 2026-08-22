# Overview

DCB coordinates direct consortial borrowing across member library systems. It ingests catalogue
metadata, resolves live availability, and drives NCIP request lifecycles.

`/items/availability-v2` adds electronic-item metadata; legacy `/items/availability` retains its existing response shape.

DCB Profile NCIP2.02+ membership is invitation-controlled. DCB pulls an ORS tenant's authoritative
public directory and creates its internal HostLMS, Agency, Library, and Location bindings atomically.
Public contracts never expose the internal HostLMS adapter class. Its invitation policy controls the
default and allowed values written to the existing Agency authentication profile.

Generic OAI-PMH catalogue ingest resumes from the highest source datestamp observed. FOLIO retains its
existing internal-clock resumption behaviour because its second-resolution timestamps require separate
handling.

Selected MARC metadata is retained as structured canonical metadata in the shared index. Public fields
include contributors, subjects, publication, notes, series, physical/content/media/carrier description,
classifications, relationships and alternate scripts. Cluster members include source-system and holding counts.
