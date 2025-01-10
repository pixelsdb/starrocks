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

#include "pixels_scanner.h"

#include <common/status.h>

#include "column/column_helper.h"
#include "column/column_viewer.h"
#include "column/nullable_column.h"
#include "column/vectorized_fwd.h"
#include "common/statusor.h"
#include "exprs/cast_expr.h"
#include "exprs/clone_expr.h"
#include "exprs/expr.h"
#include "exprs/expr_context.h"
#include "runtime/types.h"
#include "types/logical_type.h"
#include "udf/java/java_udf.h"
#include "util/defer_op.h"

namespace starrocks {

#define CHECK_JAVA_EXCEPTION(env, error_message)                                        \
if (jthrowable thr = env->ExceptionOccurred(); thr) {                               \
std::string err = JVMFunctionHelper::getInstance().dumpExceptionString(thr);    \
env->ExceptionClear();                                                          \
env->DeleteLocalRef(thr);                                                       \
return Status::InternalError(fmt::format("{}, error: {}", error_message, err)); \
}

Status PixelsScanner::_check_jni_exception(JNIEnv* env, const std::string& message) {
    if (jthrowable thr = env->ExceptionOccurred(); thr) {
        std::string jni_error_message = JVMFunctionHelper::getInstance().dumpExceptionString(thr);
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->DeleteLocalRef(thr);
        return Status::InternalError(message + " java exception details: " + jni_error_message);
    }
    return Status::OK();
}

Status PixelsScanner::_init_column_name(RuntimeState* state) {

    int len = _slot_descs.size();
    _result_chunk = std::make_shared<Chunk>();

    for(int i = 0; i < len; ++i) {
        LogicalType ret_type = _slot_descs[i]->type().type;
        _result_column_types.emplace_back(ret_type);
        TypeDescriptor intermediate;
        if(ret_type == TYPE_DECIMAL64) {
            int precision = _slot_descs[i]->type().precision;
            int scale = _slot_descs[i]->type().scale;
            // std::cout << "PRECISION: " << precision << ", SCALE: " << scale << std::endl;
            intermediate = TypeDescriptor::create_decimalv3_type(ret_type, precision, scale);
        }
        else {
            intermediate = TypeDescriptor(ret_type);
        }
        auto result_column = ColumnHelper::create_column(intermediate, true);
        _result_chunk->append_column(std::move(result_column), i);
        auto column_ref = _pool.add(new ColumnRef(intermediate, i));
        // TODO: add check cast status
        Expr* cast_expr = nullptr;
        if (ret_type != _slot_descs[i]->type().type) {
            cast_expr = VectorizedCastExprFactory::from_type(intermediate, _slot_descs[i]->type(), column_ref, &_pool,
                                                             true);
        } else {
            // clone to reuse result_chunk
            cast_expr = CloneExpr::from_child(column_ref, &_pool);
        }

        _cast_exprs.push_back(_pool.add(new ExprContext(cast_expr)));
    }
    RETURN_IF_ERROR(Expr::prepare(_cast_exprs, state));
    RETURN_IF_ERROR(Expr::open(_cast_exprs, state));

    return Status::OK();
}

// TODO: complete
Status PixelsScanner::update_jni_scanner_params() {
    // update materialized columns.

    const auto* pixels_table = dynamic_cast<const PixelsTableDescriptor*>(_tuple_desc->table_desc());

    std::string column_names;

    for (const auto& column_name : pixels_table->column_names()) {
        column_names.append(column_name);
        column_names.append(",");
    }
    if (!column_names.empty()) {
        column_names = column_names.substr(0, column_names.size() - 1);
    }

    _jni_scanner_params["column_names"] = column_names;

    std::string column_types;

    for (const auto& column_type : pixels_table->column_types()) {
        column_types.append(column_type);
        column_types.append("&");
    }
    if (!column_types.empty()) {
        column_types = column_types.substr(0, column_types.size() - 1);
    }

    _jni_scanner_params["column_types"] = column_types;

    _jni_scanner_params["storage_schema"] = _scan_range.storage_schema;
    _jni_scanner_params["schema_name"] = _scan_range.schema_name;
    _jni_scanner_params["table_name"] = _scan_range.table_name;

    std::string filters;

    for (const auto& filter : _scan_node.filters) {
        filters.append(filter);
        filters.append("&");
    }
    if (!filters.empty()) {
        filters = filters.substr(0, filters.size() - 1);
    }

    _jni_scanner_params["filters"] = filters;

    std::string required_fields;
    for (const auto& slot_desc : _slot_descs) {
        required_fields.append(slot_desc->col_name());
        required_fields.append(",");
    }
    if (!required_fields.empty()) {
        required_fields = required_fields.substr(0, required_fields.size() - 1);
    }

    _jni_scanner_params["required_fields"] = required_fields;

    std::string paths;
    for (const auto& path : _scan_range.paths) {
        paths.append(path);
        paths.append(",");
    }
    if (!paths.empty()) {
        paths = paths.substr(0, paths.size() - 1);
    }

    _jni_scanner_params["paths"] = paths;

    std::string required_column_types;
    for (const auto& type : _scan_range.column_type_order) {
        required_column_types.append(type);
        required_column_types.append(",");
    }
    if (!required_column_types.empty()) {
        required_column_types = required_column_types.substr(0, required_column_types.size() - 1);
    }

    _jni_scanner_params["required_column_types"] = required_column_types;

    return Status::OK();
}


Status PixelsScanner::open(RuntimeState* state) {
    RETURN_IF_ERROR(detect_java_runtime());
    RETURN_IF_ERROR(update_jni_scanner_params());

    auto& h = JVMFunctionHelper::getInstance();
    auto* env = h.getEnv();

    RETURN_IF_ERROR(_init_pixels_table_scanner(env, state));

    RETURN_IF_ERROR(_init_column_name(state));

    return Status::OK();
}

Status PixelsScanner::_has_next(bool* result) {
    auto* env = JVMFunctionHelper::getInstance().getEnv();
    jboolean ret = env->CallBooleanMethod(_jni_scanner_obj, _scanner_has_next);
    CHECK_JAVA_EXCEPTION(env, "call PixelsScanner hasNext failed")
    *result = ret;
    return Status::OK();
}

Status PixelsScanner::_init_pixels_table_scanner(JNIEnv* env, RuntimeState* runtime_state) {
    std::string _jni_scanner_factory_class = "com/starrocks/pixels/reader/PixelsSplitScannerFactory";
    jclass scanner_factory_class = env->FindClass(_jni_scanner_factory_class.c_str());
    jmethodID scanner_factory_constructor = env->GetMethodID(scanner_factory_class, "<init>", "()V");
    jobject scanner_factory_obj = env->NewObject(scanner_factory_class, scanner_factory_constructor);
    jmethodID get_scanner_method = env->GetMethodID(scanner_factory_class, "getScannerClass", "()Ljava/lang/Class;");
    _jni_scanner_cls = (jclass) env->CallObjectMethod(scanner_factory_obj, get_scanner_method);
    RETURN_IF_ERROR(_check_jni_exception(env, "Failed to init the scanner class."));
    env->DeleteLocalRef(scanner_factory_class);
    env->DeleteLocalRef(scanner_factory_obj);

    jmethodID scanner_constructor = env->GetMethodID(_jni_scanner_cls, "<init>", "(ILjava/util/Map;)V");
    RETURN_IF_ERROR(_check_jni_exception(env, "Failed to get a scanner class constructor."));

    jclass hashmap_class = env->FindClass("java/util/HashMap");
    jmethodID hashmap_constructor = env->GetMethodID(hashmap_class, "<init>", "(I)V");
    jobject hashmap_object = env->NewObject(hashmap_class, hashmap_constructor, _jni_scanner_params.size());
    jmethodID hashmap_put =
            env->GetMethodID(hashmap_class, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    RETURN_IF_ERROR(_check_jni_exception(env, "Failed to get the HashMap methods."));

    std::string message = "Initialize a scanner with parameters: ";
    for (const auto& it : _jni_scanner_params) {
        jstring key = env->NewStringUTF(it.first.c_str());
        jstring value = env->NewStringUTF(it.second.c_str());
        // skip encoded object
        if (_skipped_log_jni_scanner_params.find(it.first) == _skipped_log_jni_scanner_params.end()) {
            message.append(it.first);
            message.append("->");
            message.append(it.second);
            message.append(", ");
        }

        env->CallObjectMethod(hashmap_object, hashmap_put, key, value);
        env->DeleteLocalRef(key);
        env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(hashmap_class);
    LOG(INFO) << message;

    int fetch_size = runtime_state->chunk_size();
    _jni_scanner_obj = env->NewObject(_jni_scanner_cls, scanner_constructor, fetch_size, hashmap_object);
    env->DeleteLocalRef(hashmap_object);
    DCHECK(_jni_scanner_obj != nullptr);
    RETURN_IF_ERROR(_check_jni_exception(env, "Failed to initialize a scanner instance."));

    // init jmethod
    _scanner_has_next = env->GetMethodID(_jni_scanner_cls, "hasNext", "()Z");
    _scanner_get_next_chunk = env->GetMethodID(_jni_scanner_cls, "getNextChunk", "()Ljava/util/List;");
    _scanner_result_rows = env->GetMethodID(_jni_scanner_cls, "getResultNumRows", "()I");

    return Status::OK();
}

Status PixelsScanner::close() {
    JNIEnv* env = JVMFunctionHelper::getInstance().getEnv();
    if (_jni_scanner_obj != nullptr) {
        env->DeleteLocalRef(_jni_scanner_obj);
        _jni_scanner_obj = nullptr;
    }
    if (_jni_scanner_cls != nullptr) {
        env->DeleteLocalRef(_jni_scanner_cls);
        _jni_scanner_cls = nullptr;
    }
    return Status::OK();
}

Status PixelsScanner::get_next(RuntimeState* state, ChunkPtr* chunk, bool* eos) {
    jobject jchunk = nullptr;
    size_t jchunk_rows = 0;
    LOCAL_REF_GUARD(jchunk);
    RETURN_IF_ERROR(_get_next_chunk(&jchunk, &jchunk_rows));
    if(jchunk == NULL) {
        *eos = true;
        return Status::OK();
    }
    RETURN_IF_ERROR(_fill_chunk(jchunk, jchunk_rows, chunk));
    return Status::OK();
}

Status PixelsScanner::_get_next_chunk(jobject* chunk, size_t* num_rows) {
    auto* env = JVMFunctionHelper::getInstance().getEnv();
    *chunk = env->CallObjectMethod(_jni_scanner_obj, _scanner_get_next_chunk);
    CHECK_JAVA_EXCEPTION(env, "getNextChunk failed")
    *num_rows = env->CallIntMethod(_jni_scanner_obj, _scanner_result_rows);
    CHECK_JAVA_EXCEPTION(env, "getResultNumRows failed")
    return Status::OK();
}

Status PixelsScanner::_fill_chunk(jobject jchunk, size_t num_rows, ChunkPtr* chunk) {
    // get result from JNI
    {
        auto& helper = JVMFunctionHelper::getInstance();
        auto* env = helper.getEnv();

        (*chunk)->reset();

        for (size_t i = 0; i < _slot_descs.size(); i++) {
            jobject jcolumn = helper.list_get(jchunk, i);
            LOCAL_REF_GUARD_ENV(env, jcolumn);
            auto& result_column = _result_chunk->columns()[i];
            auto st =
                    helper.get_result_from_boxed_array(_result_column_types[i], result_column.get(), jcolumn, num_rows);
            RETURN_IF_ERROR(st);
            down_cast<NullableColumn*>(result_column.get())->update_has_null();
        }
    }

    for (size_t col_idx = 0; col_idx < _slot_descs.size(); col_idx++) {
        SlotDescriptor* slot_desc = _slot_descs[col_idx];
        // use reference, then we check the column's nullable and set the final result to the referred column.
        ColumnPtr& column = (*chunk)->get_column_by_slot_id(slot_desc->id());
        ASSIGN_OR_RETURN(auto result, _cast_exprs[col_idx]->evaluate(_result_chunk.get()));
        // unfold const_nullable_column to avoid error down_cast.
        // unpack_and_duplicate_const_column is not suitable, we need set correct type.
        result = ColumnHelper::unfold_const_column(slot_desc->type(), num_rows, result);
        if (column->is_nullable() == result->is_nullable()) {
            column = result;
        } else if (column->is_nullable() && !result->is_nullable()) {
            column = NullableColumn::create(result, NullColumn::create(num_rows));
        } else if (!column->is_nullable() && result->is_nullable()) {
            if (result->has_null()) {
                return Status::DataQualityError(
                        fmt::format("Unexpected NULL value occurs on NOT NULL column[{}]", slot_desc->col_name()));
            }
            column = down_cast<NullableColumn*>(result.get())->data_column();
        }
    }
    return Status::OK();
}

} // starrocks