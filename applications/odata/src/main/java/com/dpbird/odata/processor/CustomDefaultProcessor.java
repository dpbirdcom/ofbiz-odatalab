package com.dpbird.odata.processor;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.etag.ETagHelper;
import org.apache.olingo.server.api.etag.ServiceMetadataETagSupport;
import org.apache.olingo.server.api.processor.ErrorProcessor;
import org.apache.olingo.server.api.processor.MetadataProcessor;
import org.apache.olingo.server.api.processor.ServiceDocumentProcessor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.uri.UriInfo;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CustomDefaultProcessor implements MetadataProcessor, ServiceDocumentProcessor, ErrorProcessor {
    private OData odata;
    private ServiceMetadata serviceMetadata;

    @Override
    public void init(final OData odata, final ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readServiceDocument(final ODataRequest request, final ODataResponse response, final UriInfo uriInfo,
                                    final ContentType requestedContentType) throws ODataApplicationException, ODataLibraryException {
        boolean isNotModified = false;
        ServiceMetadataETagSupport eTagSupport = serviceMetadata.getServiceMetadataETagSupport();
        if (eTagSupport != null && eTagSupport.getServiceDocumentETag() != null) {
            // Set application etag at response
            response.setHeader(HttpHeader.ETAG, eTagSupport.getServiceDocumentETag());
            // Check if service document has been modified
            ETagHelper eTagHelper = odata.createETagHelper();
            isNotModified = eTagHelper.checkReadPreconditions(eTagSupport.getServiceDocumentETag(), request
                    .getHeaders(HttpHeader.IF_MATCH), request.getHeaders(HttpHeader.IF_NONE_MATCH));
        }

        // Send the correct response
        if (isNotModified) {
            response.setStatusCode(HttpStatusCode.NOT_MODIFIED.getStatusCode());
        } else {
            // HTTP HEAD requires no payload but a 200 OK response
            if (HttpMethod.HEAD == request.getMethod()) {
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            } else {
                ODataSerializer serializer = odata.createSerializer(requestedContentType);
                response.setContent(serializer.serviceDocument(serviceMetadata, request.getRawBaseUri()).getContent());
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, requestedContentType.toContentTypeString());
            }
        }
    }

    @Override
    public void readMetadata(final ODataRequest request, final ODataResponse response, final UriInfo uriInfo,
                             final ContentType requestedContentType) throws ODataApplicationException, ODataLibraryException {
        boolean isNotModified = false;
        ServiceMetadataETagSupport eTagSupport = serviceMetadata.getServiceMetadataETagSupport();
        if (eTagSupport != null && eTagSupport.getMetadataETag() != null) {
            // Set application etag at response
            response.setHeader(HttpHeader.ETAG, eTagSupport.getMetadataETag());
            // Check if metadata document has been modified
            ETagHelper eTagHelper = odata.createETagHelper();
            isNotModified = eTagHelper.checkReadPreconditions(eTagSupport.getMetadataETag(), request
                    .getHeaders(HttpHeader.IF_MATCH), request.getHeaders(HttpHeader.IF_NONE_MATCH));
        }

        // Send the correct response
        if (isNotModified) {
            response.setStatusCode(HttpStatusCode.NOT_MODIFIED.getStatusCode());
        } else {
            // HTTP HEAD requires no payload but a 200 OK response
            if (HttpMethod.HEAD == request.getMethod()) {
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            } else {
                ODataSerializer serializer = odata.createSerializer(requestedContentType);
                response.setContent(serializer.metadataDocument(serviceMetadata).getContent());
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, requestedContentType.toContentTypeString());
            }
        }
    }

    @Override
    public void processError(final ODataRequest request, final ODataResponse response,
                             final ODataServerError serverError,
                             final ContentType requestedContentType) {
        try {
            serverError.getException().printStackTrace();
            /******** ODataErrorDetail ************************************
             ODataErrorDetail oDataErrorDetail = new ODataErrorDetail();
             oDataErrorDetail.setCode("detail code 1");
             oDataErrorDetail.setMessage("detail msg 1");
             serverError.setDetails(UtilMisc.toList(oDataErrorDetail));
             *************************************************************/
            /******** innerError ****************
             Map<String, String> innerError = UtilMisc.toMap("@Common.numericSeverity", "4");
             serverError.setInnerError(innerError);
             ************************************/
            /******** additionalProperties ****************/
            Map<String, Object> additionalProperties = UtilMisc.toMap("@Common.numericSeverity", "4");
            serverError.setAdditionalProperties(additionalProperties);
            /************************************/
            String errorCode = serverError.getCode();
            if (errorCode == null) {
                serverError.setCode("500");
            }
            //翻译下异常
            String errMsg = getOlingoErrMsg(request, serverError);
            if (UtilValidate.isNotEmpty(errMsg)) {
                serverError.setMessage(errMsg);
            }
            ODataSerializer serializer = odata.createSerializer(requestedContentType);
            response.setContent(serializer.error(serverError).getContent());
            response.setStatusCode(serverError.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, requestedContentType.toContentTypeString());
        } catch (Exception e) {
            // This should never happen but to be sure we have this catch here to prevent sending a stacktrace to a client.
            String responseContent =
                    "{\"error\":{\"code\":null,\"message\":\"An unexpected exception occurred during error processing\"}}";
            response.setContent(new ByteArrayInputStream(responseContent.getBytes(Charset.forName("utf-8"))));
            response.setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON.toContentTypeString());
        }
    }

    /**
     * 只转换olingo的请求异常信息 服务端异常odata内部处理
     */
    private String getOlingoErrMsg(ODataRequest request, ODataServerError serverError) {
        try {
            String headers = request.getHeader("Accept-Language");
            Locale locale = UtilValidate.isEmpty(headers) ? Locale.getDefault() : Locale.forLanguageTag(request.getHeader("Accept-Language"));
            return serverError.getStatusCode() < 500 ? UtilProperties.getMessage("OdataUiLabels", "ErrMsg.REQUEST_ERROR", locale) : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }
}
