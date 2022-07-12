package com.dpbird.odata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.olingo.commons.api.edm.provider.CsdlActionImport;
import org.apache.olingo.commons.api.edm.provider.CsdlAnnotations;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlFunctionImport;
import org.apache.olingo.commons.api.edm.provider.CsdlSingleton;
import org.apache.olingo.commons.api.edm.provider.CsdlTerm;

import com.dpbird.odata.edm.OfbizCsdlAction;
import com.dpbird.odata.edm.OfbizCsdlComplexType;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlEnumType;
import com.dpbird.odata.edm.OfbizCsdlFunction;

public class EdmWebConfig {

	public static final String module = EdmWebConfig.class.getName();
    private List<OfbizCsdlComplexType> complexTypes = new ArrayList<OfbizCsdlComplexType>();
    private List<OfbizCsdlEnumType> enumTypes = new ArrayList<OfbizCsdlEnumType>();
	private Map<String, OfbizCsdlComplexType> complexTypeMap = new HashMap<String, OfbizCsdlComplexType>(); // key是ComplexType的name
	private Map<String, OfbizCsdlEnumType> enumTypeMap = new HashMap<String, OfbizCsdlEnumType>(); // key是EnumType的name
    private Map<String, OfbizCsdlEntityType> entityTypeMap = new HashMap<String, OfbizCsdlEntityType>(); // key是EntityType的name
	private Map<String, CsdlTerm> termMap = new HashMap<String, CsdlTerm>(); // key是Term的name
	private Map<String, CsdlEntitySet> entitySetMap = new HashMap<String, CsdlEntitySet>(); // key是EntitySet的name
    private List<OfbizCsdlFunction> functions = new ArrayList<OfbizCsdlFunction>();
    private Map<String, OfbizCsdlFunction> functionMap = new HashMap<String, OfbizCsdlFunction>(); // key是Function的name
    private List<OfbizCsdlAction> actions = new ArrayList<OfbizCsdlAction>();
    private Map<String, OfbizCsdlAction> actionMap = new HashMap<String, OfbizCsdlAction>(); // key是Action的name
    private Map<String, CsdlFunctionImport> functionImportMap = new HashMap<String, CsdlFunctionImport>(); // key是FunctionImport的name
    private Map<String, CsdlActionImport> actionImportMap = new HashMap<String, CsdlActionImport>(); // key是ActionImport的name
    private Map<String, CsdlSingleton> singletonMap = new HashMap<String, CsdlSingleton>(); // key是ActionImport的name
    private List<CsdlAnnotations> annotationses = new ArrayList<CsdlAnnotations>();
    private Map<String, Map<String, CsdlAnnotations>> annotationsMap = new HashMap<String, Map<String, CsdlAnnotations>>(); // key是Annotations的Target, Qualifier
	private List<String> mainEntityTypes = new ArrayList<>();

	public List<String> getMainEntityTypes() {
		return mainEntityTypes;
	}

	public void setMainEntityTypes(List<String> mainEntityTypes) {
		this.mainEntityTypes = mainEntityTypes;
	}

	public List<CsdlAnnotations> getAnnotationses() {
		return annotationses;
	}
	public void addAnnotations(CsdlAnnotations annotations) {
    	if (targetExists(annotations)) {
    		return;
		}
		this.annotationses.add(annotations);
		Map<String, CsdlAnnotations> targetedAnnotations = annotationsMap.get(annotations.getTarget());
		if (targetedAnnotations == null) {
			targetedAnnotations = new HashMap<String, CsdlAnnotations>();
			annotationsMap.put(annotations.getTarget(), targetedAnnotations);
		}
		targetedAnnotations.put(annotations.getQualifier(), annotations);
	}
	public CsdlAnnotations getAnnotations(String target, String qualifier) {
		if (UtilValidate.isNotEmpty(this.annotationsMap.get(target))) {
			return this.annotationsMap.get(target).get(qualifier);
		}
		return null;
	}
	public Map<String, OfbizCsdlComplexType> getComplexTypeMap() {
		return complexTypeMap;
    }
    public void addComplexType(OfbizCsdlComplexType complexType) {
		complexTypes.add(complexType);
		complexTypeMap.put(complexType.getName(), complexType);
    }
    public void addEnumType(OfbizCsdlEnumType enumType) {
    		enumTypes.add(enumType);
		enumTypeMap.put(enumType.getName(), enumType);
    }
    public Map<String, OfbizCsdlEnumType> getEnumTypeMap() {
    	return this.enumTypeMap;
    }
    public Map<String, OfbizCsdlEntityType> getEntityTypeMap() {
    		return entityTypeMap;
    }
    public Collection<CsdlTerm> getTerms() {
		return termMap.values();
	}
	public void addTerm(CsdlTerm term) {
//    	if (termMap.containsKey(term.getName())) {
//    		return;
//    	}
    	termMap.put(term.getName(), term);
	}
	public Map<String, CsdlTerm> getTermMap() {
		return termMap;
	}
	public void setTermMap(Map<String, CsdlTerm> termMap) {
		this.termMap = termMap;
	}
	public CsdlTerm getTerm(String name) {
		return this.termMap.get(name);
	}
	public void addEntityType(OfbizCsdlEntityType entityType) {
    	// 同一个对象entityType，同时放入List，快速给到schema用，放入Map，快速找到对应的ofbiz entity
//    	if (entityTypeMap.containsKey(entityType.getName())) {
//    		return;
//    	}
    	entityTypeMap.put(entityType.getName(), entityType);
    }
    
    public void addEntitySet(CsdlEntitySet entitySet) {
    		entitySetMap.put(entitySet.getName(), entitySet);
    }
    public void addFunction(OfbizCsdlFunction function) {
		this.functions.add(function);
		functionMap.put(function.getName(), function);
    }
    public void addFunctionImport(CsdlFunctionImport functionImport) {
    		functionImportMap.put(functionImport.getName(), functionImport);
    }
    public void addAction(OfbizCsdlAction action) {
		this.actions.add(action);
		actionMap.put(action.getName(), action);
    }
    public void addActionImport(CsdlActionImport actionImport) {
    		actionImportMap.put(actionImport.getName(), actionImport);
    }
    public void addSingleton(CsdlSingleton singleton) {
		singletonMap.put(singleton.getName(), singleton);
    }
    
    public List<OfbizCsdlComplexType> getComplexTypes() {
		return complexTypes;
    }
    
    public List<OfbizCsdlEnumType> getEnumTypes() {
		return enumTypes;
    }
    public Collection<OfbizCsdlEntityType> getEntityTypes() {
    		return entityTypeMap.values();
    }
    public Collection<CsdlEntitySet> getEntitySets() {
    	return entitySetMap.values();
    }
    public List<OfbizCsdlFunction> getFunctions() {
		return functions;
    }
    public List<OfbizCsdlAction> getActions() {
		return actions;
    }
    public Collection<CsdlFunctionImport> getFunctionImports() {
		return functionImportMap.values();
    }
    public Collection<CsdlActionImport> getActionImports() {
		return actionImportMap.values();
    }
    public Collection<CsdlSingleton> getSingletons() {
		return singletonMap.values();
    }
    
    public OfbizCsdlComplexType getComplexType(String complexTypeName) {
		if (complexTypeMap.get(complexTypeName) != null) {
			return complexTypeMap.get(complexTypeName);
		} else {
			return null;
		}
    }
    
    public OfbizCsdlEnumType getEnumType(String enumTypeName) {
		if (enumTypeMap.get(enumTypeName) != null) {
			return enumTypeMap.get(enumTypeName);
		} else {
			return null;
		}
    }
    public OfbizCsdlEntityType getEntityType(String entityTypeName) {
    		if (entityTypeMap.get(entityTypeName) != null) {
    			return entityTypeMap.get(entityTypeName);
    		} else {
    			return null;
    		}
    }
    
    public CsdlEntitySet getEntitySet(String entitySetName) {
		if (entitySetMap.get(entitySetName) != null) {
			return entitySetMap.get(entitySetName);
		} else {
			return null;
		}
    }

    public OfbizCsdlAction getAction(String actionName) {
    		if (actionMap.get(actionName) != null) {
    			return actionMap.get(actionName);
    		} else {
    			return null;
    		}
    }
    
	public OfbizCsdlFunction getFunction(String functionName)
	{
		return functionMap.get(functionName);
	}
    
    public CsdlActionImport getActionImport(String actionImportName) {
    		return actionImportMap.get(actionImportName);
    }
    
    public CsdlFunctionImport getFunctionImport(String functionImportName) {
		return functionImportMap.get(functionImportName);
    }
    
    public CsdlSingleton getSingleton(String singletonName) {
		if (singletonMap.get(singletonName) != null) {
			return singletonMap.get(singletonName);
		} else {
			return null;
		}
    }

	private boolean targetExists(CsdlAnnotations annotations) {
    	String annotationsTarget = annotations.getTarget();
		for (CsdlAnnotations csdlAnnotations:this.annotationses) {
			if (annotationsTarget.equals(csdlAnnotations.getTarget())) {
				return true;
			}
		}
		return false;
	}
}
