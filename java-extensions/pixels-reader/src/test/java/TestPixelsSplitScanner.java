// Copyright 2021-present StarRocks, Inc. All rights reserved.
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

import com.starrocks.jni.connector.OffHeapTable;
import com.starrocks.pixels.reader.PixelsPredicateParser;
import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.core.PixelsFooterCache;
import io.pixelsdb.pixels.core.PixelsReaderImpl;
import io.pixelsdb.pixels.core.predicate.PixelsPredicate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

public class TestPixelsSplitScanner {

    @Test
    public void testPredicateParser() {
        String[] testQueries = {
                "age = 25",                    // 整数
                "price <= 199.99",              // 小数
                "weight > 1E3",                 // 科学计数法
                "birthdate = '2024-12-09'",     // 日期
                "name like 'John%'",            // LIKE 操作符
                "temperature != 35.5",           // 小数不等于,
                "n_name = 'CHINA'",
                "col IN ('A', 'B')",
                "col IN (\"A\",\"B\")",
                "n_name in (\"CHINA\", \"JAN\")"
        };
        for (String testQuery : testQueries) {
            System.out.println(PixelsPredicateParser.parsePredicate(testQuery));
        }
        String predicate = "7: l_discount <= 0.07";
        PixelsPredicateParser.ParsedPredicate result = PixelsPredicateParser.parsePredicate(predicate);
        System.out.println(result);
    }

    @Test
    public void testArray() {
        String[] arr = {"a", "asd"};

        System.out.println(arr);
    }

    @Test
    public void runScan() throws IOException {
        try {


//            String currentDirectory = System.getProperty("user.dir");
//            System.out.println("当前执行路径：" + currentDirectory);
//            String classpath = System.getProperty("java.class.path");
//            System.out.println("Classpath: " + classpath);
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            System.out.println("Context ClassLoader: " + contextClassLoader);
            Storage storage = StorageFactory.Instance().getStorage("file");

            PixelsReaderImpl
                    .newBuilder()
                    .setStorage(storage)
                    .setPath("file:///data/9a3-10/pixles-tpch/nation/v-0-ordered/20241102014031_1058.pxl")
                    .setEnableCache(false)
                    .setPixelsFooterCache(new PixelsFooterCache())
                    .build();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
