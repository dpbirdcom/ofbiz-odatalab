package com.dpbird.test;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.DefaultNavigationHandler;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/4
 */
public class DemoProductToCategoryNavigation extends DefaultNavigationHandler {

    @Override
    public void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                   EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) {
        try {
            //创建产品和分类的关联
            LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
            GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
            Map<String, Object> serviceParam = new HashMap<>(entity.getKeyMap());
            serviceParam.putAll(bindPrimaryKey);
            serviceParam.put("userLogin", userLogin);
            Map<String, Object> serviceResult = dispatcher.runSync("addProductToCategory", serviceParam);
            Debug.log(">>>>>> create ProductReference" + edmNavigationProperty.getName() + serviceResult);
        } catch (GenericServiceException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unbindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                   EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) {
        try {
            //创建产品和分类的关联
            LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
            GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
            Map<String, Object> memberPk = new HashMap<>(entity.getKeyMap());
            memberPk.putAll(bindPrimaryKey);
            List<GenericValue> productCategoryMembers = dispatcher.getDelegator().findByAnd("ProductCategoryMember", memberPk, null, false);
            GenericValue firstMember = EntityUtil.getFirst(productCategoryMembers);
            HashMap<String, Object> serviceMap = new HashMap<>(firstMember.getPrimaryKey());
            serviceMap.put("userLogin", userLogin);
            Map<String, Object> serviceResult = dispatcher.runSync("removeProductFromCategory", serviceMap);
            Debug.log(">>>>>> remove ProductReference" + edmNavigationProperty.getName() + serviceResult);
        } catch (GenericServiceException | GenericEntityException e) {
            e.printStackTrace();
        }

    }

}
