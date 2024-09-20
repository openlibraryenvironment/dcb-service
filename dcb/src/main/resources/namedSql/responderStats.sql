select hl.name "Name", audit_date::date "Date", pra.to_status "Status", count(*) "Total"
from patron_request_audit pra, patron_request pr, host_lms hl, supplier_request sr, agency a
where pra.from_status <> pra.to_status and
      pra.to_status not in ('RESOLVED', 'PATRON_VERIFIED') and
      pr.id = pra.patron_request_id and
	  sr.patron_request_id = pr.id and
	  a.id = sr.resolved_agency_id and
	  hl.id = a.host_lms_id
group by 1, 2, 3
order by 1, 2, 3
