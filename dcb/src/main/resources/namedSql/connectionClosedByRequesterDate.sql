select hl.name "Requester", pra.audit_date::date "Date", count(*) "Total"
from patron_request_audit pra, patron_request pr, host_lms hl
where (pra.audit_data->>'errorMessage' = 'Connection closed before response was received' or
	   pra.audit_data->>'Error' like '%Connection closed before response was received%' or
       pra.brief_description = 'Connection closed before response was received') and
	  pr.id = pra.patron_request_id and
	  hl.code = pr.patron_hostlms_code
group by 1, 2
order by 2, 1
