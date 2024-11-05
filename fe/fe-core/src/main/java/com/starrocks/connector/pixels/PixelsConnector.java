package com.starrocks.connector.pixels;

import com.starrocks.connector.Connector;
import com.starrocks.connector.ConnectorContext;
import com.starrocks.connector.ConnectorMetadata;

public class PixelsConnector implements Connector {

    public PixelsConnector(ConnectorContext context){

    }
    @Override
    public ConnectorMetadata getMetadata() {
        return null;
    }
}
