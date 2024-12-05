package com.starrocks.pixels.reader;

import com.starrocks.utils.Platform;
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
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;

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
            dataColumn = new Integer[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                LocalDate date = LocalDate.ofEpochDay(dateVector.getDate(i));
                dataColumn[i] = convertToDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            }
            return dataColumn;
        }
        // 相应格式转换，注意各种enum type
        else if (columnVector instanceof DecimalColumnVector) {
//            DecimalColumnVector decimalVector = (DecimalColumnVector) columnVector;
//            dataColumn = new BigDecimal[rowBatchSize];
//            for(int i = 0; i< rowBatchSize; ++i) {
//                dataColumn[i] = BigDecimal.valueOf(decimalVector.vector[i], decimalVector.getScale());
//            }
            DecimalColumnVector decimalVector = (DecimalColumnVector) columnVector;
            dataColumn = new Long[rowBatchSize];
            for(int i = 0; i< rowBatchSize; ++i) {
                dataColumn[i] = decimalVector.vector[i];
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
