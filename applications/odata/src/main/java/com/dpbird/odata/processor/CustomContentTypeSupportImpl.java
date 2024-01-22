package com.dpbird.odata.processor;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.server.api.serializer.CustomContentTypeSupport;
import org.apache.olingo.server.api.serializer.RepresentationType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author scy
 * @date 2023/11/16
 */
public class CustomContentTypeSupportImpl implements CustomContentTypeSupport {
    private static final List<ContentType> DEFAULT_SUPPORTED_CONTENT_TYPES =
            Collections.unmodifiableList(Arrays.asList(
                    ContentType.JSON,
                    ContentType.JSON_NO_METADATA,
                    ContentType.APPLICATION_JSON,
                    ContentType.JSON_FULL_METADATA,
                    ContentType.APPLICATION_ATOM_XML,
                    ContentType.APPLICATION_XML));

    @Override
    public List<ContentType> modifySupportedContentTypes(List<ContentType> defaultContentTypes, RepresentationType type) {
        switch (type) {
            case METADATA:
                return Collections.unmodifiableList(Arrays.asList(ContentType.APPLICATION_XML,
                        ContentType.APPLICATION_JSON));
            case MEDIA:
            case BINARY:
                return Collections.singletonList(ContentType.APPLICATION_OCTET_STREAM);
            case VALUE:
            case COUNT:
                return Collections.singletonList(ContentType.TEXT_PLAIN);
            case BATCH:
                return Collections.singletonList(ContentType.MULTIPART_MIXED);
            case ACTION_PARAMETERS:
                //与OlingoDefault唯一的区别,Action请求的ContentType支持MULTIPART_FORM_DATA
                ArrayList<ContentType> contentTypes = new ArrayList<>(DEFAULT_SUPPORTED_CONTENT_TYPES);
                contentTypes.add(ContentType.MULTIPART_FORM_DATA);
                return contentTypes;
            default:
                return DEFAULT_SUPPORTED_CONTENT_TYPES;
        }
    }
}
