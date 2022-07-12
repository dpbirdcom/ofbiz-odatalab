package com.dpbird.odata;

import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmSingleton;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.queryoption.*;

import java.util.List;
import java.util.Map;

public class OdataRequestParms {

	private EdmEntitySet edmEntitySet;
	private EdmSingleton edmSingleton;
	private EdmType edmTypeFilter;
	private List<UriParameter> keyParams;
	private Map<String, Object> keyMap;
	private EdmNavigationProperty edmNavigationProperty;
	private List<UriParameter> navKeyParams;
	private Map<String, Object> navKeyMap;
	private FilterOption filterOption;
	private SelectOption selectOption;
	private ExpandOption expandOption;
	private SkipOption skipOption;
	private TopOption topOption;
	private OrderByOption orderByOption;
	private SearchOption searchOption;
	private ApplyOption applyOption;
	private boolean isCount = false;
	
	public OdataRequestParms() {
		this.edmEntitySet = null;
		this.edmTypeFilter = null;
		this.edmSingleton = null;
		this.keyParams = null;
		this.edmNavigationProperty = null;
		this.navKeyParams = null;
		this.filterOption = null;
		this.selectOption = null;
		this.expandOption = null;
		this.skipOption = null;
		this.topOption = null;
		this.orderByOption = null;
		this.searchOption = null;
		this.applyOption = null;
	}
	
	public OdataRequestParms(EdmEntitySet edmEntitySet, EdmSingleton edmSingleton, EdmType edmTypeFilter, Map<String, Object> keyMap,
			EdmNavigationProperty edmNavigationProperty, Map<String, Object> navKeyMap, FilterOption filterOption,
			SelectOption selectOption, ExpandOption expandOption, SkipOption skipOption, TopOption topOption,
			OrderByOption orderByOption, SearchOption searchOption, ApplyOption applyOption, boolean isCount) {
		super();
		this.edmEntitySet = edmEntitySet;
		this.edmSingleton = edmSingleton;
		this.edmTypeFilter = edmTypeFilter;
		this.keyMap = keyMap;
		this.edmNavigationProperty = edmNavigationProperty;
		this.navKeyMap = navKeyMap;
		this.filterOption = filterOption;
		this.selectOption = selectOption;
		this.expandOption = expandOption;
		this.skipOption = skipOption;
		this.topOption = topOption;
		this.orderByOption = orderByOption;
		this.searchOption = searchOption;
		this.applyOption = applyOption;
		this.isCount = isCount;
	}

	public EdmEntitySet getEdmEntitySet() {
		return edmEntitySet;
	}

	public void setEdmEntitySet(EdmEntitySet edmEntitySet) {
		this.edmEntitySet = edmEntitySet;
	}

	public EdmType getEdmTypeFilter() {
		return edmTypeFilter;
	}

	public void setEdmTypeFilter(EdmType edmTypeFilter) {
		this.edmTypeFilter = edmTypeFilter;
	}

	public EdmNavigationProperty getEdmNavigationProperty() {
		return edmNavigationProperty;
	}

	public void setEdmNavigationProperty(EdmNavigationProperty edmNavigationProperty) {
		this.edmNavigationProperty = edmNavigationProperty;
	}

	public List<UriParameter> getNavKeyParams() {
		return navKeyParams;
	}

	public void setNavKeyParams(List<UriParameter> navKeyParams) {
		this.navKeyParams = navKeyParams;
	}

	public FilterOption getFilterOption() {
		return filterOption;
	}

	public void setFilterOption(FilterOption filterOption) {
		this.filterOption = filterOption;
	}

	public SelectOption getSelectOption() {
		return selectOption;
	}

	public void setSelectOption(SelectOption selectOption) {
		this.selectOption = selectOption;
	}

	public ExpandOption getExpandOption() {
		return expandOption;
	}

	public void setExpandOption(ExpandOption expandOption) {
		this.expandOption = expandOption;
	}

	public SkipOption getSkipOption() {
		return skipOption;
	}

	public void setSkipOption(SkipOption skipOption) {
		this.skipOption = skipOption;
	}

	public TopOption getTopOption() {
		return topOption;
	}

	public void setTopOption(TopOption topOption) {
		this.topOption = topOption;
	}

	public OrderByOption getOrderByOption() {
		return orderByOption;
	}

	public void setOrderByOption(OrderByOption orderByOption) {
		this.orderByOption = orderByOption;
	}

	public SearchOption getSearchOption() {
		return searchOption;
	}

	public void setSearchOption(SearchOption searchOption) {
		this.searchOption = searchOption;
	}

	public ApplyOption getApplyOption() {
		return applyOption;
	}

	public void setApplyOption(ApplyOption applyOption) {
		this.applyOption = applyOption;
	}

	public boolean isCount() {
		return isCount;
	}

	public void setCount(boolean isCount) {
		this.isCount = isCount;
	}

	public Map<String, Object> getKeyMap() {
		return keyMap;
	}

	public void setKeyMap(Map<String, Object> keyMap) {
		this.keyMap = keyMap;
	}

	public Map<String, Object> getNavKeyMap() {
		return navKeyMap;
	}

	public void setNavKeyMap(Map<String, Object> navKeyMap) {
		this.navKeyMap = navKeyMap;
	}

	public EdmSingleton getEdmSingleton() {
		return edmSingleton;
	}

	public void setEdmSingleton(EdmSingleton edmSingleton) {
		this.edmSingleton = edmSingleton;
	}

}
