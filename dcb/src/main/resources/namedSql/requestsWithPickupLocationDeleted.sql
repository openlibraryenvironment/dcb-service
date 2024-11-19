select pr.id "requestId", pr.patron_hostlms_code "hostLMS", pr.pickup_location_code "locationId", pr.date_created "dateRequested", pr.status_code "requestStatus",
       cr.title "title"
from patron_request pr, cluster_record cr
where not exists (select id from location where id::text = pr.pickup_location_code) and
      cr.id = pr.bib_cluster_id and
      pr.status_code not in ('FINALISED', 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY')
order by pr.patron_hostlms_code, pr.date_created
