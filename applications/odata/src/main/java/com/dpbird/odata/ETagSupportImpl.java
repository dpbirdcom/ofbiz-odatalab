package com.dpbird.odata;

import org.apache.olingo.server.api.etag.ServiceMetadataETagSupport;

public class ETagSupportImpl implements ServiceMetadataETagSupport {
    private String metaDataETag = null;

    public ETagSupportImpl(String metaDataETag ) {
        super();
        this.metaDataETag = metaDataETag;
    }

    @Override
    public String getMetadataETag() {
        return metaDataETag;
    }

    @Override
    public String getServiceDocumentETag() {
        return null;
    }
}
