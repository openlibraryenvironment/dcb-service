delete from match_point where bib_id in ( select id from bib_record where process_version = 1 );
delete from bib_identifier where owner_id in ( select id from bib_record where process_version = 1 );
delete from bib_record where process_version = 1;
