package com.dpbird.odata.processor;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.batch.BatchFacade;
import org.apache.olingo.server.api.deserializer.batch.BatchOptions;
import org.apache.olingo.server.api.deserializer.batch.BatchRequestPart;
import org.apache.olingo.server.api.deserializer.batch.ODataResponsePart;
import org.apache.olingo.server.api.processor.BatchProcessor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class OfbizBatchProcessor implements BatchProcessor {

    public static final String module = OfbizBatchProcessor.class.getName();
    private OData odata;

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;

    }

    @Override
    public void processBatch(BatchFacade facade, ODataRequest request, ODataResponse response)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------ in processBatch, waiting for process!", module);
        try {
            // 1. Extract the boundary
            final String boundary = facade.extractBoundaryFromContentType(request.getHeader(HttpHeader.CONTENT_TYPE));
            // 2. Prepare the batch options
            final BatchOptions options = BatchOptions.with().rawBaseUri(request.getRawBaseUri())
                    .rawServiceResolutionUri(request.getRawServiceResolutionUri()).build();

            // 3. Deserialize the batch request
            final List<BatchRequestPart> requestParts = odata.createFixedFormatDeserializer()
                    .parseBatchRequest(request.getBody(), boundary, options);
            // 4. Execute the batch request parts
            final List<ODataResponsePart> responseParts = new ArrayList<ODataResponsePart>();
            String sapContextId = null;
            for (final BatchRequestPart part : requestParts) {
                ODataResponsePart responseParty = facade.handleBatchRequest(part);
                responseParts.add(responseParty);
                // try to retrieve SAP-ContextId
                if (sapContextId == null) {
                    List<ODataResponse> oDataResponses = responseParty.getResponses();
                    for (ODataResponse oDataResponse : oDataResponses) {
                        sapContextId = oDataResponse.getHeader("SAP-ContextId");
                        if (sapContextId != null) {
                            break;
                        }
                    }
                } // end try to retrieve SAP-ContextId
                boolean isFail = responseParty.getResponses().stream().anyMatch(res -> res.getStatusCode() >= 400);
                //If it fails, do not continue
                if (isFail) {
                    break;
                }
            }
            // 5. Create a new boundary for the response
            final String responseBoundary = "batch_" + UUID.randomUUID().toString();

            // 6. Serialize the response content
            final InputStream responseContent = odata.createFixedFormatSerializer().batchResponse(responseParts,
                    responseBoundary);

            // 7. Setup response
            response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.MULTIPART_MIXED + ";boundary=" + responseBoundary);
            response.setContent(responseContent);
            response.setStatusCode(HttpStatusCode.ACCEPTED.getStatusCode());
            if (sapContextId != null) {
                response.addHeader("SAP-ContextId", sapContextId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ROOT);
        }
    }

    @Override
    public ODataResponsePart processChangeSet(BatchFacade facade, List<ODataRequest> requests)
            throws ODataApplicationException, ODataLibraryException {
        /*
         * OData Version 4.0 Part 1: Protocol Plus Errata 02 11.7.4 Responding to a
         * Batch Request
         *
         * All operations in a change set represent a single change unit so a service
         * MUST successfully process and apply all the requests in the change set or
         * else apply none of them. It is up to the service implementation to define
         * rollback semantics to undo any requests within a change set that may have
         * been applied before another request in that same change set failed and
         * thereby apply this all-or-nothing requirement. The service MAY execute the
         * requests within a change set in any order and MAY return the responses to the
         * individual requests in any order. The service MUST include the Content-ID
         * header in each response with the same value that the client specified in the
         * corresponding request, so clients can correlate requests and responses.
         *
         * To keep things simple, we dispatch the requests within the Change Set to the
         * other processor interfaces.
         */
        Debug.logInfo("------------------------------ in processChangeSet", module);
        final List<ODataResponse> responses = new ArrayList<ODataResponse>();

        boolean beganTransaction = false;
        try {
            // make sure this is in a transaction
            beganTransaction = TransactionUtil.begin();

            for (final ODataRequest request : requests) {
                // Actual request dispatching to the other processor interfaces.
                final ODataResponse response = facade.handleODataRequest(request);

                // Determine if an error occurred while executing the request.
                // Exceptions thrown by the processors get caught and result in a proper OData
                // response.
                final int statusCode = response.getStatusCode();
                if (statusCode < 400) {
                    Debug.logInfo("----------------- the request is good, go to next", module);
                    // The request has been executed successfully. Return the response as a part of
                    // the change set
                    responses.add(response);
                } else {
                    // Something went wrong. Undo all previous requests in this Change Set
                    Debug.logInfo("----------------- got error in a request, rollback, statuscode = " + statusCode, module);
                    try {
                        TransactionUtil.rollback();
                    } catch (GenericTransactionException e1) {
                        Debug.logError(e1, module);
                    }

                    /*
                     * In addition the response must be provided as follows:
                     *
                     * OData Version 4.0 Part 1: Protocol Plus Errata 02 11.7.4 Responding to a
                     * Batch Request
                     *
                     * When a request within a change set fails, the change set response is not
                     * represented using the multipart/mixed media type. Instead, a single response,
                     * using the application/http media type and a Content-Transfer-Encoding header
                     * with a value of binary, is returned that applies to all requests in the
                     * change set and MUST be formatted according to the Error Handling defined for
                     * the particular response format.
                     *
                     * This can be simply done by passing the response of the failed ODataRequest to
                     * a new instance of ODataResponsePart and setting the second parameter
                     * "isChangeSet" to false.
                     */
                    // responses.add(response);
                    return new ODataResponsePart(response, false);
                }
            }
            // Everything went well, so commit the changes.
            Debug.logInfo("----------------- everythig going well, commit", module);

        } catch (Exception e) {
            Debug.logInfo("----------------- got ODataApplicationException", module);
            Debug.logError(e, module);
            try {
                TransactionUtil.rollback();
            } catch (GenericTransactionException e1) {
                Debug.logError(e1, module);
            }
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        } finally {
            try {
                TransactionUtil.commit(beganTransaction);
            } catch (GenericTransactionException e) {
                Debug.logError(e, module);
                throw new ODataApplicationException(e.getMessage(),
                        HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }
        }
        return new ODataResponsePart(responses, true);
    }

}
