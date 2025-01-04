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

#pragma once

#include <utility>

#include "column/chunk.h"
#include "column/vectorized_fwd.h"
#include "common/object_pool.h"
#include "common/status.h"
#include "exec/olap_common.h"
#include "exec/pipeline/scan/scan_operator.h"
#include "exprs/expr_context.h"
#include "jni.h"
#include "runtime/descriptors.h"
#include "runtime/mem_tracker.h"
#include "runtime/runtime_state.h"
#include "types/logical_type.h"
#include "udf/java/java_udf.h"

namespace starrocks {

class Status;

class PixelsScanner {

public:
    // add consturction as pixelsReader
    PixelsScanner(const TupleDescriptor* tuple_desc, const TPixelsScanRange& pixels_scan_range, const TPixelsScanNode& pixels_scan_node)
        :_tuple_desc(tuple_desc), _slot_descs(tuple_desc->slots()), _scan_range(pixels_scan_range), _scan_node(pixels_scan_node) {}

    static Status _check_jni_exception(JNIEnv* env, const std::string& message);

    Status update_jni_scanner_params();

    Status open(RuntimeState* state);

    Status close();

    Status get_next(RuntimeState* state, ChunkPtr* chunk, bool* eos);

private:

    Status _has_next(bool* result);

    Status _init_column_name(RuntimeState* state);

    Status _init_pixels_table_scanner(JNIEnv* env, RuntimeState* runtime_state);

    Status _get_next_chunk(jobject* chunk, size_t* num_rows);

    Status _fill_chunk(jobject jchunk, size_t num_rows, ChunkPtr* chunk);

    jclass _jni_scanner_cls = nullptr;
    jobject _jni_scanner_obj = nullptr;

    const TPixelsScanRange& _scan_range;

    const TPixelsScanNode& _scan_node;

    const TupleDescriptor* _tuple_desc;

    std::vector<SlotDescriptor*> _slot_descs;

    std::map<std::string, std::string> _jni_scanner_params;

    const std::set<std::string> _skipped_log_jni_scanner_params = {"native_table", "split_info", "predicate_info", "access_id",    "access_key", "read_session"};

    jmethodID _scanner_has_next;
    jmethodID _scanner_get_next_chunk;
    jmethodID _scanner_result_rows;

    std::vector<ExprContext*> _cast_exprs;
    ChunkPtr _result_chunk;

    std::vector<LogicalType> _result_column_types;

    ObjectPool _pool;
};

} // starrocks


