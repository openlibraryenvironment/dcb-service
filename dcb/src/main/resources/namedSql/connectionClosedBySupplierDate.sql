select hl.name "Supplier", pra.from_status "Status", pra.audit_date::date "Date", count(*) "Total"
from patron_request_audit pra, patron_request pr, host_lms hl, supplier_request sr, agency a
where (pra.audit_data->>'errorMessage' = 'Connection closed before response was received' or
	   pra.audit_data->>'Error' like '%Connection closed before response was received%' or
       pra.brief_description = 'Connection closed before response was received') and
	  pr.id = pra.patron_request_id and
	  sr.patron_request_id = pr.id and
	  a.id = sr.resolved_agency_id and
	  hl.id = a.host_lms_id
group by 1, 2, 3
order by 3, 1, 2
