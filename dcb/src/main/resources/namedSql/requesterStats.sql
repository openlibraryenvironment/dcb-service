select hl.name "Name", audit_date::date "Date", pra.to_status "Status", count(*) "Total"
from patron_request_audit pra, patron_request pr, host_lms hl
where pra.from_status <> pra.to_status and
      pr.id = pra.patron_request_id and
	  hl.code = pr.patron_hostlms_code
group by 1, 2, 3
union
select hl.name, date_created::date, 'SUBMITTED_TO_DCB', count(*)
from patron_request pr, host_lms hl
where hl.code = pr.patron_hostlms_code
group by 1, 2, 3
order by 1, 2, 3
