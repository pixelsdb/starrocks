package com.starrocks.pixels.reader;

import io.pixelsdb.pixels.core.encoding.Dictionary;
import io.pixelsdb.pixels.core.vector.BinaryColumnVector;
import io.pixelsdb.pixels.core.vector.ByteColumnVector;
import io.pixelsdb.pixels.core.vector.ColumnVector;
import io.pixelsdb.pixels.core.vector.DateColumnVector;
import io.pixelsdb.pixels.core.vector.DecimalColumnVector;
import io.pixelsdb.pixels.core.vector.DictionaryColumnVector;
import io.pixelsdb.pixels.core.vector.DoubleColumnVector;
import io.pixelsdb.pixels.core.vector.FloatColumnVector;
import io.pixelsdb.pixels.core.vector.LongColumnVector;

import java.math.BigDecimal;
import java.sql.Date;

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

    public static Object[] getObjectArrayFromPixelsVector(ColumnVector columnVector, String type, int rowBatchSize) {
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
            dataColumn = new String[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                StringBuilder buffer = new StringBuilder();
                dateVector.stringifyValue(buffer, i);
                dataColumn[i] = buffer.toString();
            }
            return dataColumn;
        }
        // 相应格式转换，注意各种enum type
        else if (columnVector instanceof DecimalColumnVector) {
            DecimalColumnVector decimalVector = (DecimalColumnVector) columnVector;
            dataColumn = new String[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                StringBuilder buffer = new StringBuilder();
                decimalVector.stringifyValue(buffer, i);
                dataColumn[i] = buffer.toString();
            }
            return dataColumn;
        }

//        String typeUpperCase = getTypeKeyword(type).toUpperCase();
//
//        switch (typeUpperCase) {
//            case "TINYINT":
//
//            case "SMALLINT":
//
//            case "INT":
//            case "INTEGER":
//            case "BIGINT":
//                LongColumnVector bigIntVector = (LongColumnVector) columnVector;
//                dataColumn = new Long[rowBatchSize];
//                for(int i = 0; i< rowBatchSize; ++i) {
//                    dataColumn[i] = bigIntVector.vector[i];
//                }
//                return dataColumn;
//
//            case "FLOAT":
//
//            case "DOUBLE":
//            case "DOUBLE PRECISION":
//
//            case "DECIMAL":
//            case "NUMERIC":
//
//            case "TIMESTAMP":
//
//            case "DATE":
//                break;
//            case "STRING":
//            case "VARCHAR":
//            case "CHAR":
//            case "BINARY":
//                BinaryColumnVector binaryVector =(BinaryColumnVector) columnVector;
//                dataColumn = new String[rowBatchSize];
//                for(int i = 0; i< rowBatchSize; ++i) {
//                    dataColumn[i] = binaryVector.toString(i);
//                }
//                return dataColumn;
//
//            case "BOOLEAN":
//
//            case "ARRAY":
//
//            case "MAP":
//
//            case  "STRUCT":
//
//            default:
//
//                break;
//        }

        return dataColumn;
    }

}
