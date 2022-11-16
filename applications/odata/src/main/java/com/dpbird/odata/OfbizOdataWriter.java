package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.ServiceValidationException;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import java.util.*;
import java.util.Map.Entry;

public class OfbizOdataWriter extends OfbizOdataProcessor {
	public static final String module = OfbizOdataWriter.class.getName();

	private EdmBindingTarget edmBindingTarget;
	private EdmEntityType edmTypeFilter;
	EdmNavigationProperty edmNavigationProperty;
	private List<UriParameter> keyParams;
	private Map<String, Object> keyMap;
	private List<UriParameter> navKeyParams;
	private Map<String, Object> navKeyMap;
	private Entity entityToWrite;
	private String rawServiceUri;
	private ServiceMetadata serviceMetadata;
	// private EdmWebConfig edmWebConfig;
	private boolean isCreatable = true;

	public OfbizOdataWriter(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
		super(odataContext, queryOptions, edmParams);
		if (UtilValidate.isNotEmpty(edmParams)) {
			this.edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
			this.edmTypeFilter = (EdmEntityType) edmParams.get("edmTypeFilter");
			this.edmNavigationProperty = (EdmNavigationProperty) edmParams.get("edmNavigationProperty");
			this.entityToWrite = (Entity) edmParams.get("entityToWrite");
			this.rawServiceUri = (String) edmParams.get("rawServiceUri");
		}
		this.oData = (OData) odataContext.get("oData");
		this.serviceMetadata = (ServiceMetadata) odataContext.get("serviceMetadata");
		this.edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
		if (this.entityToWrite != null) {
			processEntitySetValues();
		}
	}

	private void processEntitySetValues() {
		EntitySetHandler entitySetHandler = EntitySetHandlerFactory.getEntitySetHandler(this.edmBindingTarget.getName());
		String fullQualifiedTypeName = this.edmBindingTarget.getEntityType().getFullQualifiedName().getFullQualifiedNameAsString();
		if (!entityToWrite.getType().equals(fullQualifiedTypeName)) {
			return;
		}
		this.isCreatable = entitySetHandler.isCreatable();
		Map<String, Object> defaultValues = entitySetHandler.defaultFieldValues();
		if (UtilValidate.isNotEmpty(defaultValues)) {
			Set<Entry<String, Object>> entrySet = defaultValues.entrySet();
			for (Entry<String, Object> entry:entrySet) {
				Property property = new Property();
				property.setName(entry.getKey());
				EdmElement edmProperty = this.edmBindingTarget.getEntityType().getProperty(entry.getKey());
				if(UtilValidate.isEmpty(edmProperty)) {
					entityToWrite.addProperty(new Property(null, entry.getKey(), ValueType.PRIMITIVE, entry.getValue()));
					continue;
				}
				String typeFullQualifiedNameStr = edmProperty.getType().getFullQualifiedName().toString();
				property.setType(typeFullQualifiedNameStr);
				property.setValue(ValueType.PRIMITIVE, entry.getValue());
				entityToWrite.addProperty(property);
			}
		}
	}

	private void addEntitySetCondition(Entity entityToWrite) throws OfbizODataException {
		String fullQualifiedTypeName = this.edmBindingTarget.getEntityType().getFullQualifiedName().getFullQualifiedNameAsString();
		if (!entityToWrite.getType().equals(fullQualifiedTypeName)) {
			return;
		}
		OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) this.edmProvider.getEntityContainer()
				.getEntitySet(edmBindingTarget.getName());
		Map<String, Object> conditionMap = Util.parseConditionMap(csdlEntitySet.getConditionStr(), userLogin);
		if (UtilValidate.isNotEmpty(conditionMap)) {
			Set<Entry<String, Object>> entrySet = conditionMap.entrySet();
			for (Entry<String, Object> entry:entrySet) {
				Property property = new Property();
				property.setName(entry.getKey());
				EdmElement edmProperty = this.edmBindingTarget.getEntityType().getProperty(entry.getKey());
				if(UtilValidate.isEmpty(edmProperty)) {
					entityToWrite.addProperty(new Property(null, entry.getKey(), ValueType.PRIMITIVE, entry.getValue()));
					continue;
				}
				String typeFullQualifiedNameStr = edmProperty.getType().getFullQualifiedName().toString();
				property.setType(typeFullQualifiedNameStr);
				property.setValue(ValueType.PRIMITIVE, entry.getValue());
				entityToWrite.addProperty(property);
			}
		}
	}

	public OdataOfbizEntity createEntityData(Entity entityToWrite) throws OfbizODataException {
		OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, edmBindingTarget.getName());
		String entitySetHandler = csdlEntitySet.getHandler();
		EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
		addEntitySetCondition(entityToWrite); // 如果EntitySet有condition，将其转换成Property设置到entityToWrite
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
		String entityTypeHandler = csdlEntityType.getHandlerClass();
		GenericValue newGenericValue = null;
		/*********** create genericValue **********************************/
		try {
			// 先groovy validate输入参数
			boolean validateChecked = false;
			if (UtilValidate.isNotEmpty(entitySetHandler)) {
				GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
				try {
					groovyHelper.inValidateCreateGenericValue(entitySetHandler, entityToWrite);
					validateChecked = true;
				} catch (MissingMethodExceptionNoStack e){
					Debug.logInfo(e.getMessage(), module);
				}
			}
			if (!validateChecked && UtilValidate.isNotEmpty(entityTypeHandler)) {
				GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
				try {
					groovyHelper.inValidateCreateGenericValue(entityTypeHandler, entityToWrite);
					validateChecked = true;
				} catch (MissingMethodExceptionNoStack e){
					Debug.logInfo(e.getMessage(), module);
				}
			}

			// 然后groovy创建数据
			if (UtilValidate.isNotEmpty(entitySetHandler)) {
				GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
				try {
					newGenericValue = groovyHelper.createGenericValue(entitySetHandler, entityToWrite);
				} catch (MissingMethodExceptionNoStack e){
					Debug.logInfo(e.getMessage(), module);
				}
			}
			if (newGenericValue == null && UtilValidate.isNotEmpty(entityTypeHandler)) {
				GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
				try {
					newGenericValue = groovyHelper.createGenericValue(entityTypeHandler, entityToWrite);
				} catch (MissingMethodExceptionNoStack e){
					Debug.logInfo(e.getMessage(), module);
				}
			}

			// 如果groovy没有创建成功，用OdataProcessorHelper创建
			if (newGenericValue == null){
				newGenericValue = OdataProcessorHelper.createGenericValue(dispatcher, delegator, edmEntityType,
						entityToWrite, edmProvider, userLogin, oData, serviceMetadata);
			}
		} catch(GenericServiceException e){
			throw new OfbizODataException(e.getMessage());
		}

		/*********** end create genericValue ******************************/

		OdataOfbizEntity entityCreated = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
				edmBindingTarget, edmTypeFilter, newGenericValue, locale);

		// 创建语义话字段对应的数据库表的字段
		OdataProcessorHelper.createSemanticFields(delegator, dispatcher, this.edmProvider,
				entityToWrite, entityCreated, locale, userLogin);

		// 因为要返回所创建的Entity，所以，某些语义话字段可能是计算字段，也需要返回
		OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, this.edmProvider,
				queryOptions, Collections.singletonList(entityCreated), locale, userLogin);

		// 2.1.) Apply binding links
		applyBindingLinks(entityCreated, entityToWrite);

		// 2.2.) Create nested entities
		createNestedEntities(entityCreated, entityToWrite);

		return entityCreated;
	}
	
	private List<Entity> createNestedEntities(OdataOfbizEntity entityCreated, Entity entityToWrite) throws OfbizODataException {
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());

		List<Entity> nestedEntitiesCreated = new ArrayList<Entity>();
		for (final Link link : entityToWrite.getNavigationLinks()) {
			final EdmNavigationProperty edmNavigationProperty = edmBindingTarget.getEntityType().getNavigationProperty(link.getTitle());
			OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
			EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
			String handler = csdlNavigationProperty.getHandler();
			OfbizCsdlEntityType nestedCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
			boolean filterByDate = csdlNavigationProperty.isFilterByDate();
			try {
				if (edmNavigationProperty.isCollection() && link.getInlineEntitySet() != null) {
					for (Entity nestedEntityToCreate : link.getInlineEntitySet().getEntities()) {
						GenericValue nestedGenericValue = null;
						if (UtilValidate.isNotEmpty(handler)) {
							GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
							try {
								nestedGenericValue = groovyHelper.createNestedGenericValue(handler, nestedEntityToCreate, entityCreated, dispatcher, userLogin);
							} catch (MissingMethodExceptionNoStack e) {
								// Groovy 中不存在createNestedData方法
								Debug.logInfo(e.getMessage(), module);
							}
						}
						if (nestedGenericValue == null){
							nestedGenericValue = OdataProcessorHelper.createNestedGenericValue(nestedEntityToCreate, entityCreated, relAlias, dispatcher, delegator, userLogin);
						}
						OdataOfbizEntity nestedEntityCreated = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, nestedCsdlEntityType, nestedGenericValue, locale);

						setLink(entityCreated, edmNavigationProperty.getName(), nestedEntityCreated);
						nestedEntitiesCreated.add(nestedEntityCreated);
					}
				} else if (!edmNavigationProperty.isCollection() && link.getInlineEntity() != null) {
					Entity nestedEntityToCreate = link.getInlineEntity();
					List<GenericValue> relatedGenericValues = OdataProcessorHelper.getRelatedGenericValues(delegator, entityCreated.getGenericValue(), relAlias, filterByDate);
					OdataOfbizEntity nestedEntityCreated = null;
					if (UtilValidate.isNotEmpty(relatedGenericValues)) { // 如果子对象已经存在，就先删除
						OdataProcessorHelper.clearNavigationLink(entityCreated.getGenericValue(), relAlias, dispatcher, userLogin);
					}
					GenericValue nestedGenericValue = null;
					if (UtilValidate.isNotEmpty(handler)) {
						GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
						try {
							nestedGenericValue = groovyHelper.createNestedGenericValue(handler, nestedEntityToCreate, entityCreated, dispatcher, userLogin);
						} catch (MissingMethodExceptionNoStack e) {
							Debug.logInfo(e.getMessage(), module);
						}
					}
					if (nestedGenericValue == null){
						nestedGenericValue = OdataProcessorHelper.createNestedGenericValue(nestedEntityToCreate, entityCreated, relAlias, dispatcher, delegator, userLogin);
					}
					nestedEntityCreated = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, nestedCsdlEntityType, nestedGenericValue, locale);

					setLink(entityCreated, edmNavigationProperty.getName(), nestedEntityCreated);
					nestedEntitiesCreated.add(nestedEntityCreated);
				}
			} catch (GenericServiceException | ODataException | GenericEntityException e) {
				e.printStackTrace();
				throw new OfbizODataException(e.getMessage());
			}
		}
		return nestedEntitiesCreated;
	}

	private void setLink(Entity entity, String navigationPropertyName, Entity nestedEntity) {
		Link link = entity.getNavigationLink(navigationPropertyName);
		if (link == null) {
			link = new Link();
			link.setRel(Constants.NS_NAVIGATION_LINK_REL + navigationPropertyName);
			link.setType(Constants.ENTITY_SET_NAVIGATION_LINK_TYPE);
			link.setTitle(navigationPropertyName);
			if (entity.getId() != null){
				link.setHref(entity.getId().toASCIIString() + "/" + navigationPropertyName);
			}
			link.setInlineEntity(nestedEntity);

			entity.getNavigationLinks().add(link);
		} else {
			link.setInlineEntity(nestedEntity);
		}
	}

	private void applyBindingLinks(OdataOfbizEntity entityCreated, Entity entityToWrite) throws OfbizODataException {
		EdmEntityType edmEntityType = this.edmBindingTarget.getEntityType();
		for (final Link link : entityToWrite.getNavigationBindings()) {
			final EdmNavigationProperty edmNavigationProperty = edmEntityType.getNavigationProperty(link.getTitle());
			final EdmEntitySet nestedEntitySet = (EdmEntitySet) edmBindingTarget.getRelatedBindingTarget(link.getTitle());
			OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
			OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
			String handler = csdlNavigationProperty.getHandler();
			if (edmNavigationProperty.isCollection() && link.getBindingLinks() != null) {
				Long sequenceNum = 0L;
				for (final String bindingLink : link.getBindingLinks()) {
					final OdataOfbizEntity nestedEntity = readEntityByBindingLink(bindingLink, nestedEntitySet, rawServiceUri);
					sequenceNum = sequenceNum + 10L;
					if (UtilValidate.isNotEmpty(handler)) {
						GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
						groovyHelper.bindNavigationLink(handler, entityCreated, nestedEntity);

					} else {
						EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
						OdataProcessorHelper.bindNavigationLink(entityCreated, nestedEntity, relAlias, dispatcher, userLogin);
					}
					createLink(edmNavigationProperty, entityCreated, nestedEntity);
				}
			} else if (!edmNavigationProperty.isCollection() && link.getBindingLink() != null) {
				final OdataOfbizEntity nestedEntity = readEntityByBindingLink(link.getBindingLink(), nestedEntitySet, rawServiceUri);
				if (UtilValidate.isNotEmpty(handler)) {
					GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
					groovyHelper.bindNavigationLink(handler, entityCreated, nestedEntity);

				} else {
					EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
					// 因为是非collection，所以要把老的那个delete掉
					OdataProcessorHelper.clearNavigationLink(entityCreated.getGenericValue(),
							relAlias, dispatcher, userLogin);
					// 然后再bindNavigationLink
					OdataProcessorHelper.bindNavigationLink(entityCreated, nestedEntity, relAlias, dispatcher, userLogin);
				}
				createLink(edmNavigationProperty, entityCreated, nestedEntity);
			}
		}
	}

	@Deprecated
	private void createLink(EdmNavigationProperty edmNavigationProperty, Entity newEntity, Entity newNestedEntity) {
		// TODO Auto-generated method stub
		
	}

	private void setLinks(final Entity entity, final String navigationPropertyName, List<Entity> targets) {
		Link link = entity.getNavigationLink(navigationPropertyName);
		if (link == null) {
			link = new Link();
			link.setRel(Constants.NS_NAVIGATION_LINK_REL + navigationPropertyName);
			link.setType(Constants.ENTITY_SET_NAVIGATION_LINK_TYPE);
			link.setTitle(navigationPropertyName);
			if (entity.getId() != null){
				link.setHref(entity.getId().toASCIIString() + "/" + navigationPropertyName);
			}
			EntityCollection target = new EntityCollection();
			target.getEntities().addAll(targets);
			link.setInlineEntitySet(target);

			entity.getNavigationLinks().add(link);
		} else {
			link.getInlineEntitySet().getEntities().addAll(targets);
		}
	}

	private OdataOfbizEntity readEntityByBindingLink(final String entityId, final EdmEntitySet edmEntitySet,
			final String rawServiceUri) throws OfbizODataException {
		UriResourceEntitySet entitySetResource = null;
		try {
			entitySetResource = oData.createUriHelper().parseEntityId(serviceMetadata.getEdm(), entityId, rawServiceUri);

			if (!entitySetResource.getEntitySet().getName().equals(edmEntitySet.getName())) {
				throw new OfbizODataException(
						"Execpted an entity-id for entity set " + edmEntitySet.getName()
								+ " but found id for entity set " + entitySetResource.getEntitySet().getName());
			}
		} catch (DeserializerException e) {
			throw new OfbizODataException(entityId + " is not a valid entity-Id");
		}

		Map<String, Object> keyMap = Util.uriParametersToMap(entitySetResource.getKeyPredicates(), entitySetResource.getEntityType());
		GenericValue genericValue;
		try {
			genericValue = OdataProcessorHelper.readEntityData(odataContext, entitySetResource.getEntitySet(), keyMap);
		} catch (ODataException e) {
			e.printStackTrace();
			throw new OfbizODataException(entityId + " is not a valid entity-Id");
		}
		return OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
				entitySetResource.getEntitySet(), null, genericValue, locale);
	}

	public Entity createRelatedEntityData(Map<String, Object> keyMap, Entity entityToWrite) throws OfbizODataException {
		
		EdmEntityType edmEntityType = this.edmBindingTarget.getEntityType();
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
		OdataOfbizEntity entityCreated;
		if (edmBindingTarget instanceof EdmEntitySet) {
			entityCreated = OdataWriterHelper.createEntitySetRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider,
					csdlEntityType, keyMap, edmNavigationProperty.getName(), entityToWrite,
					queryOptions, userLogin, locale);
		} else {
			entityCreated = OdataWriterHelper.createSingletonRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider,
					csdlEntityType, edmBindingTarget.getName(), edmNavigationProperty.getName(), entityToWrite,
					queryOptions, userLogin, locale);
		}
		// 2.1.) Apply binding links
		applyBindingLinks(entityCreated, entityToWrite);

		// 2.2.) Create nested entities
		createNestedEntities(entityCreated, entityToWrite);

		return entityCreated;
	}

	public OdataOfbizEntity updateEntityData(Map<String, Object> keyMap, Entity entityToWrite)
			throws OfbizODataException {
		EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
		if (this.edmTypeFilter != null) {
			edmEntityType = edmTypeFilter;
		}
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
		OdataOfbizEntity updatedEntity = OdataWriterHelper.updateEntityData(delegator, dispatcher, httpServletRequest,
				edmProvider, csdlEntityType, keyMap, entityToWrite, userLogin, locale);

		// Apply binding links
		updatedEntity = Util.mergeEntity(updatedEntity, entityToWrite);
		this.applyBindingLinks(updatedEntity, entityToWrite);

		// Create nested entities
		createNestedEntities(updatedEntity, entityToWrite);
		return updatedEntity;

	}
	
	private Map<String, Object> addRequiredParms(ModelService modelService, GenericValue genericValue, Map<String, Object> fieldMap) {
		if (fieldMap == null) {
			fieldMap = new HashMap<String, Object>();
		}
		List<String> requiredParmNames = modelService.getParameterNames("IN", false, false);
		for (String requiredParmName:requiredParmNames) {
			if (!fieldMap.containsKey(requiredParmName) && genericValue.containsKey(requiredParmName)) {
				fieldMap.put(requiredParmName, genericValue.get(requiredParmName));
			}
		}
		return fieldMap;
	}

	public void deleteEntityData(Map<String, Object> keyMap) throws OfbizODataException {
		try {
			OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
			String entityName = edmBindingTarget.getEntityType().getName();
			String serviceName = Util.getEntityActionService(entityName, "delete", delegator);

			// 有可能EntityType的pk和GenericValue的pk不一致
			GenericValue genericValue;
			genericValue = OdataProcessorHelper.readEntityData(odataContext, edmBindingTarget, keyMap);
			Map<String, Object> fieldMap = new HashMap<>();
			fieldMap.putAll(genericValue.getPrimaryKey());
			fieldMap.put("userLogin", userLogin);
			Map<String, Object> result = ServiceUtil.returnSuccess();
			if (csdlEntityType.getHandlerClass() != null){
				GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
				try {
					groovyHelper.deleteGenericValue(csdlEntityType.getHandlerClass(), genericValue);
				} catch (MissingMethodExceptionNoStack e){
					Debug.logInfo(e.getMessage(), module);
					result = dispatcher.runSync(serviceName, fieldMap);
				}
			} else if (serviceName != null) {
				result = dispatcher.runSync(serviceName, fieldMap);
			}
			if (!ServiceUtil.isSuccess(result)) {
				throw new OfbizODataException(HttpStatus.SC_INTERNAL_SERVER_ERROR + "", "Error in deleting " + entityName);
			}
		} catch (GenericServiceException e) {
			e.printStackTrace();
			throw new OfbizODataException(HttpStatus.SC_INTERNAL_SERVER_ERROR + "", e.getMessage());
		}
	}

	public boolean validateModelServiceParams(ModelService modelService, Map<String, Object> ctx) {
		try {
			modelService.validate(ctx, ModelService.IN_PARAM, locale);
		} catch (ServiceValidationException e) {
			return false;
		}
		return true;
	}

	public void deleteRelatedEntityData(Map<String, Object> keyMap, Map<String, Object> navKeyMap,
										EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
		deleteRelatedEntityData(keyMap, navKeyMap, edmNavigationProperty.getName());
	}
	public void deleteRelatedEntityData(Map<String, Object> keyMap, Map<String, Object> navKeyMap,
												  String navigationPropertyName) throws OfbizODataException {
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
		if (edmBindingTarget instanceof EdmEntitySet) {
			OdataWriterHelper.deleteEntitySetRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider,
					csdlEntityType, navigationPropertyName, keyMap, navKeyMap, userLogin, locale);
		} else {
			OdataWriterHelper.deleteSingletonRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider,
					csdlEntityType, navigationPropertyName, edmBindingTarget.getName(), navKeyMap, userLogin, locale);
		}
	}
}
