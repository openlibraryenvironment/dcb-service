Select to_char(pr.date_created, 'YYYY-MM-DD HH24:MI') "dateCreated",
       pr.patron_hostlms_code,
	   (select pi.local_barcode from patron_identity pi where pi.patron_id = pr.patron_id limit 1) "bPatronBarcode",
       (select br.title from cluster_record cr, bib_record br where cr.id = pr.bib_cluster_id and br.id = cr.selected_bib) "title",
	   (select sr.local_agency from supplier_request sr where sr.patron_request_id = pr.id limit 1) "supplyingAgency",
	   pr.previous_status_code,
	   pr.status_code,
	   pr.next_expected_status,
	   pr.error_message,
	   pr.out_of_sequence_flag,
	   pr.auto_poll_count_for_current_status,
	   pr.elapsed_time_in_current_status "timeInState",
	   pr.is_manually_selected_item,
	   to_char(pr.date_updated, 'YYYY-MM-DD HH24:MI') "dateUpdated",
	   pr.id,
	   pr.local_request_id "bLocalRequestId",
	   pr.local_request_status "bLocalRequestStatus",
	   pr.raw_local_request_status "bRawLocalRequestStatus",
	   pr.local_bib_id "bLocalBibId",
	   pr.local_item_id "bLocalItemId",
	   pr.local_item_status "bLocalItemStatus",
	   pr.raw_local_item_status "bRawLocalItemStatus",
	   case
		   when rhl.lms_client_class = 'org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient' THEN
			   'Folio'
		   when rhl.lms_client_class = 'org.olf.dcb.core.interaction.polaris.PolarisLmsClient' THEN
			   'Polaris'
		   ELSE
			   'Sierra'
	   END "bLMS",
	   shl.code "supplier_hostlms_code",
	   sr.local_id "sLocalRequestId",
	   sr.local_status "sLocalRequestStatus",
	   sr.raw_local_status "sRawLocalRequestStatus",
	   sr.local_bib_id "sLocalBibId",
	   sr.local_item_barcode "sLocalItemBarcode",
	   sr.local_item_status "sLocalItemStatus",
	   sr.raw_local_item_status "sRawLocalItemStatus",
	   (select pi.local_id from patron_identity pi where pi.id = sr.virtual_identity_id limit 1) "sPatronLocalId",
	   (select pi.local_barcode from patron_identity pi where pi.id = sr.virtual_identity_id limit 1) "sPatronBarcode",
	   case
		   when shl.lms_client_class = 'org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient' THEN
			   'Folio'
		   when shl.lms_client_class = 'org.olf.dcb.core.interaction.polaris.PolarisLmsClient' THEN
			   'Polaris'
		   ELSE
			   'Sierra'
	   END "sLMS",
	   concat('https://libraries-dcb-hub-admin-scaffold-uat-git-production-knowint.vercel.app/patronRequests/', pr.id) "requestLink"
/*
select pr.status_code, count(*) "total"
*/
from patron_request pr, host_lms rhl, host_lms shl, supplier_request sr, agency a
where rhl.code = pr.patron_hostlms_code and
	  sr.patron_request_id = pr.id and
	  a.id = sr.resolved_agency_id and
	  shl.id = a.host_lms_id and
      pr.poll_count_for_current_status > 0 and
	  (
	   (pr.status_code in ('SUBMITTED_TO_DCB', 'PATRON_VERIFIED', 'RESOLVED') and 
		EXTRACT(EPOCH FROM (now() - pr.current_status_timestamp)) > 86400) OR
	   (pr.status_code in ('REQUEST_PLACED_AT_SUPPLYING_AGENCY', 'CONFIRMED') and
		EXTRACT(EPOCH FROM (now() - pr.current_status_timestamp)) > 21600) OR
	   (pr.status_code in ('REQUEST_PLACED_AT_BORROWING_AGENCY', 'PICKUP_TRANSIT', 'RECEIVED_AT_PICKUP', 'READY_FOR_PICKUP', 'RETURN_TRANSIT') and
		EXTRACT(EPOCH FROM (now() - pr.current_status_timestamp)) > 604800) OR
	   (pr.status_code = 'LOANED' and
		EXTRACT(EPOCH FROM (now() - pr.current_status_timestamp)) > 3456000)
	  )
/*
group by 1
order by 1
*/
order by pr.date_created asc
