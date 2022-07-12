import com.dpbird.odata.Util
import com.dpbird.odata.edm.OdataOfbizEntity
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.Delegator
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.product.product.ProductWorker
import org.apache.ofbiz.product.store.ProductStoreWorker
import org.apache.ofbiz.service.ServiceUtil
import org.apache.olingo.commons.api.data.Entity
import org.apache.olingo.commons.api.data.ComplexValue
import org.apache.olingo.commons.api.data.Property
import org.apache.olingo.commons.api.data.ValueType
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.olingo.server.api.uri.queryoption.SelectOption

module = "ecx.Product.generateFields.groovy";
SelectOption selectOption = parameters.get("selectOption");
//todo 后续请对店铺进行逻辑处理，而不是硬编码
productStoreId = "5001";
webSiteId = "ECX-02"
productStore = ProductStoreWorker.getProductStore(productStoreId, delegator);

String[] fieldsToSelect;
if (selectOption != null) {
    fieldsToSelect = selectOption.getText().split(",");
} else {
    fieldsToSelect = ["defaultPrice","listPrice","specialPromoPrice","promoPrice","yourPrice",
                      "isReleased","isSupportDiscontinued","isSalesDiscontinued","inventoryIsAvailable",
                      "virtualProductId","selectableFeatures","standardFeatures"];
}
List<Entity> entityList = parameters.get("entityList");
entityList.each { entity ->
    OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
    product = odataOfbizEntity.getGenericValue();
    priceCalculated = false;
    //now
    nowTimestamp = UtilDateTime.nowTimestamp()
    for (String selectFieldName : fieldsToSelect) {
        Property property;
        if (selectFieldName == "defaultPrice"
                || selectFieldName == "listPrice"
                || selectFieldName == "specialPromoPrice"
                || selectFieldName == "promoPrice"
                || selectFieldName == "yourPrice") {
            if (!priceCalculated) {
                priceContext = [product: product, currencyUomId: productStore.getString("defaultCurrencyUomId"), autoUserLogin: userLogin];
                priceContext.webSiteId = webSiteId
                priceContext.productStoreId = productStoreId
                priceContext.checkIncludeVat = "Y";
                priceResult = dispatcher.runSync('calculateProductPrice', priceContext);
                yourPrice = priceResult.price;
                defaultPrice = priceResult.defaultPrice;
                listPrice = priceResult.listPrice;
                promoPrice = priceResult.promoPrice;
                specialPromoPrice = priceResult.specialPromoPrice;
                Property defaultPrice = new Property(null, "defaultPrice", ValueType.PRIMITIVE, defaultPrice);
                entity.addProperty(defaultPrice);
                Property listPrice = new Property(null, "listPrice", ValueType.PRIMITIVE, listPrice);
                entity.addProperty(listPrice);
                Property specialPromoPrice = new Property(null, "specialPromoPrice", ValueType.PRIMITIVE, specialPromoPrice);
                entity.addProperty(specialPromoPrice);
                Property promoPrice = new Property(null, "promoPrice", ValueType.PRIMITIVE, promoPrice);
                entity.addProperty(promoPrice);
                Property yourPrice = new Property(null, "yourPrice", ValueType.PRIMITIVE, yourPrice);
                entity.addProperty(yourPrice);
            }
            continue;
        } else if (selectFieldName == "isReleased") {
            isReleased = "Y";
            releaseDate = product.getTimestamp("releaseDate");
            if (releaseDate != null && nowTimestamp.before(releaseDate)) {
                isReleased = "N";
            }
            property = new Property(null, "isReleased", ValueType.PRIMITIVE, isReleased);
        } else if (selectFieldName == "isSupportDiscontinued") {
            isSupportDiscontinued = "Y";
            supportDiscontinuationDate = product.getTimestamp("supportDiscontinuationDate");
            if (supportDiscontinuationDate != null && nowTimestamp.before(supportDiscontinuationDate)) {
                isSupportDiscontinued = "N";
            }
            property = new Property(null, "isSupportDiscontinued", ValueType.PRIMITIVE, isSupportDiscontinued);
        } else if (selectFieldName == "isSalesDiscontinued") {
            isSalesDiscontinued = "Y";
            salesDiscontinuationDate = product.getTimestamp("salesDiscontinuationDate");
            if (salesDiscontinuationDate != null && nowTimestamp.before(salesDiscontinuationDate)) {
                isSalesDiscontinued = "N";
            }
            property = new Property(null, "isSalesDiscontinued", ValueType.PRIMITIVE, isSalesDiscontinued);
        } else if (selectFieldName == "inventoryIsAvailable") {
            inventoryIsAvailable = "Y";
            isStoreInventoryRequired = "Y";
            Map<String, Object> invReqResult = dispatcher.runSync("isStoreInventoryRequired",
                    UtilMisc.toMap("productStoreId", productStoreId, "productId", product.productId, "product", product));
            if (ServiceUtil.isError(invReqResult)) {
                Debug.logError("Error calling isStoreInventoryRequired service, result is: " + invReqResult, module);
                isStoreInventoryRequired = "N";
                inventoryIsAvailable = "N";
            }
            isStoreInventoryRequired = "Y" == invReqResult.get("requireInventory")
            if (isStoreInventoryRequired) {
                Map<String, Object> invAvailResult = dispatcher.runSync("isStoreInventoryAvailable",
                        UtilMisc.toMap("productStoreId", productStoreId, "productId", product.productId,
                                "product", product, "quantity", BigDecimal.ONE));
                if (ServiceUtil.isError(invAvailResult)) {
                    Debug.logError("Error calling isStoreInventoryAvailable service, result is: " + invAvailResult, module);
                    inventoryIsAvailable = "N";
                }
                inventoryIsAvailable = invAvailResult.get("available");
            }
            property = new Property(null, "inventoryIsAvailable", ValueType.PRIMITIVE, inventoryIsAvailable);
        } else if (selectFieldName == "virtualProductId") {
            virtualProductId = ProductWorker.getVariantVirtualId(product);
            Property virtualProductId = new Property(null, "virtualProductId", ValueType.PRIMITIVE, virtualProductId);
            entity.addProperty(virtualProductId);
        } else if (selectFieldName == "selectableFeatures") {
            if (product.isVirtual != "Y") {
                continue;
            }
            productFeatureAndAppls = product.getRelated("ProductFeatureAndAppl", null,
                     null, false);
            Map<String, List<ComplexValue>> typedFeatureMap = new HashMap<>();
            for (GenericValue productFeatureAndAppl:productFeatureAndAppls) {
                if (productFeatureAndAppl.productFeatureApplTypeId != "SELECTABLE_FEATURE") {
                    continue;
                }
                ComplexValue selectableFeature = new ComplexValue();
                List<Property> properties = selectableFeature.getValue();
                String productFeatureTypeId = productFeatureAndAppl.productFeatureTypeId;
                properties.add(new Property(null, "productFeatureTypeId", ValueType.PRIMITIVE,
                        productFeatureTypeId));
                properties.add(new Property(null, "productFeatureId", ValueType.PRIMITIVE,
                        productFeatureAndAppl.productFeatureId));
                properties.add(new Property(null, "idCode", ValueType.PRIMITIVE,
                        productFeatureAndAppl.idCode));
                properties.add(new Property(null, "productFeatureCategoryId", ValueType.PRIMITIVE,
                        productFeatureAndAppl.productFeatureCategoryId));
                properties.add(new Property(null, "description", ValueType.PRIMITIVE,
                        productFeatureAndAppl.description));
                List<ComplexValue> typedFeatureList = typedFeatureMap.get(productFeatureTypeId);
                if (typedFeatureList == null) {
                    typedFeatureList = new ArrayList<ComplexValue>();
                    typedFeatureMap.put(productFeatureTypeId, typedFeatureList);
                }
                typedFeatureList.add(selectableFeature);
            }
            Set<String> featureTypes = typedFeatureMap.keySet();
            List<ComplexValue> selectableFeatures = new ArrayList<>();
            for (String featureTypeId:featureTypes) {
                ComplexValue typedFeatures = new ComplexValue();
                List<Property> properties = typedFeatures.getValue();
                properties.add(new Property(null, "productFeatureTypeId", ValueType.PRIMITIVE,
                        featureTypeId));
                properties.add(new Property(null, "productFeatures", ValueType.COLLECTION_COMPLEX,
                        typedFeatureMap.get(featureTypeId)));
                Object uiLabelValue = uiLabelMap.get("ProductFeatureType.description" + "." + featureTypeId);
                properties.add(new Property(null, "typeDescription", ValueType.PRIMITIVE,
                        uiLabelValue));
                selectableFeatures.add(typedFeatures);
            }
            if (selectableFeatures.size() > 0) {
                property = new Property(null, "selectableFeatures", ValueType.COLLECTION_COMPLEX,
                        selectableFeatures);
            }
        }
        if (property != null) {
            entity.addProperty(property);
        }
    }
}
context.put("entityList", entityList);
