package com.dpbird.odata.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用作Handler返回结果
 *
 * @date 2022/11/14
 */
public class HandlerResults {
    private int resultCount = 0;
    private List<? extends Map<String, Object>> resultData = new ArrayList<>();

    public HandlerResults(int resultCount, List<? extends Map<String, Object>> resultData) {
        this.resultCount = resultCount;
        this.resultData = resultData;
    }

    public HandlerResults() {
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
    }

    public List<? extends Map<String, Object>> getResultData() {
        return resultData;
    }

    public void setResultData(List<? extends Map<String, Object>> resultData) {
        this.resultData = resultData;
    }
}
