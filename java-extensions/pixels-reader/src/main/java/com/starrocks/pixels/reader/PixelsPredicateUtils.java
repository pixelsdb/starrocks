// Copyright 2024 PixelsDB. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.pixels.reader;

import io.pixelsdb.pixels.core.TypeDescription;
import io.pixelsdb.pixels.core.utils.Decimal;
import io.pixelsdb.pixels.executor.predicate.Bound;
import io.pixelsdb.pixels.executor.predicate.ColumnFilter;
import io.pixelsdb.pixels.executor.predicate.Filter;
import io.pixelsdb.pixels.executor.predicate.TableScanFilter;

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.util.Objects.requireNonNull;

// only support conjunction, and assume range and discrete do not occur in same column
// 只支持and，并假定范围查询和等值/不等值查询不会出现在同一列
// 一个界只能设定一次，比如有了一个更大的下界不会按顺序更新
public class PixelsPredicateUtils {

    public static TableScanFilter createTableScanFilter(
            String schemaName, String tableName, String[] filters, Map<String, String> colNameToType, String[] includeCols) {

        List<PixelsPredicateParser.ParsedPredicate> predicates = new ArrayList<>();

        for (String predicate : filters) {
            predicates.add(PixelsPredicateParser.parsePredicate(predicate));
        }

        Map<String, PixelsFilter> colFilters = new HashMap();

        for (PixelsPredicateParser.ParsedPredicate predicate : predicates) {
            String colName = predicate.getColumn();
            String colType = colNameToType.get(colName);
            System.out.println(colType);
            String rawColType = new String(colType);
            if(colType.contains("(")) {
                colType = colType.split("\\(")[0];
            }

            if(!colFilters.containsKey(colName)) {
                PixelsFilter filter = new PixelsFilter(colName, colType);
                if(predicate.operator == PixelsPredicateParser.BinaryType.NE ||
                predicate.operator == PixelsPredicateParser.BinaryType.EQ ||
                        predicate.operator == PixelsPredicateParser.BinaryType.IN) {
                    filter.setFilterType(PixelsFilter.FilterType.DISCRETE);
                }
                else {
                    filter.setFilterType(PixelsFilter.FilterType.RANGE);
                }
                colFilters.put(colName, filter);
            }

            PixelsFilter filter = colFilters.get(colName);

            Object val;
            // parse type value
            switch (filter.getColType()) {
                case "boolean":
                    val = Boolean.parseBoolean(predicate.getValue());
                    break;
                case "integer":
                case "int":
                    val = Integer.parseInt(predicate.getValue());
                    break;
                case "bigint":
                case "long":
                    val = Long.parseLong(predicate.getValue());
                    break;
                case "float":
                case "real":
                    val = Float.parseFloat(predicate.getValue());
                    break;
                case "double":
                    val = Double.parseDouble(predicate.getValue());
                    break;
                case "decimal":
//                    BigDecimal decimal = new BigDecimal(predicate.getValue());
//                    val = new Decimal(Long.parseLong(decimal.toPlainString().replace(".", "")),decimal.precision(),decimal.scale());
                    double figure = Double.parseDouble(predicate.getValue());
                    int decimalScale = Integer.valueOf(rawColType.split(",")[1].split("\\)")[0].trim());
                    val = (long)  (figure * Math.pow(10, decimalScale));
                    break;
                case "varbinary":
                case "binary":
                case "varchar":
                case "char":
                    val = predicate.getValue();
                    break;
                case "date":
                    Date date = Date.valueOf(predicate.getValue());

                    val =  (int) date.toLocalDate().toEpochDay();

                    break;
                default:
                    throw new IllegalArgumentException("Invalid colType: " + colType);
            }

            // add to which
            if(predicate.getOperator() == PixelsPredicateParser.BinaryType.EQ){
                filter.addInclusiveValue(val);
            }
            else if(predicate.getOperator() == PixelsPredicateParser.BinaryType.NE){
                filter.addExclusiveValue(val);
            }
            else if(predicate.getOperator() == PixelsPredicateParser.BinaryType.IN){
                String str = (String) val;
                if(str.startsWith("(")){
                    str = str.substring(1, str.length() - 2);
                }
                String[] split = str.split(",");
                for (String s : split) {
                    String item = s.trim();
                    System.out.println(item);
                    item = item.replaceFirst("^['\"]", "").replaceFirst("['\"]$", "");
                    Object obj = getObjectFromColumnType(rawColType, filter.getColType(), item);
                    filter.addInclusiveValue(obj);
                }
            }

            else if(predicate.getOperator() == PixelsPredicateParser.BinaryType.GT){
                filter.setLowUnbounded(false);
                filter.setLowInclusive(false);
                filter.setLowerBound(val);
            }
            else if(predicate.getOperator() == PixelsPredicateParser.BinaryType.GE){
                filter.setLowUnbounded(false);
                filter.setLowerBound(val);
            }
            else if(predicate.getOperator() == PixelsPredicateParser.BinaryType.LT){
                filter.setHighUnbounded(false);
                filter.setHighInclusive(false);
                filter.setUpperBound(val);
            }
            else if(predicate.getOperator() == PixelsPredicateParser.BinaryType.LE){
                filter.setHighUnbounded(false);
                filter.setUpperBound(val);
            }
            else if(predicate.getOperator() == PixelsPredicateParser.BinaryType.LIKE){

            }

        }

        SortedMap<Integer, ColumnFilter> columnFilters = new TreeMap<>();
        TableScanFilter tableScanFilter = new TableScanFilter(schemaName, tableName, columnFilters);

        Map<String, Integer> colToCid = new HashMap<>(includeCols.length);
        for (int i = 0; i < includeCols.length; ++i)
        {
            colToCid.put(includeCols[i], i);
        }

        if (colFilters.size() > 0) {

            for (Map.Entry<String, PixelsFilter> entry : colFilters.entrySet())
            {
                ColumnFilter<?> columnFilter = createColumnFilter(entry.getKey(), entry.getValue());
                if (colToCid.containsKey(entry.getKey()))
                {
                    columnFilters.put(colToCid.get(entry.getKey()), columnFilter);
                }
                else
                {
                    throw new RuntimeException("column '" + entry.getKey() + "' does not exist in the includeCols");
                }
            }
        }

        return tableScanFilter;
    }

    private static  <T extends Comparable<T>> ColumnFilter<T> createColumnFilter(
            String colName, PixelsFilter filter)
    {
        Class<?> javaType = filter.getFilterJavaType();
        String columnName = colName;
        String columnTypeString = filter.getColType().toUpperCase();
        if(columnTypeString.equals(("BIGINT"))) {
            columnTypeString = "LONG";
        }
        TypeDescription.Category columnType = TypeDescription.Category.valueOf(columnTypeString);
        Class<?> filterJavaType = columnType.getInternalJavaType() == byte[].class ?
                String.class : columnType.getInternalJavaType();
        if(filterJavaType == Decimal.class) {
            filterJavaType = long.class;
        }
        boolean isAll = false;
        boolean isNone = false;
        boolean allowNull = true;
        boolean onlyNull = false;

        Filter<T> res = new Filter<>(filterJavaType, isAll, isNone, allowNull, onlyNull);

        if(filter.getFilterType() == PixelsFilter.FilterType.RANGE) {
            Bound.Type lowerBoundType = filter.isLowInclusive() ?
                    Bound.Type.INCLUDED : Bound.Type.EXCLUDED;
            Bound.Type upperBoundType = filter.isHighInclusive() ?
                    Bound.Type.INCLUDED : Bound.Type.EXCLUDED;
            Object lowerBoundValue = null, upperBoundValue = null;
            if (filter.isLowUnbounded())
            {
                lowerBoundType = Bound.Type.UNBOUNDED;
            } else
            {
                lowerBoundValue = filter.getLowerBound();
            }
            if (filter.isHighUnbounded())
            {
                upperBoundType = Bound.Type.UNBOUNDED;
            } else
            {
                upperBoundValue = filter.getUpperBound();
            }

            Bound<?> lowerBound = createBound(filterJavaType, filter.getColType(), lowerBoundType, lowerBoundValue);
            Bound<?> upperBound = createBound(filterJavaType, filter.getColType(), upperBoundType, upperBoundValue);
            res.addRange((Bound<T>) lowerBound, (Bound<T>) upperBound);
        }
        else if(filter.getFilterType() == PixelsFilter.FilterType.DISCRETE) {
            Bound.Type boundType = filter.getInclusiveValues().size() > 0 ?
                    Bound.Type.INCLUDED : Bound.Type.EXCLUDED;
            if (filter.getInclusiveValues().size() > 0) {
                for (Object value : filter.getInclusiveValues()) {
                    if (value == null) {
                        throw new RuntimeException("discrete value is null");
                    } else {
                        Bound<?> bound = createBound(filterJavaType, filter.getColType(), boundType, value);
                        res.addDiscreteValue((Bound<T>) bound);
                    }
                }
            }
            else {
                for (Object value : filter.getExclusiveValues()) {
                    if (value == null) {
                        throw new RuntimeException("discrete value is null");
                    } else {
                        Bound<?> bound = createBound(filterJavaType, filter.getColType(), boundType, value);
                        res.addDiscreteValue((Bound<T>) bound);
                    }
                }
            }

        }



        return new ColumnFilter<>(columnName, columnType, res);
    }

    private static Bound<?> createBound(Class<?> javaType, String rawType, Bound.Type boundType, Object value)
    {
        Bound<?> bound = null;
        if (boundType == Bound.Type.UNBOUNDED)
        {
            bound = new Bound<>(boundType, null);
        }
        else
        {
            requireNonNull(value, "the value of the bound is null");
            if (javaType == long.class)
            {
                switch (rawType)
                {
                    case "date":
                    case "time":
                        bound = new Bound<>(boundType, ((Long) value).intValue());
                        break;
                    default:
                        bound = new Bound<>(boundType, (Long) value);
                        break;
                }
            }
            else if (javaType == int.class)
            {
                bound = new Bound<>(boundType, (Integer) value);
            }
            else if (javaType == double.class)
            {
                bound = new Bound<>(boundType, Double.doubleToLongBits((Double) value));
            }
            else if (javaType == boolean.class)
            {
                bound = new Bound<>(boundType, (byte) ((Boolean) value ? 1 : 0));
            }
            else if (javaType == String.class)
            {
                bound = new Bound<>(boundType, (String) value);
            }
//            else if (javaType == Decimal.class)
//            {
//                bound = new Bound<>(boundType, (Decimal) value);
//            }
//            else if (javaType == Slice.class)
//            {
//                bound = new Bound<>(boundType, ((Slice) value).toString(StandardCharsets.UTF_8).trim());
//            }
            else
            {
                throw new RuntimeException("unsupported data type for filter bound: " + javaType.getName());
            }
        }

        return bound;
    }

    private static Object getObjectFromColumnType(String rawColType, String type, String item) {
        Object val;
        switch (type) {
            case "boolean":
                val = Boolean.parseBoolean(item);
                break;
            case "integer":
            case "int":
                val = Integer.parseInt(item);
                break;
            case "bigint":
            case "long":
                val = Long.parseLong(item);
                break;
            case "float":
            case "real":
                val = Float.parseFloat(item);
                break;
            case "double":
                val = Double.parseDouble(item);
                break;
            case "decimal":
                //                    BigDecimal decimal = new BigDecimal(item);
                //                    val = new Decimal(Long.parseLong(decimal.toPlainString().replace(".", "")),decimal.precision(),decimal.scale());
                double figure = Double.parseDouble(item);
                int decimalScale = Integer.valueOf(rawColType.split(",")[1].split("\\)")[0].trim());
                val = (long)  (figure * Math.pow(10, decimalScale));
                break;
            case "varbinary":
            case "binary":
            case "varchar":
            case "char":
                val = item;
                break;
            case "date":
                Date date = Date.valueOf(item);

                val =  (int) date.toLocalDate().toEpochDay();

                break;
            default:
                throw new IllegalArgumentException("Invalid colType: " + type);
        }
        return val;
    }

}
