package com.dpbird.odata;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfbizMapOdata {

    public static final String module = OfbizMapOdata.class.getName();

    public static final String NAMESPACE = "com.dpbird";
    public static final String CONTAINER_NAME = "O3Container";

    public static final String ERROR_CODE_ONE = "100";
    public static final String ERROR_CODE_TWO = "200";
    public static final String ERROR_CODE_THREE = "300";
    public static final String ERROR_CODE_FOUR = "400";
    public static final String ERROR_CODE_FIVE = "500";
    public static final String ERROR_CODE_SIX = "600";
    public static final String ERROR_CODE_SEVEN = "700";

    // ofbiz的字段类型，转换成odata的字段类型
    public final static Map<String, EdmPrimitiveTypeKind> FIELDMAP = new HashMap<String, EdmPrimitiveTypeKind>() {
        {
            put("id", EdmPrimitiveTypeKind.String);
            put("id-ne", EdmPrimitiveTypeKind.String);
            put("id-long", EdmPrimitiveTypeKind.String);
            put("id-long-ne", EdmPrimitiveTypeKind.String);
            put("id-vlong", EdmPrimitiveTypeKind.String);
            put("id-vlong-ne", EdmPrimitiveTypeKind.String);
            put("comment", EdmPrimitiveTypeKind.String);
            put("description", EdmPrimitiveTypeKind.String);
            put("currency-precise", EdmPrimitiveTypeKind.Decimal);
            put("currency-amount", EdmPrimitiveTypeKind.Decimal);
            put("fixed-point", EdmPrimitiveTypeKind.Decimal);
            put("date-time", EdmPrimitiveTypeKind.DateTimeOffset);
            put("date", EdmPrimitiveTypeKind.Date);
            put("indicator", EdmPrimitiveTypeKind.String);
            // put("indicatorStr", EdmPrimitiveTypeKind.String);
            put("floating-point", EdmPrimitiveTypeKind.Double);
            put("long-varchar", EdmPrimitiveTypeKind.String);
            put("short-varchar", EdmPrimitiveTypeKind.String);
            put("very-long", EdmPrimitiveTypeKind.String);
            put("very-short", EdmPrimitiveTypeKind.String);
            put("value", EdmPrimitiveTypeKind.String);
            put("name", EdmPrimitiveTypeKind.String);
            put("url", EdmPrimitiveTypeKind.String);
            put("numeric", EdmPrimitiveTypeKind.Int64);
            put("blob", EdmPrimitiveTypeKind.Binary);
            put("byte-array", EdmPrimitiveTypeKind.Stream);
            put("object", EdmPrimitiveTypeKind.Binary);
            put("time", EdmPrimitiveTypeKind.TimeOfDay);
            put("email", EdmPrimitiveTypeKind.String);
            put("credit-card-number", EdmPrimitiveTypeKind.String);
            put("credit-card-date", EdmPrimitiveTypeKind.String);
            put("tel-number", EdmPrimitiveTypeKind.String);
        }
    };

    // java的类转换成odata的字段类型
    public final static Map<String, EdmPrimitiveTypeKind> PARAM_TYPE_MAP = new HashMap<String, EdmPrimitiveTypeKind>() {
        {
            put("String", EdmPrimitiveTypeKind.String);
            put("java.lang.String", EdmPrimitiveTypeKind.String);
            put("BigDecimal", EdmPrimitiveTypeKind.Decimal);
            put("java.math.BigDecimal", EdmPrimitiveTypeKind.Decimal);
            put("java.sql.Timestamp", EdmPrimitiveTypeKind.DateTimeOffset);
            put("Timestamp", EdmPrimitiveTypeKind.DateTimeOffset);
            put("java.sql.Date", EdmPrimitiveTypeKind.DateTimeOffset);
            put("Long", EdmPrimitiveTypeKind.Int64);
            put("java.lang.Long", EdmPrimitiveTypeKind.Int64);
            put("Double", EdmPrimitiveTypeKind.Double);
            put("java.lang.Double", EdmPrimitiveTypeKind.Double);
            put("Boolean", EdmPrimitiveTypeKind.Boolean);
            put("java.lang.Boolean", EdmPrimitiveTypeKind.Boolean);
            put("Integer", EdmPrimitiveTypeKind.Int64);
            put("java.sql.Time", EdmPrimitiveTypeKind.DateTimeOffset);
            put("net.sf.json.JSON", EdmPrimitiveTypeKind.SByte);
            put("net.sf.json.JSONObject", EdmPrimitiveTypeKind.SByte);
        }
    };


    // 这个Map只支持单个实体的补充,线索是必须的,且需要重构。
    public static final Map<String, ServiceFixValidation> SERVICE_FIX_VALIDATION = new HashMap<String, ServiceFixValidation>() {
        {
            put("updateProductPrice", new ServiceFixValidation("updateProductPrice", UtilMisc.toMap("price", "price"), UtilMisc.toMap("productId", "productId"), "ProductPrice"));
            put("createUpdatePartyPostalAddress", new ServiceFixValidation("createUpdatePartyPostalAddress", UtilMisc.toMap("city", "geoName"), UtilMisc.toMap("cityGeoId", "geoId"), "Geo"));
            put("createPartyPostalAddress", new ServiceFixValidation("createPartyPostalAddress", UtilMisc.toMap("city", "geoName"), UtilMisc.toMap("cityGeoId", "geoId"), "Geo"));
            put("updatePerson", new ServiceFixValidation("updatePerson", UtilMisc.toMap("firstName", "firstName", "lastName", "lastName"), UtilMisc.toMap("partyId", "partyId"), "Person"));

        }
    };

    // edmConfig类型转ofbiz类型
    public static final Map<String, String> EDM_TYPE_MAP = new HashMap<String, String>() {
        {
            put("Edm.String", "value");
            put("Edm.Int64", "numeric");
            put("Edm.Boolean", "indicator");
            put("Edm.Double", "floating-point");
            put("Edm.Decimal", "currency-precise");
            put("Edm.Date", "date");
            put("Edm.DateTimeOffset", "date-time");
            put("com.dpbird.Edm.DateTimeOffset", "date-time");
        }
    };

    //收信人
    public static final String ADDRESSEE_ROLE = "Addressee";

    /**
     * Aliyun Ram AccessKey ID
     */
    public static final String ALIYUN_ACCESSKEY_ID = "aliyun.accesskey.id";

    /**
     * Aliyun Ram Access Key Secret
     */
    public static final String ALIYUN_ACCESSKEY_SECRET = "aliyun.accesskey.secret";

    /**
     * AliYun HangZhou Region ID
     */
    public static final String ALIYUN_HUADONG_1 = "cn-hangzhou";

    /**
     * RDS PWD
     */
    public static final String ALIYUN_RDS_PWD = "aliyun.rds.pwd";

    //自动邮件沟通
    public static final String AUTO_MAIL_COMM = "AUTO_EMAIL_COMM";

    /**
     * Database default CharacterSetName (mb4)
     */
    public static final String DB_CHARACTER_SET_NAME = "utf8mb4";

    /**
     * Aliyun Rds db Status
     */
    public static final String DB_STATUS_CREATING = "Creating";

    /**
     * Aliyun Rds db Status
     */
    public static final String DB_STATUS_DELETING = "Deleting";

    /**
     * Aliyun Rds db Status
     */
    public static final String DB_STATUS_RUNNING = "Running";

    /**
     * default db stance id
     */
    public static final String DEFAULT_DB_ID = "rm-bp1b55wr958t504k4";

    /**
     * default dbIp/{@value}
     */
    public static final String DEFAULT_DB_IP = "rm-bp1b55wr958t504k4ao.mysql.rds.aliyuncs.com";

    /**
     * default dbPassword/{@value}
     */
    public static final String DEFAULT_DB_PASSWORD = "root";

    /**
     * default dbUser/{@value}
     */
    public static final String DEFAULT_DB_USER = "root";

    //默认发件人名称
    public static final String DEFAULT_MAIL_FROM = "account@dpbird.com";

    //默认发件模版
    public static final String DEFAULT_MAIL_TEMP_ID = "O3_REGIST_SUCCESS";

    /**
     * system property resource id
     */
    public static final String DEFAULT_RESOURCE_ID = "dpbird";

    /**
     * 仓库是一个实体店铺
     */
    public static final String FACILITY_RETAILSTORE = "RETAIL_STORE";

    /**
     * 仓库类型场所
     */
    public static final String FACILITY_WAREHOUSE = "WAREHOUSE";

    /**
     * gradlew create new tenant/{@value}
     */
    public static final String GRADLEW_CREATE_TENANT = "gradlew createTenant -PtenantId=TENANT_ID -PtenantName=\"YOUR_TENANT_NAME\" -PdomainName=DOMAIN -PtenantReaders=seed,seed-initial -PdbPlatform=M -PdbIp=DB_IP -PdbUser=DB_USER -PdbPassword=DB_PASSWORD";

//	public static final Map<String, ScheduleFacade> SCHEDULE_SERVICE = new HashMap<String, ScheduleFacade>() {
//		{
//			put("updateProductPrice", new ScheduleFacade("updateProductPrice",new String[]{"ProductPrice"}));
//		}
//	};


    public static final Map<String, Map<String, String>> REMOVE_MAP = new HashMap<String, Map<String, String>>() {
        {
            put("ProductFeatureCategory", UtilMisc.toMap());
            put("ProductFeature", UtilMisc.toMap());
            put("ProductFeatureGroup", UtilMisc.toMap());
            put("Party", UtilMisc.toMap());

        }
    };

    public static final Map<String, Map<String, String>> CREATE_SERVICE_MAP = new HashMap<String, Map<String, String>>() {
        {
            put("Facility", UtilMisc.toMap("create", "dpbird.createFacility", "update", "updateFacility", "delete", "deleteFacility"));
            put("ProductStore", UtilMisc.toMap("create", "createProductStore", "update", "updateProductStore"));
            put("ProductStoreRole", UtilMisc.toMap("create", "createProductStoreRole", "update", "updateProductStoreRole", "delete", "removeProductStoreRole"));
            put("ShipmentCostEstimate", UtilMisc.toMap("create", "createShipmentCostEstimate", "update", "updateShipmentCostEstimate", "delete", "deleteShipmentCostEstimate"));
            put("ProductStorePaymentSetting", UtilMisc.toMap("create", "createProductStorePaymentSetting", "update", "updateProductStorePaymentSetting", "delete", "deleteProductStorePaymentSetting"));
            put("ProductStoreShipmentMeth", UtilMisc.toMap("create", "createProductStoreShipMeth", "update", "updateProductStoreShipMeth", "delete", "removeProductStoreShipMeth"));
            put("ContactMech", UtilMisc.toMap("create", "createContactMech", "update", "updateContactMech"));
            // 不应该有直接的createPostalAddress，都是从Party或者其它对象间接创建的
            put("PostalAddress", UtilMisc.toMap("create", "dpbird.createPostalAddress", "update", "dpbird.updatePostalAddress", "delete", "deleteShipppingAddress"));
            put("FacilityContactMech", UtilMisc.toMap("create", "createFacilityContactMech", "update", "updateFacilityContactMech", "delete", "deleteFacilityContactMech"));
            put("FacilityContactMechPurpose", UtilMisc.toMap("create", "createFacilityContactMechPurpose", "delete", "deleteFacilityContactMechPurpose"));
            put("PartyContactMech", UtilMisc.toMap("create", "createPartyContactMech", "update", "updatePartyContactMech", "delete", "deletePartyContactMech"));
            put("PartyContactMechPurpose", UtilMisc.toMap("create", "createPartyContactMechPurpose", "delete", "deletePartyContactMechPurpose"));
            put("PartyNote", UtilMisc.toMap("create", "createPartyNote"));
            // put("TelecomNumber", UtilMisc.toMap("create", "createTelecomNumberObj","update","updateTelecomNumber"));
            put("TelecomNumber", UtilMisc.toMap("create", "createTelecomNumber", "update", "updateTelecomNumber"));
            put("NoteData", UtilMisc.toMap("create", "createNoteData", "update", "updateNote"));
            put("OrderAdjustment", UtilMisc.toMap("create", "createOrderAdjustment", "update", "updateOrderAdjustment", "delete", "deleteOrderAdjustment"));
            put("Product", UtilMisc.toMap("create", "createProduct", "delete", "removeProduct", "update", "updateProduct"));
            put("ProductPrice", UtilMisc.toMap("create", "createProductPrice", "update", "updateProductPrice", "delete", "deleteProductPrice"));
            put("Party", UtilMisc.toMap("create", "createPartyObj", "update", "updateParty"));
            put("Person", UtilMisc.toMap("create", "createPerson", "update", "updatePerson"));
            put("PartyGroup", UtilMisc.toMap("create", "createPartyGroup", "update", "updatePartyGroup"));
            put("ProductStoreFacility", UtilMisc.toMap("create", "dpbird.createProductStoreFacility", "update", "updateProductStoreFacility", "delete", "deleteProductStoreFacility"));
            put("PosTerminal", UtilMisc.toMap("create", "createPosTerminal", "update", "updatePosTerminal", "delete", "removePosTerminal"));
            put("ReturnHeader", UtilMisc.toMap("create", "createReturnHeader", "update", "updateReturnHeader"));
//			put("Product", UtilMisc.toMap("create", "createProduct", "update", "updateProduct"));
            put("DataResource", UtilMisc.toMap("create", "createDataResource", "update", "updateDataResource"));
            put("Content", UtilMisc.toMap("create", "createContent", "update", "updateContent"));
            put("ContentAssoc", UtilMisc.toMap("create", "createContentAssoc", "update", "updateContentAssoc", "delete", "removeContentAssoc"));
            put("ProductContent", UtilMisc.toMap("create", "createProductContent", "update", "updateProductContent", "delete", "removeProductContent"));
            put("OrderHeader", UtilMisc.toMap("create", "createOrderHeader", "update", "updateOrderHeader"));
            put("ProductCategory", UtilMisc.toMap("create", "createProductCategory", "update", "updateProductCategory"));
            put("ProductFeatureCategoryAppl", UtilMisc.toMap("create", "createProductFeatureCategoryAppl", "update", "updateProductFeatureCategoryAppl"));
            put("ProductFeatureCatGrpAppl", UtilMisc.toMap("create", "createProductFeatureCatGrpAppl", "update", "updateProductFeatureCatGrpAppl"));
            put("InventoryItem", UtilMisc.toMap("create", "createInventoryItem", "update", "updateInventoryItem"));
            put("PartyIdentification", UtilMisc.toMap("create", "createPartyIdentification", "update", "updatePartyIdentification"));
            put("PartyRole", UtilMisc.toMap("create", "createPartyRole"));
            put("UserLogin", UtilMisc.toMap("create", "createUserLogin"));
            put("UserPreference", UtilMisc.toMap("create", "dpbird.createUserPreference", "delete", "removeUserPreference"));
            put("Shipment", UtilMisc.toMap("create", "createShipment", "update", "updateShipment"));
            put("ShipmentRouteSegment", UtilMisc.toMap("create", "createShipmentRouteSegment", "update", "updateShipmentRouteSegment"));
            put("Geo", UtilMisc.toMap("create", "createGeo"));
            put("CarrierShipmentMethod", UtilMisc.toMap("create", "createCarrierShipmentMethod", "update", "updateCarrierShipmentMethod"));
            put("WorkEffort", UtilMisc.toMap("create", "createWorkEffort", "update", "updateWorkEffort"));
            put("WorkEffortAttribute", UtilMisc.toMap("create", "createWorkEffortAttribute", "update", "updateWorkEffortAttribute", "delete", "deleteWorkEffortAttribute"));
            put("WorkEffortPartyAssignment", UtilMisc.toMap("create", "assignPartyToWorkEffort", "update", "updatePartyToWorkEffortAssignment", "delete", "deletePartyToWorkEffortAssignment"));
            put("WorkEffortContent", UtilMisc.toMap("create", "createWorkEffortContent", "update", "updateWorkEffortContent", "delete", "deleteWorkEffortContent"));
            put("PartyRelationship", UtilMisc.toMap("create", "createPartyRelationshipAndRole", "update", "updatePartyRelationship", "delete", "deletePartyRelationship"));
            put("GoodIdentification", UtilMisc.toMap("create", "createGoodIdentification", "update", "updateGoodIdentification"));
            put("ElectronicText", UtilMisc.toMap("create", "createElectronicText", "update", "updateElectronicText"));
            put("CommunicationEvent", UtilMisc.toMap("create", "createCommunicationEvent"));
            put("CommEventContentAssoc", UtilMisc.toMap("create", "createCommEventContentAssoc"));
            put("CommunicationEventWorkEff", UtilMisc.toMap("create", "createCommunicationEventWorkEff"));
            put("WebSiteContent", UtilMisc.toMap("create", "createWebSiteContent", "update", "updateWebSiteContent", "delete", "removeWebSiteContent"));
            put("ContentAttribute", UtilMisc.toMap("create", "createContentAttribute", "update", "updateContentAttribute", "delete", "removeContentAttribute"));
            put("ProductAttribute", UtilMisc.toMap("create", "createProductAttribute", "update", "updateProductAttribute", "delete", "deleteProductAttribute"));
            put("ProductFeatureGroup", UtilMisc.toMap("create", "createProductFeatureGroup", "update", "updateProductFeatureGroup", "delete", "removeProductFeatureGroup"));
            put("ProductFeatureCategory", UtilMisc.toMap("create", "createProductFeatureCategory", "update", "updateProductFeatureCategory", "delete", "removeProductFeatureCategory"));
            put("ProductFeature", UtilMisc.toMap("create", "createProductFeature", "update", "updateProductFeature", "delete", "removeProductFeature"));
            put("ProductFeatureGroupAppl", UtilMisc.toMap("create", "createProductFeatureGroupAppl"));
            put("ReturnContactMech", UtilMisc.toMap("create", "createReturnContactMech"));
            put("ProductCategoryContent", UtilMisc.toMap("create", "createCategoryContent", "update", "updateCategoryContent", "delete", "removeCategoryContent"));
            put("TrackingCode", UtilMisc.toMap("create", "createTrackingCode", "update", "updateTrackingCode", "delete", "deleteTrackingCode"));
            put("ProductFeatureAppl", UtilMisc.toMap("create", "applyFeatureToProduct", "update", "updateFeatureToProductApplication", "delete", "removeFeatureFromProduct"));
            put("WmpLive", UtilMisc.toMap("create", "createWmpLive", "update", "updateWmpLive", "delete", "deleteWmpLive"));
            put("MarketingCampaign", UtilMisc.toMap("create", "createMarketingCampaign", "update", "updateMarketingCampaign", "delete", "deleteMarketingCampaign"));
            put("ContactList", UtilMisc.toMap("create", "createContactList", "update", "updateContactList", "delete", "removeContactList"));
            put("MarketingCampaignPrice", UtilMisc.toMap("create", "addPriceRuleToMarketingCampaign", "delete", "removePriceRuleFromMarketingCampaign"));
            put("MarketingCampaignPromo", UtilMisc.toMap("create", "addPromoToMarketingCampaign", "delete", "removePromoFromMarketingCampaign"));
            put("SalesOpportunity", UtilMisc.toMap("create", "createSalesOpportunity", "update", "updateSalesOpportunity"));
            put("MarketingCampaignRole", UtilMisc.toMap("create", "createMarketingCampaignRole", "delete", "deleteMarketingCampaignRole"));
            put("MarketingCampaignNote", UtilMisc.toMap("create", "createMarketingCampaignNote", "delete", "deleteMarketingCampaignNote"));
            put("ProductCategoryMember", UtilMisc.toMap("create", "addProductToCategory", "update", "updateProductToCategory", "delete", "removeProductFromCategory"));
            put("ProductAssoc", UtilMisc.toMap("create", "createProductAssoc", "update", "updateProductAssoc", "delete", "deleteProductAssoc"));
            put("ProductFacility", UtilMisc.toMap("create", "createProductFacility", "update", "updateProductFacility", "delete", "deleteProductFacility"));
            put("ProductPaymentMethodType", UtilMisc.toMap("create", "createProductPaymentMethodType", "update", "updateProductPaymentMethodType", "delete", "deleteProductPaymentMethodType"));
            put("SupplierProduct", UtilMisc.toMap("create", "createSupplierProduct", "update", "updateSupplierProduct", "delete", "removeSupplierProduct"));
            put("VendorProduct", UtilMisc.toMap("create", "createVendorProduct", "delete", "deleteVendorProduct"));
            put("ProductMeter", UtilMisc.toMap("create", "createProductMeter", "update", "updateProductMeter", "delete", "deleteProductMeter"));
            put("ProductKeyword", UtilMisc.toMap("create", "createProductKeyword", "update", "updateProductKeyword", "delete", "deleteProductKeyword"));
            put("ProductRole", UtilMisc.toMap("create", "addPartyToProduct", "update", "updatePartyToProduct", "delete", "removePartyFromProduct"));
            put("ProductCategoryRollup", UtilMisc.toMap("create", "addProductCategoryToCategory", "update", "updateProductCategoryToCategory", "delete", "removeProductCategoryFromCategory"));
            put("ProdCatalogCategory", UtilMisc.toMap("create", "addProductCategoryToProdCatalog", "update", "updateProductCategoryToProdCatalog", "delete", "removeProductCategoryFromProdCatalog"));
            put("ProductCategoryRole", UtilMisc.toMap("create", "addPartyToCategory", "update", "updatePartyToCategory", "delete", "removePartyFromCategory"));
            put("ProductCategoryAttribute", UtilMisc.toMap("create", "createProductCategoryAttribute", "update", "updateProductCategoryAttribute", "delete", "deleteProductCategoryAttribute"));
            put("ProductCategoryLink", UtilMisc.toMap("create", "createProductCategoryLink", "update", "updateProductCategoryLink", "delete", "deleteProductCategoryLink"));
            put("ProductPromo", UtilMisc.toMap("create", "createProductPromo", "update", "updateProductPromo", "delete", "deleteProductPromo"));
            put("ProductStorePromoAppl", UtilMisc.toMap("create", "createProductStorePromoAppl", "update", "updateProductStorePromoAppl", "delete", "deleteProductStorePromoAppl"));
            put("ProductPromoCode", UtilMisc.toMap("create", "createProductPromoCode", "update", "updateProductPromoCode", "delete", "deleteProductPromoCode"));
            put("ProductPromoCodeParty", UtilMisc.toMap("create", "createProductPromoCodeParty", "delete", "deleteProductPromoCodeParty"));
            put("Redeem", UtilMisc.toMap("create", "createRedeem", "update", "updateRedeem", "delete", "deleteRedeem"));
            put("RedeemPoints", UtilMisc.toMap("create", "createRedeemPoints", "update", "updateRedeemPoints", "delete", "deleteRedeemPoints"));
            put("PartyInvitationGroupAssoc", UtilMisc.toMap("create", "createPartyInvitationGroupAssoc", "delete", "deletePartyInvitationGroupAssoc"));
            put("WebSiteRedeem", UtilMisc.toMap("create", "createWebSiteRedeem", "update", "updateWebSiteRedeem", "delete", "deleteWebSiteRedeem"));
            put("RedeemDetail", UtilMisc.toMap("create", "createRedeemDetail", "update", "updateRedeemDetail", "delete", "deleteRedeemDetail"));
            put("PartyInvitation", UtilMisc.toMap("create", "createPartyInvitation"));
            put("ShoppingList", UtilMisc.toMap("create", "createShoppingList", "update", "updateShoppingList", "delete", "removeShoppingList"));
            put("ShoppingListItem", UtilMisc.toMap("create", "createShoppingListItem", "update", "updateShoppingListItem", "delete", "removeShoppingListItem"));
            put("FacilityParty", UtilMisc.toMap("create", "addPartyToFacility", "delete", "removePartyFromFacility"));
            put("PartyAttribute", UtilMisc.toMap("create", "createPartyAttribute", "update", "updatePartyAttribute", "delete", "removePartyAttribute"));
            put("FacilityAttribute", UtilMisc.toMap("create", "createFacilityAttribute", "update", "updateFacilityAttribute", "delete", "deleteFacilityAttribute"));
            put("WorkEffortNote", UtilMisc.toMap("create", "assocWorkEffortNote"));
            put("EnumerationType", UtilMisc.toMap("create", "createEnumerationType", "update", "updateEnumerationType", "delete", "deleteEnumerationType"));
            put("Enumeration", UtilMisc.toMap("create", "createEnumeration", "update", "updateEnumeration", "delete", "deleteEnumeration"));
            put("ContactMechAttribute", UtilMisc.toMap("create", "createContactMechAttribute", "update", "updateContactMechAttribute", "delete", "removeContactMechAttribute"));
            put("FixedAssetMaint", UtilMisc.toMap("create", "createFixedAssetMaint", "update", "updateFixedAssetMaint", "delete", "deleteFixedAssetMaint"));
            put("FinAccount", UtilMisc.toMap("create", "createFinAccount", "delete", "deleteFinAccount", "update", "updateFinAccount"));
            put("BudgetItem", UtilMisc.toMap("create", "createBudgetItem", "delete", "removeBudgetItem", "update", "updateBudgetItem"));
            put("Budget", UtilMisc.toMap("create", "createBudget", "delete", "removeBudget", "update", "updateBudget"));
            put("RequirementBudgetAllocation", UtilMisc.toMap("create", "createRequirementBudgetAllocation"));
            put("Requirement", UtilMisc.toMap("create", "createRequirement"));
            put("WorkRequirementFulfillment", UtilMisc.toMap("create", "createWorkRequirementFulfillment"));
            put("EmploymentApp", UtilMisc.toMap("create", "createEmploymentApp", "update", "updateEmploymentApp"));
        }
    };

    public static final List<String> COMPUTED_FIELDS = new ArrayList<String>() {
        {
            add("lastModifiedByUserLogin");
            add("createdByUserLogin");
            add("createdDate");
            add("lastModifiedDate");
        }
    };

    public static final List<String> MULTILINE_TYPES = new ArrayList<String>() {
        {
            add("very-long");
            add("comment");
            add("description");
            add("value");
            add("long-varchar");
        }
    };
    public static final Map<String, String> MIME_EXTENSIONS = new HashMap<String, String>() {
        {
            put("application/x-tar", ".tar");
            put("image/gif", ".gif");
            put("text/plain", ".txt");
            put("text/html", ".html");
            put("image/png", ".png");
            put("image/tiff", ".tif");
            put("video/mpeg", ".mpeg");
            put("application/pdf", ".pdf");
            put("application/xml", ".xml");
            put("application/zip", ".zip");
            put("image/jpeg", ".jpg");
            put("application/msword", ".doc");
            put("application/vnd.ms-excel", ".xls");
            put("vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
            put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
//            put("application/octet-stream", ".mpeg");

        }
    };

}
