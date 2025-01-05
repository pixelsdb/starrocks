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

import io.pixelsdb.pixels.core.vector.BinaryColumnVector;
import io.pixelsdb.pixels.core.vector.ColumnVector;
import io.pixelsdb.pixels.core.vector.DateColumnVector;
import io.pixelsdb.pixels.core.vector.DecimalColumnVector;
import io.pixelsdb.pixels.core.vector.DictionaryColumnVector;
import io.pixelsdb.pixels.core.vector.DoubleColumnVector;
import io.pixelsdb.pixels.core.vector.FloatColumnVector;
import io.pixelsdb.pixels.core.vector.LongColumnVector;

import java.time.LocalDate;

public class PixelsScannerUtils {

    public static String getTypeKeyword(String type) {
        String keyword = type;
        int parenthesesIndex;
        if ((parenthesesIndex = keyword.indexOf('<')) >= 0) {
            keyword = keyword.substring(0, parenthesesIndex).trim();
        } else if ((parenthesesIndex = keyword.indexOf('(')) >= 0) {
            keyword = keyword.substring(0, parenthesesIndex).trim();
        }
        return keyword;
    }

    private static int convertToDate(int year, int month, int day) {
        int century;
        int julianDate;

        if (month > 2) {
            month += 1;
            year += 4800;
        } else {
            month += 13;
            year += 4799;
        }
        century = year / 100;
        julianDate = year * 365 - 32167;
        julianDate += year / 4 - century + century / 4;
        julianDate += 7834 * month / 256 + day;

        return julianDate;
    }

    public static Object[] getObjectArrayFromPixelsVector(ColumnVector columnVector, int rowBatchSize) {
        Object[] dataColumn = null;

        if (columnVector instanceof LongColumnVector) {
            LongColumnVector bigIntVector = (LongColumnVector) columnVector;
            dataColumn = new Long[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                dataColumn[i] = bigIntVector.vector[i];
            }
            return dataColumn;
        }
        else if (columnVector instanceof BinaryColumnVector) {
            BinaryColumnVector binaryVector =(BinaryColumnVector) columnVector;
            dataColumn = new String[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                dataColumn[i] = binaryVector.toString(i);
            }
            return dataColumn;
        }
        else if (columnVector instanceof DictionaryColumnVector) {
            DictionaryColumnVector dictionaryVector =(DictionaryColumnVector) columnVector;
            dataColumn = new String[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                dataColumn[i] = dictionaryVector.toString(i);
            }
            return dataColumn;
        }
        else if (columnVector instanceof FloatColumnVector) {
            FloatColumnVector floatVector =(FloatColumnVector) columnVector;
            dataColumn = new Float[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                dataColumn[i] = Float.intBitsToFloat(floatVector.vector[i]);
            }
            return dataColumn;
        }
        else if (columnVector instanceof DoubleColumnVector) {
            DoubleColumnVector doubleVector =(DoubleColumnVector) columnVector;
            dataColumn = new Double[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                dataColumn[i] = Double.longBitsToDouble(doubleVector.vector[i]);
            }
            return dataColumn;
        }
        else if (columnVector instanceof DateColumnVector) {
            DateColumnVector dateVector = (DateColumnVector) columnVector;
            dataColumn = new Integer[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                LocalDate date = LocalDate.ofEpochDay(dateVector.getDate(i));
                dataColumn[i] = convertToDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            }
            return dataColumn;
        }
        // 相应格式转换，注意各种enum type
        else if (columnVector instanceof DecimalColumnVector) {
            DecimalColumnVector decimalVector = (DecimalColumnVector) columnVector;
            dataColumn = new Long[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                dataColumn[i] = decimalVector.vector[i];
            }
            return dataColumn;
        }


        return dataColumn;
    }

}
