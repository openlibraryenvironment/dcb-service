package org.olf.dcb.request.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.util.Map;
import jakarta.inject.Singleton;
import jakarta.inject.Named;
import org.olf.dcb.tracking.model.StateChange;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import jakarta.transaction.Transactional;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import java.util.UUID;
import org.olf.dcb.core.HostLmsService;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;


@Singleton
@Named("BorrowerRequestLoaned")
public class HandleBorrowerItemLoaned implements WorkflowAction {

        private static final Logger log = LoggerFactory.getLogger(HandleBorrowerItemLoaned.class);
        private RequestWorkflowContextHelper requestWorkflowContextHelper;
        private PatronRequestRepository patronRequestRepository;
        private HostLmsService hostLmsService;
	private PatronRequestAuditService patronRequestAuditService;

        public HandleBorrowerItemLoaned(
                PatronRequestRepository patronRequestRepository,
                HostLmsService hostLmsService,
                RequestWorkflowContextHelper requestWorkflowContextHelper,
		PatronRequestAuditService patronRequestAuditService) {
                this.patronRequestRepository = patronRequestRepository;
                this.hostLmsService = hostLmsService;
                this.requestWorkflowContextHelper = requestWorkflowContextHelper;
                this.patronRequestAuditService = patronRequestAuditService;
        }
        
        @Transactional
        public Mono<Map<String,Object>> execute(Map<String,Object> context) {
                StateChange sc = (StateChange) context.get("StateChange");
                log.debug("HandleBorrowerLoaned {}",sc);
                PatronRequest pr = (PatronRequest) sc.getResource();
                if ( pr != null ) {
                        pr.setStatus(PatronRequest.Status.LOANED);
                        pr.setLocalItemStatus("LOANED");

                        return requestWorkflowContextHelper.fromPatronRequest(pr)
                                .flatMap( this::checkHomeItemOutToVirtualPatron )
                                .flatMap(rwc -> Mono.from(patronRequestRepository.saveOrUpdate(pr)))
                                .doOnNext(spr -> log.debug("Saved {}",spr))
                                .thenReturn(context);
                }
                else {
                        log.warn("Unable to locate patron request to mark as available");
                        return Mono.just(context);
                }
        }


        public Mono<RequestWorkflowContext> checkHomeItemOutToVirtualPatron(RequestWorkflowContext rwc) {

                if ( ( rwc.getSupplierRequest() != null ) && 
                     ( rwc.getSupplierRequest().getLocalItemId() != null ) &&
                     ( rwc.getLenderSystemCode() != null ) &&
                     ( rwc.getPatronVirtualIdentity() != null ) ) {


                        // In some systems a patron can have multiple barcodes. In those systems getLocalBarcode will be encoded as [value, value, value]
                        // So we trim the opening and closing [] and split on the ", " Otherwise just split on ", " just in case
                        final String[] patron_barcodes = extractPatronBarcodes(rwc.getPatronVirtualIdentity().getLocalBarcode());

                        if ( ( patron_barcodes != null ) && ( patron_barcodes.length > 0 ) ) {

                                log.info("Update check home item out : {} to {} at {}",
                                        rwc.getSupplierRequest().getLocalItemBarcode(), patron_barcodes[0],rwc.getLenderSystemCode());

                                return hostLmsService.getClientFor(rwc.getLenderSystemCode())
                                         .flatMap(hostLmsClient -> hostLmsClient.checkOutItemToPatron(
                                                rwc.getSupplierRequest().getLocalItemBarcode(),
                                                patron_barcodes[0]))
                                      .thenReturn(rwc);
                        }
                        else {
                                log.error("NO BARCODE FOR PATRON VIRTUAL IDENTITY. UNABLE TO CHECK OUT {}",rwc.getPatronVirtualIdentity().getLocalBarcode());

				return patronRequestAuditService.addErrorAuditEntry(rwc.getPatronRequest(),
					"NO BARCODE FOR PATRON VIRTUAL IDENTITY. UNABLE TO CHECK OUT") 
					.thenReturn(rwc);
                        }
                }
                else { 
                        log.error("Missing data attempting to set home item off campus {} {} {}",rwc,rwc.getSupplierRequest(), rwc.getPatronVirtualIdentity());
			return patronRequestAuditService.addErrorAuditEntry(rwc.getPatronRequest(),
				String.format("Missing data attempting to set home item off campus {} {} {}",
				rwc,rwc.getSupplierRequest(), rwc.getPatronVirtualIdentity()))
                        	.thenReturn(rwc);
                }       
        }

        private String[] extractPatronBarcodes(String inputstr) {
                String[] result = null;
                if ( inputstr != null ) {
                        if ( inputstr.startsWith("[") ) {
                                result = inputstr.substring(1, inputstr.length() - 1).split(", ");
                        }
                        else {
                                return inputstr.split(", ");
                        }
                }
                return result;
        }

}
