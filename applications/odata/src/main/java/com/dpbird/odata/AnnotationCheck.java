package com.dpbird.odata;


import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntitySet;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlAnnotation;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.uri.UriParameter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 这里主要实现对Annotation的验证
 *
 * @author scy
 * @date 2022/9/14
 */
public class AnnotationCheck {
    public static final String MODULE = AnnotationCheck.class.getName();

    /**
     * 检查当前的请求是否需要If-Match或If-None-Match
     */
    public static void checkIfMatch(OfbizAppEdmProvider edmProvider, ODataRequest request,
                                    OdataOfbizEntity entity, EdmEntitySet edmEntitySet) throws ODataApplicationException {
        // 1.先验证当前请求是否缺少必须的If-Match
        requiredPrecondition(request, edmProvider, edmEntitySet);

        // 2.如果带有If-Match 进行匹配验证
        if (request.getHeader("If-Match") != null || request.getHeader("If-None-Match") != null) {
            matchEtag(entity.getGenericValue(), request);
        }

    }

    /**
     * 检查当前的请求是否需要If-Match或If-None-Match
     */
    public static void checkIfMatch(Delegator delegator, OfbizAppEdmProvider edmProvider, ODataRequest request,
                                    EdmEntitySet edmEntitySet, List<UriParameter> keyPredicates) throws ODataApplicationException {
        try {
            // 1.先验证当前请求是否缺少必须的If-Match
            requiredPrecondition(request, edmProvider, edmEntitySet);

            // 2.如果带有If-Match 进行匹配验证
                if (request.getHeader("If-Match") != null || request.getHeader("If-None-Match") != null) {
                Map<String, Object> primaryKey = Util.uriParametersToMap(keyPredicates, edmEntitySet.getEntityType(), edmProvider);
                OfbizCsdlEntityType entityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntitySet.getEntityType().getFullQualifiedName());
                GenericValue genericValue = EntityQuery.use(delegator).from(entityType.getOfbizEntity()).where(primaryKey)
                        .select("lastUpdatedStamp").cache(true).queryOne();
                matchEtag(genericValue, request);
            }
        } catch (GenericEntityException | OfbizODataException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查request是否是否缺少必须的If-Match
     */
    private static void requiredPrecondition(ODataRequest request, OfbizAppEdmProvider edmProvider, EdmEntitySet edmEntitySet) throws ODataApplicationException {
        try {
            if (request.getHeader("If-Match") == null && request.getHeader("If-None-Match") == null) {
                OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, edmEntitySet.getName());
                List<String> terms = csdlEntitySet.getAnnotations().stream().map(CsdlAnnotation::getTerm).collect(Collectors.toList());
                //如果配置了这个term并且更新数据时请求不带有If-Match 那么响应异常_428
                if (terms.contains("Core.OptimisticConcurrency")) {
                    throw new ODataApplicationException(HttpStatusCode.PRECONDITION_REQUIRED.getInfo(), HttpStatusCode.PRECONDITION_REQUIRED.getStatusCode(), null);
                }
            }
        } catch (OfbizODataException e) {
            e.printStackTrace();
        }
    }

    /**
     * 匹配If-Match/If-None-Match
     */
    private static void matchEtag(GenericValue genericValue, ODataRequest request) throws ODataApplicationException {
        boolean checkResult;
        String currEtag = Util.getGenericValueETag(genericValue);
        if (UtilValidate.isEmpty(currEtag)) {
            return;
        }
        if (request.getHeader("If-Match") != null) {
            //无差异为正确匹配
            String ifMatch = request.getHeader("If-Match");
            checkResult = ifMatch.equals(currEtag);
        } else {
            //有差异为正确匹配
            String ifMatch = request.getHeader("If-None-Match");
            checkResult = !ifMatch.equals(currEtag);
        }
        if (!checkResult) {
            throw new ODataApplicationException(HttpStatusCode.PRECONDITION_FAILED.getInfo(),
                    HttpStatusCode.PRECONDITION_FAILED.getStatusCode(), null);
        }
    }

}
