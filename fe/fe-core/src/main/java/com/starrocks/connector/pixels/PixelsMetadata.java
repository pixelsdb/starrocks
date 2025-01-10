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

package com.starrocks.connector.pixels;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.HiveMetaStoreTable;
import com.starrocks.catalog.HiveTable;
import com.starrocks.catalog.PartitionKey;
import com.starrocks.catalog.PixelsTable;
import com.starrocks.catalog.Table;
import com.starrocks.catalog.Type;
import com.starrocks.common.AlreadyExistsException;
import com.starrocks.common.DdlException;
import com.starrocks.common.MetaNotFoundException;
import com.starrocks.connector.ColumnTypeConverter;
import com.starrocks.connector.ConnectorMetadata;
import com.starrocks.connector.RemoteFileDesc;
import com.starrocks.connector.RemoteFileInfo;
import com.starrocks.connector.exception.StarRocksConnectorException;
import com.starrocks.connector.paimon.PaimonRemoteFileDesc;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.ast.CreateTableStmt;
import com.starrocks.sql.ast.DropTableStmt;
import com.starrocks.sql.optimizer.OptimizerContext;
import com.starrocks.sql.optimizer.operator.scalar.ColumnRefOperator;
import com.starrocks.sql.optimizer.operator.scalar.ScalarOperator;
import com.starrocks.sql.optimizer.statistics.ColumnStatistic;
import com.starrocks.sql.optimizer.statistics.Statistics;
import com.starrocks.statistic.StatisticUtils;
import io.pixelsdb.pixels.common.exception.MetadataException;
import io.pixelsdb.pixels.common.metadata.domain.Column;
import io.pixelsdb.pixels.common.metadata.domain.Layout;
import io.pixelsdb.pixels.common.metadata.domain.Schema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.pixelsdb.pixels.common.metadata.MetadataService;
import org.apache.paimon.table.source.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.starrocks.catalog.Table.TableType.PIXELS;
import static com.starrocks.connector.ConnectorTableId.CONNECTOR_ID_GENERATOR;
import static com.starrocks.server.CatalogMgr.ResourceMappingCatalog.isResourceMappingCatalog;

public class PixelsMetadata implements ConnectorMetadata {
    private static final Logger LOG = LogManager.getLogger(PixelsMetadata.class);
    private final String catalogName;
    private final MetadataService metadataService;
    private final Map<String, Table> tables = new ConcurrentHashMap<>();
    private final Map<String, Database> databases = new ConcurrentHashMap<>();
    private static final String DATABASE_TABLE_JOINER = ".";

    public PixelsMetadata(String catalogName){
        this.catalogName = catalogName;
        this.metadataService = MetadataService.Instance();
    }

    @Override
    public Table.TableType getTableType() {
        return PIXELS;
    }

    @Override
    public List<String> listDbNames() {
        List<String> schemaList = new ArrayList<String>();
        try {
            List<Schema> schemas = metadataService.getSchemas();
            for (Schema s : schemas) {
                schemaList.add(s.getName());
            }
        }
        catch (MetadataException e){
            throw new RuntimeException(e);
        }

        return schemaList;
    }

    @Override
    public void createDb(String dbName, Map<String, String> properties) throws AlreadyExistsException {
        if (dbExists(dbName)) {
            throw new AlreadyExistsException("Database Already Exists");
        }
        try{
            metadataService.createSchema(dbName);
        }
        catch (MetadataException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dropDb(String dbName, boolean isForceDrop) throws MetaNotFoundException {
        if (listTableNames(dbName).size() != 0) {
            throw new StarRocksConnectorException("Database %s not empty", dbName);
        }
        try{
            metadataService.dropSchema(dbName);
        }
        catch (MetadataException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    // could add cache
    public Database getDb(String dbName) {

        if (databases.containsKey(dbName)) {
            return databases.get(dbName);
        }

        try {
            if(!metadataService.existSchema(dbName)){
                throw new MetadataException("Failed to get pixels database " + catalogName + "." + dbName);
            }
            Database db = new Database(CONNECTOR_ID_GENERATOR.getNextId().asInt(), dbName);
            databases.put(dbName, db);
            return db;

        } catch (MetadataException e) {
            LOG.error("Failed to get pixels database [{}.{}]", catalogName, dbName, e);
            return null;
        }

    }

    @Override
    public List<String> listTableNames(String dbName) {

        List<String> tableList = new ArrayList<String>();
        try {
            List<io.pixelsdb.pixels.common.metadata.domain.Table> tables = metadataService.getTables(dbName);
            for (io.pixelsdb.pixels.common.metadata.domain.Table t : tables) {
                tableList.add(t.getName());
            }
        }
        catch (MetadataException e){
            throw new RuntimeException(e);
        }

        return tableList;
    }

//    @Override
//    public boolean createTable(CreateTableStmt stmt) throws DdlException {
//        metadataService.createTable(stmt.getDbName(), stmt.getTableName());
//    }

    @Override
    public void dropTable(DropTableStmt stmt) throws DdlException {
        String dbName = stmt.getDbName();
        String tableName = stmt.getTableName();

        io.pixelsdb.pixels.common.metadata.domain.Table  pixelsTable = null;

        try {
            pixelsTable = metadataService.getTable(dbName, tableName);
        } catch (Exception e) {
            // ignore not found exception
        }
        if (pixelsTable == null && stmt.isSetIfExists()) {
            LOG.warn("Table {}.{} doesn't exist", dbName, tableName);
            return;
        }

        try {
            metadataService.dropTable(dbName, tableName);
        } catch (MetadataException e) {
            throw new RuntimeException(e);
        }

    }

    // could add cache
    @Override
    public Table getTable(String dbName, String tblName) {

        String fullTableName = getPixelsFullTableName(dbName, tblName);

        if(tables.containsKey(fullTableName)){
            return tables.get(fullTableName);
        }

        io.pixelsdb.pixels.common.metadata.domain.Table pixelsTable = null;
        List<Column> columns = null;
        try {
            pixelsTable = metadataService.getTable(dbName, tblName, true);
            columns = metadataService.getColumns(dbName, tblName, false);
        } catch (MetadataException e) {
            LOG.error("Pixels table {}.{} does not exist.", dbName, tblName, e);
            throw new RuntimeException(e);
        }

        List<com.starrocks.catalog.Column> fullSchema = new ArrayList<>(columns.size());
        for (Column column: columns){
            String name = column.getName();
            String type = column.getType();
            Type starrocksType = ColumnTypeConverter.fromPixelsType(type);
            com.starrocks.catalog.Column starrocksColumn = new com.starrocks.catalog.Column(name, starrocksType);
            fullSchema.add(starrocksColumn);
        }
        PixelsTable table = new PixelsTable(this.catalogName, dbName, tblName, fullSchema, pixelsTable, columns);
        tables.put(fullTableName, table);
        return table;

    }

    @Override
    public boolean tableExists(String dbName, String tblName) {
        try {
            return metadataService.existTable(dbName, tblName);
        } catch (MetadataException e) {
            throw new RuntimeException(e);
        }
    }

    // to be complete
    @Override
    public List<RemoteFileInfo> getRemoteFileInfos(Table table, List<PartitionKey> partitionKeys, long snapshotId,
                                                   ScalarOperator predicate, List<String> fieldNames, long limit) {
        RemoteFileInfo remoteFileInfo = new RemoteFileInfo();
        PixelsTable pixelsTable = (PixelsTable) table;
        List<Layout> pixelsLayouts;
        try {
            pixelsLayouts = metadataService.getLayouts(pixelsTable.getDbName(), pixelsTable.getTableName());
        } catch (MetadataException e) {
            throw new RuntimeException(e);
        }
        List<RemoteFileDesc> remoteFileDescs = ImmutableList.of(
                PixelsRemoteFileDesc.createPixelsRemoteFileDesc(pixelsLayouts));
        remoteFileInfo.setFiles(remoteFileDescs);
        return Lists.newArrayList(remoteFileInfo);
    }

    // to be complemented for optimizer
    @Override
    public Statistics getTableStatistics(OptimizerContext session, Table table,
                                         Map<ColumnRefOperator, com.starrocks.catalog.Column> columns,
                                         List<PartitionKey> partitionKeys, ScalarOperator predicate, long limit) {
        Statistics.Builder builder = Statistics.builder();
        for (ColumnRefOperator columnRefOperator : columns.keySet()) {
            builder.addColumnStatistic(columnRefOperator, ColumnStatistic.unknown());
        }

        long rowCount = ((PixelsTable) table).getPixelsTable().getRowCount();
        if (rowCount == 0) {
            builder.setOutputRowCount(1);
        } else {
            builder.setOutputRowCount(rowCount);
        }

        return builder.build();
    }

    private String getPixelsFullTableName(String dbName, String tblName) {
        return dbName + DATABASE_TABLE_JOINER + tblName;
    }


}
