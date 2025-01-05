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

import com.starrocks.jni.connector.ScannerHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.core.PixelsFooterCache;
import io.pixelsdb.pixels.core.PixelsReader;
import io.pixelsdb.pixels.core.PixelsReaderImpl;
import io.pixelsdb.pixels.core.reader.PixelsReaderOption;
import io.pixelsdb.pixels.core.reader.PixelsRecordReader;
import io.pixelsdb.pixels.core.utils.Bitmap;
import io.pixelsdb.pixels.core.vector.ColumnVector;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;
import io.pixelsdb.pixels.executor.predicate.TableScanFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PixelsSplitScanner {

    private static final Logger LOG = LogManager.getLogger(PixelsSplitScanner.class);
    private final int BatchSize;
    private final ClassLoader classLoader;
    private final Storage storage;
    private PixelsReader pixelsReader;
    private PixelsRecordReader recordReader;
    private PixelsReaderOption option;
    private final String[] includeCols;
    private final String[] paths;
    private final String[] colTypes;
    private final int numColumnToRead;

    private final CompletableFuture<?> blocked;
    private final Optional<TableScanFilter> filter;
    private final Bitmap filtered;
    private final Bitmap tmp;
    private int pathIndex = 0;
    private boolean closed = false;
    private int batchId = 0;
    private int resultNumRows = 0;

    public PixelsSplitScanner(int fetchSize, Map<String, String> params) {
        this.BatchSize = fetchSize;
        this.classLoader = this.getClass().getClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(Storage.class.getClassLoader()); // ensure serviceloader correctly find class in Storage.Instance()
            this.storage = StorageFactory.Instance().getStorage(params.get("storage_schema"));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String[] paths = ScannerHelper.splitAndOmitEmptyStrings(params.get("paths"), ",");
        this.paths = paths;
        this.colTypes = ScannerHelper.splitAndOmitEmptyStrings(params.get("required_column_types"), ",");

        String[] scanColumns = ScannerHelper.splitAndOmitEmptyStrings(params.get("required_fields"), ",");
        String schemaName = params.get("schema_name");
        String tableName = params.get("table_name");
        String[] filters = ScannerHelper.splitAndOmitEmptyStrings(params.get("filters"), "&");
        String[] filterColumns = PixelsPredicateParser.getFilterColumnNames(filters).toArray(new String[0]);

        List<String> mergeList = Arrays.asList(scanColumns);
        for (String filterColumn : filterColumns) {
            if(!mergeList.contains(filterColumn)) {
                mergeList.add(filterColumn);
            }
        }

        this.includeCols = mergeList.toArray(new String[0]);
        this.numColumnToRead = includeCols.length;
        // TODO: add filter/predicate(constraint)

        String[] columnNames = ScannerHelper.splitAndOmitEmptyStrings(params.get("column_names"), ",");
        String[] columnTypes = ScannerHelper.splitAndOmitEmptyStrings(params.get("column_types"), "&");

        Map<String, String> colNameToType = new HashMap<>();
        for (int i = 0; i < columnNames.length; ++i) {
            colNameToType.put(columnNames[i], columnTypes[i]);
        }

        this.filter = Optional.empty();
        this.filtered = new Bitmap(this.BatchSize, true);
        this.tmp = new Bitmap(this.BatchSize, false);

        readFirstPath();
        this.blocked = CompletableFuture.completedFuture((Object)null);
    }

    public synchronized void close()
    {
        if (closed)
        {
            return;
        }

        closeReader();

        closed = true;
    }

    private void closeReader()
    {
        try
        {
            if (pixelsReader != null)
            {
                if (recordReader != null)
                {

                }
                pixelsReader.close();
                /**
                 * PIXELS-114:
                 * Must set pixelsReader and recordReader to null,
                 * close() may be called multiple times by Presto.
                 */
                recordReader = null;
                pixelsReader = null;
            }
        } catch (Exception e)
        {
            LOG.error("close error: " + e.getMessage(), e);
            throw new RuntimeException();
        }
    }

    public String getPath() {
        return this.paths[pathIndex];
    }

    public boolean nextPath()
    {
        if (this.pathIndex+1 < this.paths.length)
        {
            this.pathIndex++;
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean hasNext() {
        return this.pathIndex < this.paths.length;
    }

    private void readFirstPath() {
        this.option = new PixelsReaderOption();
        this.option.skipCorruptRecords(true);
        this.option.tolerantSchemaEvolution(true);
        this.option.enableEncodedColumnVector(true);
        this.option.includeCols(includeCols);
//      TODO:  this.option.rgRange(split.getRgStart(), split.getRgLength());
        this.option.rgRange(0, 1);

        // TODO: add predicate

        try
        {
            if (this.storage != null)
            {
                // TODO: add cache
                this.pixelsReader = PixelsReaderImpl
                        .newBuilder()
                        .setStorage(this.storage)
                        .setPath(this.getPath())
                        .setEnableCache(false)
                        .setPixelsFooterCache(new PixelsFooterCache())
                        .build();
                if (this.pixelsReader.getRowGroupNum() <= this.option.getRGStart())
                {
                    /**
                     * As PixelsSplitManager does not check the exact number of row groups
                     * in the file, the start row group index might be invalid. in this case,
                     * we can simply close this page source.
                     */
                    this.close();
                } else
                {
                    this.recordReader = this.pixelsReader.read(this.option);
                }
            } else
            {
                LOG.error("pixelsReader error: storage handler is null");
                throw new IOException("pixelsReader error: storage handler is null.");
            }
        } catch (IOException e)
        {
            LOG.error("pixelsReader error: " + e.getMessage(), e);
            System.out.println("pixelsReader error: " + e.getMessage());
            throw new RuntimeException();
        }
        catch (Exception e) {
            LOG.error("pixelsReader error: " + e.getMessage(), e);
            System.out.println("pixelsReader error: " + e.getMessage());
            throw new RuntimeException();
        }

    }

    private synchronized boolean readNextPath () {
        try
        {
            if (this.nextPath())
            {
                closeReader();
                if (this.storage != null)
                {
                    this.pixelsReader = PixelsReaderImpl
                            .newBuilder()
                            .setStorage(this.storage)
                            .setPath(getPath())
                            .setEnableCache(false)
                            .setPixelsFooterCache(new PixelsFooterCache())
                            .build();
//                  TODO: this.option.rgRange(split.getRgStart(), split.getRgLength());
                    this.option.rgRange(0, 1);
                    if (this.pixelsReader.getRowGroupNum() <= this.option.getRGStart())
                    {
                        /**
                         * As PixelsSplitManager does not check the exact number of row groups
                         * in the file, the start row group index might be invalid. In this case,
                         * we can simply return false, and the page source will be closed outside.
                         */
                        return false;
                    }
                    this.recordReader = this.pixelsReader.read(this.option);
                } else
                {
                    LOG.error("pixelsReader error: storage handler is null");
                    throw new IOException("pixelsReader error: storage handler is null");
                }
                return true;
            } else
            {
                return false;
            }
        } catch (Exception e)
        {
            LOG.error("pixelsReader error: " + e.getMessage(), e);
            throw new RuntimeException();
        }
    }

    public List<Object[]> getNextChunk() throws Exception {
        if (!this.blocked.isDone())
        {
            return null;
        }
        if (this.blocked.isCancelled())
        {
            this.close();
        }

        if (this.closed)
        {
            return null;
        }

        this.batchId++;
        VectorizedRowBatch rowBatch = null;
        int rowBatchSize = 0;

        List<Object[]> resultChunk = new ArrayList<>(this.numColumnToRead);

        if (this.numColumnToRead > 0)
        {
            try
            {
                do
                {
                    rowBatch = recordReader.readBatch(BatchSize, false);
                    if (rowBatch.size <= 0)
                    {
                        if (readNextPath())
                        {
                            return getNextChunk();
                        } else
                        {
                            this.close();
                            return null;
                        }
                    }

                    if (this.filter.isPresent())
                    {
                        this.filter.get().doFilter(rowBatch, this.filtered, this.tmp);
                        rowBatch.applyFilter(this.filtered);
                    }
                    rowBatchSize = rowBatch.size;
                } while (rowBatchSize <= 0);

                for (int fieldId = 0; fieldId < numColumnToRead; ++fieldId)
                {

                    ColumnVector vector = rowBatch.cols[fieldId];

                    Object[] dataColumn = PixelsScannerUtils.getObjectArrayFromPixelsVector(vector, rowBatchSize);

                    this.resultNumRows = rowBatchSize;
                    resultChunk.add(dataColumn);
                }
            } catch (IOException e)
            {
                throw new RuntimeException("read row batch error.");
            }
        }
        else
        {
            // No column to read.
            try
            {
                rowBatchSize = this.recordReader.prepareBatch(BatchSize);
                if (rowBatchSize <= 0)
                {
                    if (readNextPath())
                    {
                        return getNextChunk();
                    } else
                    {
                        this.close();
                        return null;
                    }
                }
            } catch (IOException e)
            {
               throw new RuntimeException("prepare row batch error.");
            }
        }
        return resultChunk;
    }

    public int getResultNumRows() {
        return this.resultNumRows;
    }


}
