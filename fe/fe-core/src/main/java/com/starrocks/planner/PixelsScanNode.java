package com.starrocks.planner;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.starrocks.analysis.Analyzer;
import com.starrocks.analysis.DescriptorTable;
import com.starrocks.analysis.TupleDescriptor;
import com.starrocks.catalog.PixelsTable;
import com.starrocks.common.UserException;
import com.starrocks.connector.RemoteFileDesc;
import com.starrocks.connector.RemoteFileInfo;
import com.starrocks.connector.pixels.PixelsRemoteFileDesc;
import com.starrocks.qe.ConnectContext;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.server.RunMode;
import com.starrocks.server.WarehouseManager;
import com.starrocks.sql.plan.ExecPlan;
import com.starrocks.system.ComputeNode;
import com.starrocks.system.SystemInfoService;
import com.starrocks.thrift.TNetworkAddress;
import com.starrocks.thrift.TPixelsScanNode;
import com.starrocks.thrift.TPixelsScanRange;
import com.starrocks.thrift.TPlanNode;
import com.starrocks.thrift.TPlanNodeType;
import com.starrocks.thrift.TScanRange;
import com.starrocks.thrift.TScanRangeLocation;
import com.starrocks.thrift.TScanRangeLocations;
import io.pixelsdb.pixels.common.exception.MetadataException;
import io.pixelsdb.pixels.common.layout.ColumnSet;
import io.pixelsdb.pixels.common.layout.SplitsIndex;
import io.pixelsdb.pixels.common.metadata.MetadataService;
import io.pixelsdb.pixels.common.metadata.SchemaTableName;
import io.pixelsdb.pixels.common.metadata.domain.Layout;
import io.pixelsdb.pixels.common.metadata.domain.Ordered;
import io.pixelsdb.pixels.common.metadata.domain.Splits;
import io.pixelsdb.pixels.common.metadata.domain.Table;
import io.pixelsdb.pixels.common.physical.Location;
import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.pixelsdb.pixels.planner.PixelsPlanner.getFilePaths;

public class PixelsScanNode extends ScanNode {

    private static final Logger LOG = LogManager.getLogger(PixelsScanNode.class);
    private final Random random = new Random(System.currentTimeMillis());
    private Multimap<String, ComputeNode> nodeMap;
    private List<ComputeNode> nodeList;
    private final PixelsTable pixelsTable;
    private final List<TScanRangeLocations> scanRangeLocationsList = new ArrayList<>();
    private final ConfigFactory configFactory = ConfigFactory.Instance();

    public PixelsScanNode(PlanNodeId id, TupleDescriptor desc, String planNodeName) {
        super(id, desc, planNodeName);
        this.pixelsTable = (PixelsTable) desc.getTable();
        try {
            assignNodes();
        } catch (UserException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(Analyzer analyzer) throws UserException {
        super.init(analyzer);

        // assignNodes();
    }

    public void assignNodes() throws UserException {
        nodeMap = HashMultimap.create();
        nodeList = Lists.newArrayList();

        List<ComputeNode> nodes;
        SystemInfoService systemInfoService = GlobalStateMgr.getCurrentState().getNodeMgr().getClusterInfo();
        if (RunMode.isSharedDataMode()) {
            WarehouseManager warehouseManager = GlobalStateMgr.getCurrentState().getWarehouseMgr();
            String warehouseName = WarehouseManager.DEFAULT_WAREHOUSE_NAME;
            if (ConnectContext.get() != null) {
                warehouseName = ConnectContext.get().getCurrentWarehouseName();
            }
            List<Long> computeNodeIds = warehouseManager.getAllComputeNodeIds(warehouseName);
            nodes = computeNodeIds.stream()
                    .map(id -> systemInfoService.getBackendOrComputeNode(id)).collect(Collectors.toList());
        } else {
            nodes = systemInfoService.backendAndComputeNodeStream().collect(Collectors.toList());
        }
        for (ComputeNode node : nodes) {
            if (node.isAlive()) {
                nodeMap.put(node.getHost(), node);
                nodeList.add(node);
            }
        }
        if (nodeMap.isEmpty()) {
            throw new UserException("No Alive backends or compute nodes");
        }
    }

    private TNetworkAddress getTNetworkAddressFromString(String hostPortString) {
        Objects.requireNonNull(hostPortString, "hostPortString is null");
        String portString = null;
        String host = "-1";
        int port;
        if (hostPortString.startsWith("[")) {
            Matcher matcher = Pattern.compile("^\\[(.*:.*)\\](?::(\\d*))?$").matcher(hostPortString);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid bracketed host/port: " + hostPortString);
            }

            host = matcher.group(1);
            portString = matcher.group(2);
        } else {
            port = hostPortString.indexOf(58);
            if (port >= 0 && hostPortString.indexOf(58, port + 1) == -1) {
                host = hostPortString.substring(0, port);
                portString = hostPortString.substring(port + 1);
            } else {
                host = hostPortString;
            }
        }

        port = -1;
        if (portString != null && portString.length() != 0) {
            if (portString.startsWith("+")) {
                throw new IllegalArgumentException("Unparseable port number: " + hostPortString);
            }

            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException var5) {
                throw new IllegalArgumentException("Unparseable port number: " + hostPortString);
            }

            if (!(port >= 0 && port <= 65535)) {
                throw new IllegalArgumentException("Port number out of range: " + hostPortString);
            }
        }

        return new TNetworkAddress(host, port);
    }

    private List<TNetworkAddress> toTNetworkAddress(List<Location> locations) {
        ImmutableList.Builder<TNetworkAddress> addressBuilder = ImmutableList.builder();
        for (Location location : locations)
        {
            for (String host : location.getHosts())
            {

                addressBuilder.add(getTNetworkAddressFromString(host));
            }
        }
        return addressBuilder.build();
    }

    public void setupScanRangeLocations(ExecPlan context){

        int nodeIndex = random.nextInt(nodeList.size());

        // get pixels layout
        List<RemoteFileInfo> fileInfos = GlobalStateMgr.getCurrentState().getMetadataMgr().getRemoteFileInfos(
                pixelsTable.getCatalogName(), pixelsTable, null, -1, null, null, -1);
        PixelsRemoteFileDesc pixelsRemoteFileDesc = (PixelsRemoteFileDesc) fileInfos.get(0).getFiles().get(0);
        String schemaName = pixelsTable.getDbName();
        String tableName = pixelsTable.getTableName();
        Table table = pixelsTable.getPixelsTable();
        List<Layout> layouts = pixelsRemoteFileDesc.getPixelsLayouts();
        Storage storage;
        // TODO: use pixels transId
        long pseudoTransId = context.getConnectContext().getQueryId().getMostSignificantBits();

        try {
            storage = StorageFactory.Instance().getStorage(pixelsTable.getPixelsTable().getStorageScheme());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // get splits from layout, currently only implement orderedPath
        boolean orderedPathEnabled = true;
        // TODO: add compactPath split
        boolean compactPathEnabled = false;

        // one layout correspond to one scanRange
        for (Layout layout : layouts)
        {
            // get index
            long version = layout.getVersion();
            SchemaTableName schemaTableName = new SchemaTableName(schemaName, tableName);
            Ordered ordered = layout.getOrdered();
            ColumnSet columnSet = new ColumnSet();
            for(String columnName: context.getColNames()) {
                columnSet.addColumn(columnName);
            }

            // get split size, currently fixed
            // TODO: add dynamic split schema
            int splitSize = 16;
            Splits splits = layout.getSplits();

            LOG.info("using pixels split size: " + splitSize);

            // TODO: add projectionReadEnabled branch

            long splitId = 0;
            // TODO: add cache branch

            try {
                // add splits in orderedPaths
                if (orderedPathEnabled) {
                    List<String> orderedFilePaths = getFilePaths(
                            layout.getOrderedPaths(), MetadataService.Instance());
                    int numPath = orderedFilePaths.size();
                    boolean multiSplitForOrdered = Boolean.parseBoolean(configFactory.getProperty("multi.split.for.ordered"));

                    for (int i = 0; i < numPath; ){
                        int firstPath = i;
                        List<String> paths = new ArrayList<>(multiSplitForOrdered ? splitSize : 1);
                        if (multiSplitForOrdered)
                        {
                            for (int j = 0; j < splitSize && i < numPath; ++j, ++i)
                            {
                                paths.add(orderedFilePaths.get(i));
                            }
                        } else
                        {
                            paths.add(orderedFilePaths.get(i++));
                        }

                        List<Location> locations = null;
                        try {
                            locations = storage.getLocations(orderedFilePaths.get(firstPath));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        List<TNetworkAddress> orderedAddresses = toTNetworkAddress(locations);

                        // addSplitScanRangeLocations
                        TScanRangeLocations scanRangeLocations = new TScanRangeLocations();
                        TPixelsScanRange pixelsScanRange = new TPixelsScanRange();

                        // pixelsScanRange.set(...)
                        pixelsScanRange.setSplit_id(splitId++);
                        pixelsScanRange.setSchema_name(schemaName);
                        pixelsScanRange.setTable_name(tableName);
                        pixelsScanRange.setStorage_schema(pixelsTable.getPixelsTable().getStorageScheme().name());
                        pixelsScanRange.setPaths(paths);
                        pixelsScanRange.setRg_starts( Collections.nCopies(paths.size(), 0));
                        pixelsScanRange.setRg_lengths( Collections.nCopies(paths.size(), 1));
                        pixelsScanRange.setCached(false);
                        pixelsScanRange.setEnsure_locality(storage.hasLocality());

                        List<String> desiredColumns = context.getColNames();

                        pixelsScanRange.setColumn_order(desiredColumns);
                        pixelsScanRange.setCache_order(new ArrayList<>(0));

                        List<String> columnTypeOrder = new ArrayList<>();
                        for (String columnName : desiredColumns) {
                            columnName = pixelsTable.getPixelsColumnType(columnName);
                            if (columnName != null) {
                                columnTypeOrder.add(columnName);
                            }
                        }
                        pixelsScanRange.setColumn_type_order(columnTypeOrder);


                        // add attribute for pixels in thrift and set here
                        int numNode = Math.min(3, nodeList.size());
                        TScanRange scanRange = new TScanRange();
                        scanRange.setPixels_scan_range(pixelsScanRange);
                        scanRangeLocations.setScan_range(scanRange);

                        List<ComputeNode> candidateNodeList = Lists.newArrayList();
                        for (int k = 0; k < numNode; ++k) {
                            candidateNodeList.add(nodeList.get(nodeIndex++ % nodeList.size()));
                        }
                        for (int k = 0; k < numNode && k < candidateNodeList.size(); ++k) {
                            TScanRangeLocation scanRangeLocation = new TScanRangeLocation();
                            ComputeNode be = candidateNodeList.get(k);
                            scanRangeLocation.setBackend_id(be.getId());
                            scanRangeLocation.setServer(new TNetworkAddress(be.getHost(), be.getBePort()));
                            scanRangeLocations.addToLocations(scanRangeLocation);
                        }

                        scanRangeLocationsList.add(scanRangeLocations);
                    }

                }


            } catch (MetadataException e) {
                throw new RuntimeException(e);
            }

        }


    }

    @Override
    public List<TScanRangeLocations> getScanRangeLocations(long maxScanRangeLength) {
        return scanRangeLocationsList;
    }

    @Override
    protected void toThrift(TPlanNode msg) {
        msg.node_type = TPlanNodeType.PIXELS_SCAN_NODE;
        msg.pixels_scan_node = new TPixelsScanNode();
        msg.pixels_scan_node.setTuple_id(desc.getId().asInt());
        msg.pixels_scan_node.setTable_name(pixelsTable.getTableName());
    }

    @Override
    public int getNumInstances() {
        return scanRangeLocationsList.size();
    }
}
