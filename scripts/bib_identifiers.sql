select br.id, br.title, br.source_system_id, br.source_record_id, count(*) 
from bib_record br, 
     bib_identifier bi 
where br.id = bi.owner_id 
group by br.id 
having count(*) > 5 
order by count(*) desc limit 50;
