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

// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/catalog/EsTable.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.catalog;

import com.starrocks.analysis.DescriptorTable;
import com.starrocks.connector.ColumnTypeConverter;
import com.starrocks.thrift.TPixelsTable;
import com.starrocks.thrift.TTableDescriptor;
import com.starrocks.thrift.TTableType;
import org.apache.paimon.types.DataField;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.starrocks.connector.ConnectorTableId.CONNECTOR_ID_GENERATOR;

public class PixelsTable extends Table{

    private final String catalogName;
    private final String databaseName;
    private final String tableName;
    private final io.pixelsdb.pixels.common.metadata.domain.Table pixelsTable;
    private final List<io.pixelsdb.pixels.common.metadata.domain.Column> columns;

    public PixelsTable(String catalogName, String dbName, String tblName, List<Column> schema,
                       io.pixelsdb.pixels.common.metadata.domain.Table pixelsTable, List<io.pixelsdb.pixels.common.metadata.domain.Column> columns) {
        super(CONNECTOR_ID_GENERATOR.getNextId().asInt(), tblName, TableType.PIXELS, schema);
        this.catalogName = catalogName;
        this.databaseName = dbName;
        this.tableName = tblName;
        this.pixelsTable = pixelsTable;
        this.columns = columns;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getCatalogName() {
        return catalogName;
    }

    public String getPixelsColumnType(String columnName) {
        for(io.pixelsdb.pixels.common.metadata.domain.Column column: this.columns) {
            if(column.getName().equals(columnName)) {
                return column.getType();
            }
        }
        return null;
    }

    public String getDbName() {
        return databaseName;
    }

    public String getTableName() {
        return tableName;
    }

    public io.pixelsdb.pixels.common.metadata.domain.Table getPixelsTable() { return pixelsTable; }

    @Override
    public TTableDescriptor toThrift(List<DescriptorTable.ReferencedPartitionInfo> partitions) {
        TPixelsTable tPixelsTable = new TPixelsTable();
        TTableDescriptor tTableDescriptor = new TTableDescriptor(getId(), TTableType.PIXELS_TABLE,
                fullSchema.size(), 0, getName(), "");

        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();

        for (io.pixelsdb.pixels.common.metadata.domain.Column column: columns){
            String name = column.getName();
            String type = column.getType();
            columnNames.add(name);
            columnTypes.add(type);
        }

        tPixelsTable.setPixels_column_names(columnNames);
        tPixelsTable.setPixels_column_types(columnTypes);

        tTableDescriptor.setPixelsTable(tPixelsTable);

        return tTableDescriptor;
    }
}
