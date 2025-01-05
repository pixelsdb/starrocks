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

#include "connector/pixels_connector.h"
#include "exec/pixels_scanner.h"

namespace starrocks::connector {

DataSourceProviderPtr PixelsConnector::create_data_source_provider(ConnectorScanNode* scan_node,
                                                               const TPlanNode& plan_node) const {
    return std::make_unique<PixelsDataSourceProvider>(scan_node, plan_node);
}

PixelsDataSourceProvider::PixelsDataSourceProvider(ConnectorScanNode* scan_node, const TPlanNode& plan_node)
        : _scan_node(scan_node), _pixels_scan_node(plan_node.pixels_scan_node) {}

DataSourcePtr PixelsDataSourceProvider::create_data_source(const TScanRange& scan_range) {
    return std::make_unique<PixelsDataSource>(this, scan_range);
}

const TupleDescriptor* PixelsDataSourceProvider::tuple_descriptor(RuntimeState* state) const {
    return state->desc_tbl().get_tuple_descriptor(_pixels_scan_node.tuple_id);
}

PixelsDataSource::PixelsDataSource(const PixelsDataSourceProvider* provider, const TScanRange& scan_range)
        : _provider(provider), _scan_range(scan_range.pixels_scan_range) {}

std::string PixelsDataSource::name() const {
    return "PixelsDataSource";
}

Status PixelsDataSource::open(RuntimeState* state) {
    const TPixelsScanNode& pixels_scan_node = _provider->_pixels_scan_node;
        _runtime_state = state;
        _tuple_desc = state->desc_tbl().get_tuple_descriptor(pixels_scan_node.tuple_id);
        RETURN_IF_ERROR(_create_scanner());
        return Status::OK();
}

Status PixelsDataSource::_create_scanner() {
    const TPixelsScanNode& pixels_scan_node = _provider->_pixels_scan_node; // add filters from pixels_scan_node.filters
    _pixels_scanner = _pool->add(new PixelsScanner(_tuple_desc, _scan_range, pixels_scan_node));
    RETURN_IF_ERROR(_pixels_scanner->open(_runtime_state));
    return Status::OK();
}

void PixelsDataSource::close(RuntimeState* state) {
    if (_pixels_scanner != nullptr) {
        WARN_IF_ERROR(_pixels_scanner->close(), "close pixels reader failed");
    }
}

Status PixelsDataSource::get_next(RuntimeState* state, ChunkPtr* chunk) {
    bool eos = false;
    RETURN_IF_ERROR(_init_chunk_if_needed(chunk, 0));
    do {
        RETURN_IF_ERROR(_pixels_scanner->get_next(state, chunk, &eos));
    } while (!eos && (*chunk)->num_rows() == 0);
    if (eos) {
        return Status::EndOfFile("");
    }
    _rows_read += (*chunk)->num_rows();
    _bytes_read += (*chunk)->bytes_usage();
    return Status::OK();
}

int64_t PixelsDataSource::raw_rows_read() const {
    return _rows_read;
}
int64_t PixelsDataSource::num_rows_read() const {
    return _rows_read;
}
int64_t PixelsDataSource::num_bytes_read() const {
    return _bytes_read;
}
int64_t PixelsDataSource::cpu_time_spent() const {
    // TODO: calculte the real cputime
    return 0;
}

}