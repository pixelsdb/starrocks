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

import java.util.HashSet;
import java.util.Set;
import java.util.regex.*;

public class PixelsPredicateParser {

    // 定义正则表达式，匹配列名、比较符和数字
    private static final String PREDICATE_REGEX = "(\\w+)\\s*(=|!=|<>|>|<|>=|<=|LIKE|IN|like|in)\\s*(\\(.*?\\)|'[^']*'|\"[^\"]*\"|\\S+)";

    public static enum BinaryType {
        EQ("=", "eq", false),
        NE("!=", "ne", false),
        LE("<=", "le", true),
        GE(">=", "ge", true),
        LT("<", "lt", true),
        GT(">", "gt", true),
        LIKE("LIKE", "like", false),
        IN("IN", "in", false);

        private final String type;
        private final String name;
        private final boolean monotonic;

        BinaryType(String description,
                   String name,
                   boolean monotonic) {
            this.type = description;
            this.name = name;
            this.monotonic = monotonic;
        }

        @Override
        public String toString() {
            return type;
        }

        public String getName() {
            return name;
        }

    }

    // 定义一个结构体来存储解析结果
    public static class ParsedPredicate {
        String column;    // 列名
        BinaryType operator;  // 比较符
        String value;    // 数字

        public ParsedPredicate(String column, String operator, String value) {
            this.column = column;
            switch (operator.toUpperCase()) {
                case "=":
                    this.operator = BinaryType.EQ;
                    break;
                case "<>":
                case "!=":
                    this.operator = BinaryType.NE;
                    break;
                case "<=":
                    this.operator = BinaryType.LE;
                    break;
                case ">=":
                    this.operator = BinaryType.GE;
                    break;
                case "<":
                    this.operator = BinaryType.LT;
                    break;
                case ">":
                    this.operator = BinaryType.GT;
                    break;
                case "LIKE":
                    this.operator = BinaryType.LIKE;
                    break;
                case "IN":
                    this.operator = BinaryType.IN;
                    break;
                default:
                    // 处理未知操作符的情况（例如：抛出异常或赋予默认值）
                    throw new IllegalArgumentException("Invalid operator: " + operator);
            }
            this.value = value;
            if(value.length() > 0 && (value.startsWith("'") || value.startsWith("\""))) {
                    this.value = value.replaceFirst("^['\"]", "").replaceFirst("['\"]$", "");
            }
            System.out.println(value);
        }

        @Override
        public String toString() {
            return String.format("Column: %s, Operator: %s, Value: %s", column, operator, value);
        }

        public String getColumn() {
            return column;
        }

        public BinaryType getOperator() {
            return operator;
        }

        public String getValue() {
            return value;
        }

    }

    // 解析单个谓词的方法
    public static ParsedPredicate parsePredicate(String predicate) {
        if(predicate.contains((":"))) {
            predicate = predicate.split(":")[1];
        }

        Pattern pattern = Pattern.compile(PREDICATE_REGEX);
        Matcher matcher = pattern.matcher(predicate.trim());

        if (matcher.matches()) {
            String column = matcher.group(1);   // 列名
            String operator = matcher.group(2); // 比较符
            String value = matcher.group(3);

            if(column.endsWith(")")) {
                column = column.substring(0, column.length() - 1);
            }

            return new ParsedPredicate(column, operator, value);
        } else {
            throw new IllegalArgumentException("Invalid predicate format: " + predicate);
        }
    }

    public static Set<String> getFilterColumnNames(String[] filters){
        Set<String> res = new HashSet<>();
        for (String filter : filters) {
            res.add(parsePredicate(filter).getColumn());
        }
        return res;
    }


}
