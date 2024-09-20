select hl.name "Name", pr.date_created::date "Date", count(*) "Total"
from patron_request pr, host_lms hl
where hl.code = pr.patron_hostlms_code
group by 1, 2
order by 1, 2
