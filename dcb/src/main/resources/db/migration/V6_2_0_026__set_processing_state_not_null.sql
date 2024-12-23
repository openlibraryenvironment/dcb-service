do $$
declare
	source_record_id UUID;

begin
	for source_record_id in select id from source_record where processing_state is null
	loop
		update source_record set processing_state = 'PROCESSING_REQUIRED' where id = source_record_id;
	end loop;
end$$
language plpgsql;
