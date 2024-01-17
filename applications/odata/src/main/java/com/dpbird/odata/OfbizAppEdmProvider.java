/*
 * 这个EdmProvider，将会根据app的不同，仅提供app需要的entity和function等
 */
package com.dpbird.odata;

import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlSchema;
import org.apache.ofbiz.base.location.FlexibleLocation;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.FileUtil;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.*;
import org.apache.olingo.commons.api.ex.ODataException;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class OfbizAppEdmProvider extends CsdlAbstractEdmProvider {

    public static final String module = OfbizAppEdmProvider.class.getName();
    private Map<String, EdmWebConfig> edmReferenceConfigMap = null;
    private final String webapp;
    // private InputStream edmConfigInputStream;
    public static final FullQualifiedName CONTAINER = new FullQualifiedName(OfbizMapOdata.NAMESPACE, OfbizMapOdata.CONTAINER_NAME);

    private static final Map<String, String> edmReferencePath = new HashMap<String, String>() {
        {
            put("Core", "/vocabularies/Core.xml");
            put("Capabilities", "/vocabularies/Capabilities.xml");
            put("Common", "/vocabularies/Common.xml");
            put("UI", "/vocabularies/UI.xml");
            put("Aggregation", "/vocabularies/Aggregation.xml");
            put("Session", "/vocabularies/Session.xml");
        }
    };
    private final Delegator delegator;
    private static LocalDispatcher dispatcher;
    private final GenericValue userLogin;
    private final Locale locale;
    private static String eTag = null;
    private String componentName;
    CsdlSchema cachedSchema;
    Map<String, CsdlSchema> referenceSchemaMap = new HashMap<>();

    public OfbizAppEdmProvider(Delegator delegator, LocalDispatcher dispatcher, String appName,
                               boolean reload, GenericValue userLogin, Locale locale, String componentName, String componentPath) throws OfbizODataException {
        super();
        Debug.logInfo("=============================== constructor OfbizAppEdmProvider", module);
        this.delegator = delegator;
        OfbizAppEdmProvider.dispatcher = dispatcher;
        this.webapp = appName;
        this.userLogin = userLogin;
        this.locale = locale;
        this.componentName = componentName;
        CsdlSchemaCache csdlSchemaCache = new CsdlSchemaCache(this.delegator.getDelegatorName());
        cachedSchema = csdlSchemaCache.get(locale.getLanguage() + this.webapp);
        Iterator<Map.Entry<String, String>> it = edmReferencePath.entrySet().iterator();
        while (it.hasNext()) { // 获取所有的reference schema从cache里
            Map.Entry<String, String> entry = it.next();
            CsdlSchema csdlSchema = csdlSchemaCache.get(entry.getKey());
            this.referenceSchemaMap.put(entry.getKey(), csdlSchema);
            if (csdlSchema == null) {
                reload = true;
            }
        }
        if (cachedSchema == null || reload) {
            this.reloadAppSchema(csdlSchemaCache, Locale.ENGLISH);
            this.reloadAppSchema(csdlSchemaCache, Locale.SIMPLIFIED_CHINESE);
            this.cachedSchema = csdlSchemaCache.get(locale.getLanguage() + this.webapp);
            eTag = String.valueOf(UtilDateTime.nowTimestamp().getTime());
        }
    }

    public String getWebapp() {
        return webapp;
    }

    public String getComponentName() {
        return componentName;
    }

    private void reloadAppSchema(CsdlSchemaCache csdlSchemaCache, Locale locale) throws OfbizODataException {
        String prefix = "component://" ;
        String filePath = prefix+ componentName + "/config/" + this.webapp + "EdmConfig.xml";
        InputStream edmConfigInputStream = getEdmFileInputStream(filePath);
        EdmWebConfig edmWebConfig;
        try {
            edmWebConfig = EdmConfigLoader.loadAppEdmConfig(delegator, dispatcher, this.webapp, edmConfigInputStream, locale);
            OfbizCsdlSchema csdlSchema = this.createSchema(OfbizMapOdata.NAMESPACE, edmWebConfig, null);
            EdmConfigLoader.generateAnnotations(delegator, csdlSchema, locale);
            csdlSchemaCache.put(locale.getLanguage() + this.webapp, csdlSchema);
            if (UtilValidate.isEmpty(this.edmReferenceConfigMap)) {
                this.edmReferenceConfigMap = new HashMap<>();
            }
            Iterator<Map.Entry<String, String>> referenceIt = edmReferencePath.entrySet().iterator();
            while (referenceIt.hasNext()) {
                Map.Entry<String, String> entry = referenceIt.next();
                CsdlSchema referenceSchema = referenceSchemaMap.get(entry.getKey());
                if (referenceSchema == null) {
                    InputStream inputStream = getSchemaInputStream(prefix + "odata/config"+entry.getValue());
                    EdmWebConfig edmReferenceConfig = EdmConfigLoader.loadEdmReference(delegator, dispatcher, inputStream, locale);
                    this.edmReferenceConfigMap.put(entry.getKey(), edmReferenceConfig);
                    referenceSchema = this.createSchema(entry.getKey(), edmReferenceConfig, null);
                    csdlSchemaCache.put(entry.getKey(), referenceSchema);
                    referenceSchemaMap.put(entry.getKey(), referenceSchema);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    @Override
    public CsdlComplexType getComplexType(FullQualifiedName complexTypeName) throws ODataException {
        if (cachedSchema != null) {
            CsdlComplexType csdlComplexType = cachedSchema.getComplexType(complexTypeName.getName());
            if (csdlComplexType != null) {
                return csdlComplexType;
            }
        }
        CsdlSchema referenceSchema = this.referenceSchemaMap.get(complexTypeName.getNamespace());
        if (referenceSchema != null) {
            CsdlComplexType csdlComplexType = referenceSchema.getComplexType(complexTypeName.getName());
            if (csdlComplexType != null) {
                Debug.logInfo("+++++++++++++++++++++++++ will get ComplexType, " + complexTypeName.getFullQualifiedNameAsString() + ", from reference cache", module);
                return csdlComplexType;
            }
        }
        return null;
    }

    @Override
    public CsdlEnumType getEnumType(final FullQualifiedName enumTypeName) throws OfbizODataException {
        if (cachedSchema != null) {
            CsdlEnumType csdlEnumType = cachedSchema.getEnumType(enumTypeName.getName());
            if (csdlEnumType != null) {
                return csdlEnumType;
            }
        }
        CsdlSchema referenceSchema = this.referenceSchemaMap.get(enumTypeName.getNamespace());
        if (referenceSchema != null) {
            CsdlEnumType csdlEnumType = referenceSchema.getEnumType(enumTypeName.getName());
            if (csdlEnumType != null) {
                Debug.logInfo("+++++++++++++++++++++++++ will get EnumType, " + enumTypeName.getFullQualifiedNameAsString() + ", from reference cache", module);
                return csdlEnumType;
            }
        }
        return null;
    }

    @Override
    public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) throws OfbizODataException {
        if (cachedSchema != null) {
            CsdlEntityType csdlEntityType = cachedSchema.getEntityType(entityTypeName.getName());
            return csdlEntityType;
        }
        return null;
    }

    @Override
    public CsdlEntitySet getEntitySet(FullQualifiedName entityContainer, String entitySetName) throws OfbizODataException {
        if (cachedSchema != null) {
            return cachedSchema.getEntityContainer().getEntitySet(entitySetName);
        }
        return null;
    }

    @Override
    public CsdlEntityContainer getEntityContainer() throws OfbizODataException {
        if (cachedSchema != null) {
            return cachedSchema.getEntityContainer();
        }
        return null;
    }

    private CsdlEntityContainer createEntityContainer(EdmWebConfig edmWebConfig, EdmWebConfig edmGlobalConfig) throws ODataException {
        CsdlEntityContainer entityContainer = new CsdlEntityContainer();
        entityContainer.setName(OfbizMapOdata.CONTAINER_NAME);

        List<CsdlEntitySet> entitySets = new ArrayList<CsdlEntitySet>();
        entitySets.addAll(edmWebConfig.getEntitySets());

        List<CsdlFunctionImport> functionImports = new ArrayList<CsdlFunctionImport>();
        functionImports.addAll(edmWebConfig.getFunctionImports());

        List<CsdlActionImport> actionImports = new ArrayList<CsdlActionImport>();
        actionImports.addAll(edmWebConfig.getActionImports());

        List<CsdlSingleton> singletons = new ArrayList<CsdlSingleton>();
        singletons.addAll(edmWebConfig.getSingletons());

        if (edmGlobalConfig != null) {
            entitySets.addAll(edmGlobalConfig.getEntitySets());
            functionImports.addAll(edmGlobalConfig.getFunctionImports());
            actionImports.addAll(edmGlobalConfig.getActionImports());
            singletons.addAll(edmGlobalConfig.getSingletons());
        }
        entityContainer.setEntitySets(entitySets);
        entityContainer.setFunctionImports(functionImports);
        entityContainer.setActionImports(actionImports);
        entityContainer.setSingletons(singletons);

        return entityContainer;
    }


    @Override
    public List<CsdlSchema> getSchemas() throws ODataException {
        Debug.logInfo("+++++++++++++++++++++++++ entering into getSchemas()", module);
        List<CsdlSchema> schemas = new ArrayList<CsdlSchema>();
        if (cachedSchema != null) {
            CsdlSchema theSchema = cachedSchema;
            personalizeSchema(theSchema);
            schemas.add(cachedSchema);
            return schemas;
        }
        return null;
    }

    private void personalizeSchema(CsdlSchema theSchema) {
        String selectionVariant = getSelectionVariant();
        setSelectionVariant(theSchema, selectionVariant);
        String presentationVariant = getPresentationVariant();
        setPresentationVariant(theSchema, presentationVariant);
    }

    private void setPresentationVariant(CsdlSchema theSchema, String presentationVariant) {
        // TODO 从presentationVariant的json值，解析出annotaion，加入到theschema中

    }

    private String getPresentationVariant() {
        // <UserPreference userLoginId="admin" userPrefTypeId="UI_VARIANT" userPrefValue="10000" />
        // <UiVariant appName="ProductManage" component="xxx" uiVariantId="10000" value="xxx" />
        // TODO 从以上两个表中获取variant的json值
        return null;
    }

    private void setSelectionVariant(CsdlSchema theSchema, String selectionVariant) {
        // TODO Auto-generated method stub

    }

    private String getSelectionVariant() {
        // TODO Auto-generated method stub
        return null;
    }

    private OfbizCsdlSchema createSchema(String namespace, EdmWebConfig edmWebConfig, EdmWebConfig edmGlobalConfig) throws ODataException {
        // create Schema
        OfbizCsdlSchema schema = new OfbizCsdlSchema();
        schema.setNamespace(namespace);

        // add EntityTypes
        List<CsdlEntityType> entityTypes = new ArrayList<CsdlEntityType>();
        entityTypes.addAll(edmWebConfig.getEntityTypes());
        // schema.setEntityTypes(edmConfig.getEntityTypes(edmWebConfig));
        // add complex types
        List<CsdlComplexType> complexTypes = new ArrayList<CsdlComplexType>();
        complexTypes.addAll(edmWebConfig.getComplexTypes());
        // add enum types
        List<CsdlEnumType> enumTypes = new ArrayList<CsdlEnumType>();
        enumTypes.addAll(edmWebConfig.getEnumTypes());
        // add functions
        List<CsdlFunction> functions = new ArrayList<CsdlFunction>();
        functions.addAll(edmWebConfig.getFunctions());
        // add actions
        List<CsdlAction> actions = new ArrayList<CsdlAction>();
        actions.addAll(edmWebConfig.getActions());
        // add EntityContainer
//		schema.setEntityContainer(getEntityContainer());
        schema.setEntityContainer(this.createEntityContainer(edmWebConfig, edmGlobalConfig));
        // add Annotations
        List<CsdlAnnotations> annotationses = new ArrayList<CsdlAnnotations>();
        annotationses.addAll(edmWebConfig.getAnnotationses());
        // add Terms
        List<CsdlTerm> terms = new ArrayList<CsdlTerm>();
        terms.addAll(edmWebConfig.getTerms());

        if (edmGlobalConfig != null) {
            Collection<OfbizCsdlEntityType> globalEntityTypes = edmGlobalConfig.getEntityTypes();
            for (OfbizCsdlEntityType ofbizCsdlEntityType : globalEntityTypes) {
                if (entityTypes.contains(ofbizCsdlEntityType)) {
                    continue;
                }
                entityTypes.add(ofbizCsdlEntityType);
            }
            // entityTypes.addAll(edmGlobalConfig.getEntityTypes());
            complexTypes.addAll(edmGlobalConfig.getComplexTypes());
            enumTypes.addAll(edmGlobalConfig.getEnumTypes());
            functions.addAll(edmGlobalConfig.getFunctions());
            actions.addAll(edmGlobalConfig.getActions());
            annotationses.addAll(edmGlobalConfig.getAnnotationses());
        }
        schema.setEntityTypes(entityTypes);
        schema.setComplexTypes(complexTypes);
        schema.setEnumTypes(enumTypes);
        schema.setFunctions(functions);
        schema.setActions(actions);
        schema.setAnnotationsGroup(annotationses);
        schema.setTerms(terms);
        schema.setMainEntityTypes(edmWebConfig.getMainEntityTypes());

        return schema;
    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) throws ODataException {
        Debug.logInfo("+++++++++++++++++++++++++ entering into getEntityContainerInfo()", module);

        // This method is invoked when displaying the Service Document at e.g.
        // http://localhost:8080/DemoService/DemoService.svc
        if (entityContainerName == null || entityContainerName.equals(CONTAINER)) {
            CsdlEntityContainerInfo entityContainerInfo = new CsdlEntityContainerInfo();
            entityContainerInfo.setContainerName(CONTAINER);
            return entityContainerInfo;
        }

        return null;
    }

    @Override
    public List<CsdlFunction> getFunctions(final FullQualifiedName functionName) throws OfbizODataException {
        if (cachedSchema != null) {
            return cachedSchema.getFunctions(functionName.getName());
        }
        return null;
    }

    @Override
    public CsdlFunctionImport getFunctionImport(FullQualifiedName entityContainer, String functionImportName) throws ODataException {
        if (cachedSchema != null) {
            return cachedSchema.getEntityContainer().getFunctionImport(functionImportName);
        }
        return null;
    }

    @Override
    public List<CsdlAction> getActions(final FullQualifiedName actionName) throws OfbizODataException {
        if (cachedSchema != null) {
            return cachedSchema.getActions(actionName.getName());
        }
        return null;
    }


    @Override
    public CsdlActionImport getActionImport(FullQualifiedName entityContainer, String actionImportName) throws ODataException {
        if (cachedSchema != null) {
            return cachedSchema.getEntityContainer().getActionImport(actionImportName);
        }
        return null;
    }

    @Override
    public CsdlSingleton getSingleton(final FullQualifiedName entityContainer, final String singletonName)
            throws OfbizODataException {
        if (cachedSchema != null) {
            return cachedSchema.getEntityContainer().getSingleton(singletonName);
        }
        return null;
    }

    @Override
    public CsdlTerm getTerm(final FullQualifiedName termName) throws ODataException {
        if (cachedSchema != null) {
            CsdlTerm csdlTerm = cachedSchema.getTerm(termName.getName());
            if (csdlTerm != null) {
                Debug.logInfo("+++++++++++++++++++++++++ will get Term from cache", module);
                return csdlTerm;
            }
        }
        CsdlSchema referenceSchema = this.referenceSchemaMap.get(termName.getNamespace());
        if (referenceSchema != null) {
            CsdlTerm csdlTerm = referenceSchema.getTerm(termName.getName());
            if (csdlTerm != null) {
                Debug.logInfo("+++++++++++++++++++++++++ will get Term, " + termName.getFullQualifiedNameAsString() + ", from reference cache", module);
                return csdlTerm;
            } else {
                //打印当前的term
                List<String> allTermName = referenceSchema.getTerms().stream().map(CsdlTerm::getName).collect(Collectors.toList());
                Debug.logInfo("++++++++++++++++++++++++++++ " + referenceSchema.getNamespace() + " >> " + allTermName, module);
            }
        } else {
            //打印当前的schema
            Collection<CsdlSchema> values = this.referenceSchemaMap.values();
            List<String> allSchema = values.stream().map(CsdlSchema::getNamespace).collect(Collectors.toList());
            Debug.logInfo("++++++++++++++++++++++++++++ " + allSchema, module);
        }
        Debug.logInfo("++++++++++++++++++++++++++++ not a term of the system " + termName, module);
        return null;
    }

    @Override
    public CsdlAnnotations getAnnotationsGroup(final FullQualifiedName targetName, String qualifier)
            throws ODataException {
        if (cachedSchema != null) {
            Debug.logInfo("+++++++++++++++++++++++++ will get AnnotaionGroup from cache", module);
            return cachedSchema.getAnnotationGroup(targetName.getName(), qualifier);
        }
        CsdlSchema referenceSchema = this.referenceSchemaMap.get(targetName.getNamespace());
        if (referenceSchema != null) {
            Debug.logInfo("+++++++++++++++++++++++++ will get AnnotaionGroup from reference cache", module);
            return referenceSchema.getAnnotationGroup(targetName.getName(), qualifier);
        }
        return null;
    }

    private InputStream getEdmFileInputStream(String filePath) {
        try {
            //Load from db
            GenericValue edmAppConfig = EntityQuery.use(delegator).from("EdmService").where("serviceName", webapp).queryFirst();
            if (edmAppConfig != null) {
                GenericValue edmContent = EntityQuery.use(delegator).from("EdmServiceContent")
                        .where("edmServiceId", edmAppConfig.getString("edmServiceId"), "format", "xml").queryFirst();
                if (UtilValidate.isNotEmpty(edmContent) && UtilValidate.isNotEmpty(edmContent.getString("edmContent"))) {
                    String edmConfigContent = edmContent.getString("edmContent");
                    return new ByteArrayInputStream(edmConfigContent.getBytes(StandardCharsets.UTF_8));
                }
            }
            //Load from local file
            String fileUrl = FlexibleLocation.resolveLocation(filePath).getFile();
            File file = new File(fileUrl);
            if (file.exists()) {
                return new FileInputStream(file);
            }
        } catch (FileNotFoundException | MalformedURLException | GenericEntityException e) {
            Debug.logInfo("------- didn't find file " + filePath + filePath, module);
        }
        return null;
    }

    private InputStream getSchemaInputStream(String filePath) {
        try {
            String fileUrl = FlexibleLocation.resolveLocation(filePath).getFile();
            File file = new File(fileUrl);
            if (file.exists()) {
                return new FileInputStream(file);
            }
        } catch (FileNotFoundException | MalformedURLException e) {
            Debug.logInfo("------- didn't find file " + filePath + filePath, module);
        }
        return null;
    }

    public String getETag() {
        return eTag;
    }
}
