select hl.name "Name", sr.date_created::date "Date", count(*) "Total"
from host_lms hl, supplier_request sr, agency a
where a.id = sr.resolved_agency_id and
	  hl.id = a.host_lms_id
group by 1, 2
order by 1, 2
