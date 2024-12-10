package com.starrocks.pixels.reader;

import io.pixelsdb.pixels.core.utils.Decimal;

import java.util.ArrayList;
import java.util.List;

public class PixelsFilter {

    public enum FilterType {
        RANGE,
        DISCRETE
    }

    private String colName;
    private String colType;
    private Class<?> filterJavaType;
    private FilterType filterType;
    private Object lowerBound;
    private Object upperBound;
    private boolean lowUnbounded = true;
    private boolean highUnbounded = true;
    private boolean lowInclusive = true;
    private boolean highInclusive = true;
    private List<Object> inclusiveValues = new ArrayList<>();
    private List<Object> exclusiveValues = new ArrayList<>();

    public PixelsFilter(String colName, String colType) {
        this.colName = colName;
        this.colType = colType;
        switch (colType) {
            case "boolean":
                filterJavaType = byte.class;
                break;
            case "tinyint":
            case "byte":
                filterJavaType = byte.class;
                break;
            case "smallint":
            case "short":
                filterJavaType = long.class;
                break;
            case "integer":
            case "int":
                filterJavaType = long.class;
                break;
            case "bigint":
            case "long":
                filterJavaType = long.class;
                break;
            case "float":
            case "real":
                filterJavaType = long.class;
                break;
            case "double":
                filterJavaType = long.class;
                break;
            case "decimal":
//                filterJavaType = Decimal.class;
                filterJavaType = double.class;
                break;
            case "string":
                filterJavaType = byte[].class;
                break;
            case "date":
                filterJavaType = int.class;
                break;
            case "time":
                filterJavaType = int.class;
                break;
            case "timestamp":
                filterJavaType = long.class;
                break;
            case "varbinary":
            case "binary":
                filterJavaType = byte[].class;
                break;
            case "varchar":
                filterJavaType = byte[].class;
                break;
            case "char":
                filterJavaType = byte[].class;
                break;
            case "struct":
                filterJavaType = Class.class;
                break;
            case "vector":
            case "array":
                filterJavaType = double[].class;
                break;
            default:
                throw new IllegalArgumentException("Invalid colType: " + colType);
        }
    }

    public Class<?> getFilterJavaType(){
        return filterJavaType;
    }

    public List<Object> getInclusiveValues() {
        return inclusiveValues;
    }

    public List<Object> getExclusiveValues() {
        return exclusiveValues;
    }


    // 添加包含值的方法
    public void addInclusiveValue(Object val) {
        inclusiveValues.add(val);
    }

    // 添加排除值的方法
    public void addExclusiveValue(Object val) {
        exclusiveValues.add(val);
    }

    // Getter 和 Setter 方法

    public String getColName() {
        return colName;
    }

    public void setColName(String colName) {
        this.colName = colName;
    }

    public String getColType() {
        return colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    public FilterType getFilterType() {
        return filterType;
    }

    public void setFilterType(FilterType filterType) {
        this.filterType = filterType;
    }

    public Object getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(Object lowerBound) {
        this.lowerBound = lowerBound;
    }

    public Object getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(Object upperBound) {
        this.upperBound = upperBound;
    }

    public boolean isLowUnbounded() {
        return lowUnbounded;
    }

    public void setLowUnbounded(boolean lowUnbounded) {
        this.lowUnbounded = lowUnbounded;
    }

    public boolean isHighUnbounded() {
        return highUnbounded;
    }

    public void setHighUnbounded(boolean highUnbounded) {
        this.highUnbounded = highUnbounded;
    }

    public boolean isLowInclusive() {
        return lowInclusive;
    }

    public void setLowInclusive(boolean lowInclusive) {
        this.lowInclusive = lowInclusive;
    }

    public boolean isHighInclusive() {
        return highInclusive;
    }

    public void setHighInclusive(boolean highInclusive) {
        this.highInclusive = highInclusive;
    }
}
