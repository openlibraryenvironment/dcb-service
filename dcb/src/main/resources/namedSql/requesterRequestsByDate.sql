select pr.date_created::date "Date", count(*) "Total"
from patron_request pr
group by 1
order by 1
