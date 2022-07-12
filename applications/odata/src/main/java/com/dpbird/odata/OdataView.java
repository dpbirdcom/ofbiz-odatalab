package com.dpbird.odata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.collections.PagedList;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.DynamicViewEntity;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelViewEntity;
import org.apache.ofbiz.entity.model.ModelViewEntity.ComplexAlias;
import org.apache.ofbiz.entity.model.ModelViewEntity.ComplexAliasField;
import org.apache.ofbiz.entity.model.ModelViewEntity.ModelViewLink;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityFindOptions;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.server.api.uri.queryoption.ApplyOption;

public class OdataView {
	public static final String module = OdataView.class.getName();
	private DynamicViewEntity dynamicViewEntity;
	private Delegator delegator;

	public OdataView(Delegator delegator, String entityName) {
		super();
		this.delegator = delegator;
		try {
			makeViewEntity(entityName);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean isOdataView(Delegator delegator, String entityName) {
		GenericValue odataView = null;
		try {
			odataView = delegator.findOne("OdataView", UtilMisc.toMap("viewName", entityName), true);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		if (odataView == null) {
			return false;
		}
		return true;
	}
	
	public static GenericValue getRelation(Delegator delegator,String availableEntityName, String relEntityName) {
		List<GenericValue> relations = null;
		GenericValue result = null;
		try {
			relations = delegator.findByAnd("Relation",UtilMisc.toMap("availableEntityName",availableEntityName,"relEntityName",relEntityName),null,false);
         } catch (GenericEntityException e) {
			e.printStackTrace();
		}
		if(UtilValidate.isNotEmpty(relations)){
			result =  EntityUtil.getFirst(relations);
        }
		return result;
	}
	
	public static List<Map<String, Object>> getRelationKeyMap(Delegator delegator,String availableEntityName, String relEntityName) {
		List<Map<String, Object>> result = new ArrayList<>(); 
		List<GenericValue> relationKeyMaps = null;
		try {
			relationKeyMaps = delegator.findByAnd("RelationKeyMap",UtilMisc.toMap("availableEntityName",availableEntityName,"relEntityName",relEntityName),null,false);
         } catch (GenericEntityException e) {
			e.printStackTrace();
		}
		if(UtilValidate.isNotEmpty(relationKeyMaps)){
			for (GenericValue genericValue : relationKeyMaps) {
				Map<String, Object> resultMap = new HashMap<>();
				resultMap.put("fieldName",genericValue.getString("fieldName"));
				String relFieldName = genericValue.getString("relFieldName");
				if(UtilValidate.isNotEmpty(relFieldName)) {
					resultMap.put("relFieldName",relFieldName);
				}else {
					resultMap.put("relFieldName",genericValue.getString("fieldName"));
				}
				result.add(resultMap);
			}
        }
		return result;
	}
	
	public DynamicViewEntity getDynamicViewEntity() {
		return dynamicViewEntity;
	}
	public void setDynamicViewEntity(DynamicViewEntity dynamicViewEntity) {
		this.dynamicViewEntity = dynamicViewEntity;
	}
	
	private void makeViewEntity(String entityName) throws GenericEntityException {
		GenericValue odataView = delegator.findOne("OdataView", UtilMisc.toMap("viewName", entityName), true);
		if (odataView == null) {
			dynamicViewEntity = null;
			return;
		}
		dynamicViewEntity = new DynamicViewEntity();
		dynamicViewEntity.setEntityName(entityName);
		List<GenericValue> memberEntities = odataView.getRelated("MemberEntity", null, null, true);
		for (GenericValue memberEntity:memberEntities) {
			dynamicViewEntity.addMemberEntity(memberEntity.getString("entityAlias"), memberEntity.getString("memberEntityName"));
		}
		List<GenericValue> aliasAlls = odataView.getRelated("AliasAll", null, null, true);
		for (GenericValue aliasAll:aliasAlls) {
			boolean groupBy = aliasAll.getBoolean("groupBy");
			// fix for upgrade
			// dynamicViewEntity.addAliasAll(aliasAll.getString("entityAlias"), aliasAll.getString("aliasPrefix"), null, groupBy);
			dynamicViewEntity.addAliasAll(aliasAll.getString("entityAlias"), aliasAll.getString("aliasPrefix"), null);
		}
		List<GenericValue> fieldAliases = odataView.getRelated("FieldAlias", null, null, true);
		for (GenericValue fieldAlias:fieldAliases) {
			GenericValue complexAliasGV = fieldAlias.getRelatedOne("ComplexAlias", true);
			ComplexAlias complexAlias = null;
			if (complexAliasGV != null) {
				complexAlias = new ComplexAlias(complexAliasGV.getString("operator"));
				List<GenericValue> complexAliasFieldGVs = complexAliasGV.getRelated("ComplexAliasField", null, null, true);
				for (GenericValue complexAliasFieldGV:complexAliasFieldGVs) {
					ComplexAliasField complexAliasField = new ComplexAliasField(complexAliasFieldGV.getString("operatorAlias"),
							complexAliasFieldGV.getString("operatorField"),
							complexAliasFieldGV.getString("defaultValue"),
							complexAliasFieldGV.getString("fieldFunction"),
							complexAliasFieldGV.getString("fieldValue"));
					complexAlias.addComplexAliasMember(complexAliasField);
				}
				dynamicViewEntity.addAlias(fieldAlias.getString("entityAlias"), fieldAlias.getString("aliasName"),
						fieldAlias.getString("aliasField"), fieldAlias.getString("colAlias"), fieldAlias.getBoolean("isPk"),
						fieldAlias.getBoolean("groupBy"), fieldAlias.getString("aliasFunction"), complexAlias);
			} else {
				dynamicViewEntity.addAlias(fieldAlias.getString("entityAlias"), fieldAlias.getString("aliasName"),
						fieldAlias.getString("aliasField"), fieldAlias.getString("colAlias"), fieldAlias.getBoolean("isPk"),
						fieldAlias.getBoolean("groupBy"), fieldAlias.getString("aliasFunction"));
			}
		}
		List<GenericValue> viewLinks = odataView.getRelated("ViewLink", null, null, true);
		for (GenericValue viewLink:viewLinks) {
			ModelViewEntity modelViewEntity = dynamicViewEntity.makeModelViewEntity(delegator);
			ModelViewLink modelViewLink = new ModelViewLink(modelViewEntity, viewLink);
			// fix for upgrade
			// dynamicViewEntity.addViewLink(modelViewLink);
			dynamicViewEntity.addAllViewLinksToList(UtilMisc.toList(modelViewLink));
			
		}
		List<GenericValue> relations = odataView.getRelated("Relation", null, null, true);
		for (GenericValue relation:relations) {
			List<GenericValue> keyMaps = relation.getRelated("RelationKeyMap", null, null, true);
			List<ModelKeyMap> modelKeyMaps = new ArrayList<ModelKeyMap>();
			for (GenericValue keyMap:keyMaps) {
				ModelKeyMap modelKeyMap = new ModelKeyMap(keyMap.getString("fieldName"), keyMap.getString("relFieldName"));
				modelKeyMaps.add(modelKeyMap);
			}
			String title = relation.getString("title");
			if (title.equals("_NA_")) {
				title = null;
			}
			dynamicViewEntity.addRelation(relation.getString("relType"), title,
					relation.getString("relEntityName"), modelKeyMaps);
		}
	}
	
	public PagedList<GenericValue> findList(EntityCondition entityCondition,
			ApplyOption applyOption, Set<String> fieldsToSelect, List<String> orderBy,
			EntityFindOptions efo, boolean isCount) {
		EntityListIterator entityListIt = null;
		List<GenericValue> resultList = new ArrayList<GenericValue>();
		long listCount = 0L;
		GenericValue genericValue;
		/********************** debug用，输出dynamicViewEntity的xml表达式 ****************************************/
		try {
			
			String dynamicViewXml = dynamicViewEntity.getViewXml(dynamicViewEntity.getEntityName());
			Debug.logInfo(dynamicViewXml, module);
			if (entityCondition != null) {
				Debug.logInfo(entityCondition.toString(), module);
			}
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		/*************************************************************************************/
        boolean beganTransaction = false;
        try {
            // make sure this is in a transaction
            beganTransaction = TransactionUtil.begin();
            EntityQuery entityQuery = EntityQuery.use(delegator)
	                .select(UtilMisc.toSet(fieldsToSelect))
	                .from(dynamicViewEntity)
	                .having(entityCondition);
            if (isCount) {
            	listCount = entityQuery.queryCount();
			}
            entityQuery = entityQuery.orderBy(orderBy);
            if (efo != null) {
            	entityQuery = entityQuery.fetchSize(efo.getFetchSize())
            			.maxRows(efo.getMaxRows());
            }

			entityListIt = entityQuery
	                .distinct(true)
	                .cursorScrollInsensitive()
	                .queryIterator();
			
			resultList = entityListIt.getCompleteList();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Error saving product search result info/stats", module);
            try {
                TransactionUtil.rollback(beganTransaction, e.getMessage(), e);
            } catch (GenericTransactionException e1) {
                Debug.logError(e1, module);
            }
        } finally {
            if (entityListIt != null) {
                try {
                	entityListIt.close();
                } catch (GenericEntityException gee) {
                    Debug.logError(gee, "Error closing EntityListIterator when indexing content keywords.", module);
                }
            }

            // commit the transaction
            try {
                TransactionUtil.commit(beganTransaction);
            } catch (GenericTransactionException e) {
                Debug.logError(e, module);
            }
        }
        // 注意，这里的PagedList，只有data和size是维护的，其它字段暂时不可靠
        PagedList<GenericValue> result = new PagedList<GenericValue>(0, 20, (int) listCount, 0, 0, resultList);
        return result;
	}
	
	public String getViewXml() {
		try {
			return dynamicViewEntity.getViewXml(dynamicViewEntity.getEntityName());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
