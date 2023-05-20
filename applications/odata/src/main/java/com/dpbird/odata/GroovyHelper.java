package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntitySet;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.GroovyUtil;
import org.apache.ofbiz.base.util.ScriptUtil;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unchecked")
public class GroovyHelper {
    public static final String module = GroovyHelper.class.getName();
    private Delegator delegator;
    private LocalDispatcher dispatcher;
    private GenericValue userLogin;
    private Locale locale;
    private HttpServletRequest httpServletRequest;
    Map<String, Object> gContext = new HashMap<>();

    public GroovyHelper(Delegator delegator, LocalDispatcher dispatcher, GenericValue userLogin, Locale locale, HttpServletRequest httpServletRequest) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.userLogin = userLogin;
        this.locale = locale;
        this.httpServletRequest = httpServletRequest;
        Map<String, Object> params = new HashMap<>();
        gContext.put(ScriptUtil.PARAMETERS_KEY, params);
        gContext.put("dispatcher", dispatcher);
        gContext.put("delegator", delegator);
        gContext.put("locale", locale);
        gContext.put("userLogin", userLogin);
        gContext.put("httpServletRequest", httpServletRequest);
        gContext.put("uiLabelMap", Util.getUiLabelMap(this.locale));
    }

    public Object runScript(String location, String methodName) throws OfbizODataException {
        try {
            return GroovyUtil.runScriptAtLocation(location, methodName, gContext);
        } catch (GeneralException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }


    public GenericValue createNestedGenericValue(String handler, Entity nestedEntityToCreate,
                                                 OdataOfbizEntity entityCreated,
                                                 LocalDispatcher dispatcher, GenericValue userLogin) throws OfbizODataException, GenericEntityException {
        Map<String, Object> params = (Map<String, Object>) gContext.get(ScriptUtil.PARAMETERS_KEY);
        params.put("entity", entityCreated);
        params.put("nestedEntity", nestedEntityToCreate);
        return (GenericValue) runScript(handler, "createNestedData");
    }

    public void deleteNavigationData(String handler, GenericValue genericValue, GenericValue nestedGenericValue) throws OfbizODataException {
        Map<String, Object> params = (Map<String, Object>) gContext.get(ScriptUtil.PARAMETERS_KEY);
        params.put("genericValue", genericValue);
        params.put("toDeleteGenericValue", nestedGenericValue);
        runScript(handler, "deleteNavigationData");
    }

    // 创建语义话字段
    public List<Entity> generateEntityListFields(String handler, HttpServletRequest httpServletRequest,
                                                 List<Entity> entityList, SelectOption selectOption) throws OfbizODataException {
        Map<String, Object> params = (Map<String, Object>) gContext.get(ScriptUtil.PARAMETERS_KEY);
        params.put("entityList", entityList);
        params.put("selectOption", selectOption);
        return (List<Entity>) runScript(handler, "generateFields");
    }

    public GenericValue createGenericValue(String handler, Entity entityToWrite) throws OfbizODataException, GenericServiceException {
        Map<String, Object> params = (Map<String, Object>) gContext.get(ScriptUtil.PARAMETERS_KEY);
        params.put("entity", entityToWrite);
        return (GenericValue) runScript(handler, "createEntity");
    }

    public GenericValue updateGenericValue(String handler, Entity entityToWrite) throws OfbizODataException {
        Map<String, Object> params = (Map<String, Object>) gContext.get(ScriptUtil.PARAMETERS_KEY);
        params.put("entity", entityToWrite);
        return (GenericValue) runScript(handler, "updateEntity");
    }

    public void deleteGenericValue(String handler, GenericValue genericValue) throws OfbizODataException, GenericServiceException {
        Map<String, Object> params = (Map<String, Object>) gContext.get(ScriptUtil.PARAMETERS_KEY);
        params.put("genericValue", genericValue);
        runScript(handler, "deleteEntity");
    }

    // 以下为调用EntitSet的handler
    public List<GenericValue> findGenericValues(String handler, OfbizAppEdmProvider edmProvider,
                                                OfbizCsdlEntitySet csdlEntitySet,
                                                Map<String, QueryOption> queryOptions,
                                                EntityCondition entityCondition) throws OfbizODataException {
        Map<String, Object> params = (Map<String, Object>) gContext.get(ScriptUtil.PARAMETERS_KEY);
        params.put("queryOptions", queryOptions);
        params.put("edmProvider", edmProvider);
        params.put("csdlEntitySet", csdlEntitySet);
        params.put("entityCondition", entityCondition);
        // groovy相应的方法，如果没有数据，需要返回空List，不能返回null
        return (List<GenericValue>) runScript(handler, "findGenericValues");
    }

}
