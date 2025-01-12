package com.starrocks.pixels.reader;

import com.starrocks.utils.Platform;
import io.pixelsdb.pixels.core.vector.BinaryColumnVector;
import io.pixelsdb.pixels.core.vector.ColumnVector;
import io.pixelsdb.pixels.core.vector.DateColumnVector;
import io.pixelsdb.pixels.core.vector.DecimalColumnVector;
import io.pixelsdb.pixels.core.vector.DictionaryColumnVector;
import io.pixelsdb.pixels.core.vector.DoubleColumnVector;
import io.pixelsdb.pixels.core.vector.FloatColumnVector;
import io.pixelsdb.pixels.core.vector.LongColumnVector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.TimeZone;

import static com.starrocks.utils.NativeMethodHelper.getAddrs;
import static com.starrocks.utils.NativeMethodHelper.resizeStringData;

public class PixelsUDFHelper {
    public static final int TYPE_TINYINT = 1;
    public static final int TYPE_SMALLINT = 3;
    public static final int TYPE_INT = 5;
    public static final int TYPE_BIGINT = 7;
    public static final int TYPE_FLOAT = 10;
    public static final int TYPE_DOUBLE = 11;
    public static final int TYPE_CHAR = 13;
    public static final int TYPE_DECIMAL = 16;
    public static final int TYPE_VARCHAR = 17;
    public static final int TYPE_ARRAY = 19;
    //    public static final int FE_DECIMAL32 = 21;
    public static final int TYPE_BOOLEAN = 24;
    public static final int TYPE_TIME = 44;
    public static final int TYPE_VARBINARY = 46;
    public static final int TYPE_DECIMAL64 = 48;
    public static final int TYPE_DATE = 50;
    public static final int TYPE_DATETIME = 51;

    private static final byte[] emptyBytes = new byte[0];

    private static final ThreadLocal<DateFormat> formatter =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final TimeZone timeZone = TimeZone.getDefault();

    private static void getBooleanBoxedResult(int numRows, Boolean[] boxedArr, long columnAddr) {
        byte[] nulls = new byte[numRows];
        byte[] dataArr = new byte[numRows];
        for (int i = 0; i < numRows; i++) {
            if (boxedArr[i] == null) {
                nulls[i] = 1;
            } else {
                dataArr[i] = (byte) (boxedArr[i] ? 1 : 0);
            }
        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(nulls, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(dataArr, Platform.BYTE_ARRAY_OFFSET, null, addrs[1], numRows);
    }

    private static void getByteBoxedResult(int numRows, Byte[] boxedArr, long columnAddr) {
        byte[] nulls = new byte[numRows];
        byte[] dataArr = new byte[numRows];
        for (int i = 0; i < numRows; i++) {
            if (boxedArr[i] == null) {
                nulls[i] = 1;
            } else {
                dataArr[i] = boxedArr[i];
            }
        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(nulls, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(dataArr, Platform.BYTE_ARRAY_OFFSET, null, addrs[1], numRows);
    }

    private static void getShortBoxedResult(int numRows, Short[] boxedArr, long columnAddr) {
        byte[] nulls = new byte[numRows];
        short[] dataArr = new short[numRows];
        for (int i = 0; i < numRows; i++) {
            if (boxedArr[i] == null) {
                nulls[i] = 1;
            } else {
                dataArr[i] = boxedArr[i];
            }
        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(nulls, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(dataArr, Platform.SHORT_ARRAY_OFFSET, null, addrs[1], numRows * 2L);
    }

    // getIntBoxedResult
    private static void getIntBoxedResult(int numRows, Integer[] boxedArr, long columnAddr) {
        byte[] nulls = new byte[numRows];
        int[] dataArr = new int[numRows];
        for (int i = 0; i < numRows; i++) {
            if (boxedArr[i] == null) {
                nulls[i] = 1;
            } else {
                dataArr[i] = boxedArr[i];
            }
        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(nulls, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(dataArr, Platform.INT_ARRAY_OFFSET, null, addrs[1], numRows * 4L);
    }

    // getIntBoxedResult
    private static void getBigIntBoxedResult(int numRows, LongColumnVector boxedArr, long columnAddr) {
//        byte[] nulls = new byte[numRows];
//        long[] dataArr = new long[numRows];
//        for (int i = 0; i < numRows; i++) {
//            if (boxedArr[i] == null) {
//                nulls[i] = 1;
//            } else {
//                dataArr[i] = boxedArr[i];
//            }
//        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(boxedArr.isNull, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(boxedArr.vector, Platform.LONG_ARRAY_OFFSET, null, addrs[1], numRows * 8L);
    }

    public static void getStringLargeIntResult(int numRows, BigInteger[] column, long columnAddr) {
        String[] results = new String[numRows];
        for (int i = 0; i < numRows; i++) {
            if (column[i] != null) {
                results[i] = column[i].toString();
            }
        }
        getStringBoxedResult(numRows, results, columnAddr);
    }

    private static void getFloatBoxedResult(int numRows, FloatColumnVector boxedArr, long columnAddr) {
//        byte[] nulls = new byte[numRows];
//        float[] dataArr = new float[numRows];
//        for (int i = 0; i < numRows; i++) {
//            if (boxedArr.vector[i] == null) {
//                nulls[i] = 1;
//            } else {
//                dataArr[i] = boxedArr[i];
//            }
//        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(boxedArr.isNull, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(boxedArr.vector, Platform.FLOAT_ARRAY_OFFSET, null, addrs[1], numRows * 4L);
    }

    private static void getDoubleBoxedResult(int numRows, DoubleColumnVector boxedArr, long columnAddr) {
//        byte[] nulls = new byte[numRows];
//        double[] dataArr = new double[numRows];
//        for (int i = 0; i < numRows; i++) {
//            if (boxedArr[i] == null) {
//                nulls[i] = 1;
//            } else {
//                dataArr[i] = boxedArr[i];
//            }
//        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(boxedArr.isNull, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(boxedArr.vector, Platform.DOUBLE_ARRAY_OFFSET, null, addrs[1], numRows * 8L);
    }

    private static void getDoubleTimeResult(int numRows, Time[] boxedArr, long columnAddr) {
        byte[] nulls = new byte[numRows];
        double[] dataArr = new double[numRows];
        for (int i = 0; i < numRows; i++) {
            if (boxedArr[i] == null) {
                nulls[i] = 1;
            } else {
                // Note: add the timezone offset back because Time#getTime() returns the GMT timestamp
                dataArr[i] = (boxedArr[i].getTime() + timeZone.getRawOffset()) / 1000;
            }
        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(nulls, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to double array
        Platform.copyMemory(dataArr, Platform.DOUBLE_ARRAY_OFFSET, null, addrs[1], numRows * 8L);
    }

    private static void getStringDateResult(int numRows, Date[] column, long columnAddr) {
        // TODO: return timestamp
        String[] results = new String[numRows];
        for (int i = 0; i < numRows; i++) {
            if (column[i] != null) {
                results[i] = formatter.get().format(column[i]);
            }
        }
        getStringBoxedResult(numRows, results, columnAddr);
    }

    private static void getStringLocalDateResult(int numRows, LocalDate[] column, long columnAddr) {
        // TODO: return timestamp
        String[] results = new String[numRows];
        for (int i = 0; i < numRows; i++) {
            if (column[i] != null) {
                results[i] = dateFormatter.format(column[i]);
            }
        }
        getStringBoxedResult(numRows, results, columnAddr);
    }

    private static void getStringTimeStampResult(int numRows, Timestamp[] column, long columnAddr) {
        // TODO: return timestamp
        String[] results = new String[numRows];
        for (int i = 0; i < numRows; i++) {
            if (column[i] != null) {
                results[i] = column[i].toString();
            }
        }
        getStringBoxedResult(numRows, results, columnAddr);
    }

    public static void getStringDateTimeResult(int numRows, LocalDateTime[] column, long columnAddr) {
        // TODO:
        String[] results = new String[numRows];
        for (int i = 0; i < numRows; i++) {
            if (column[i] != null) {
                results[i] = column[i].toString();
            }
        }
        getStringBoxedResult(numRows, results, columnAddr);
    }

    public static void getStringDecimalResult(int numRows, BigDecimal[] column, long columnAddr) {
        String[] results = new String[numRows];
        for (int i = 0; i < numRows; i++) {
            if (column[i] != null) {
                results[i] = column[i].toString();
            }
        }
        getStringBoxedResult(numRows, results, columnAddr);
    }

    private static void copyDataToBinaryColumn(int numRows, byte[][] byteRes, int[] offsets, byte[] nulls, long columnAddr) {
        byte[] bytes = new byte[offsets[numRows - 1]];
        int dst = 0;
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < byteRes[i].length; j++) {
                bytes[dst++] = byteRes[i][j];
            }
        }
        final long bytesAddr = resizeStringData(columnAddr, offsets[numRows - 1]);
        final long[] addrs = getAddrs(columnAddr);
        Platform.copyMemory(nulls, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);

        Platform.copyMemory(offsets, Platform.INT_ARRAY_OFFSET, null, addrs[1] + 4, numRows * 4L);

        Platform.copyMemory(bytes, Platform.BYTE_ARRAY_OFFSET, null, bytesAddr, offsets[numRows - 1]);
    }

    private static void getPixelsStringBoxedResult(int numRows, BinaryColumnVector column, long columnAddr) {
        int len = column.start[numRows - 1] + column.lens[numRows - 1];
        byte[] bytes = new byte[len];
        int dst = 0;
        int[] offsets = new int[numRows];
        for (int i = 0; i < numRows; i++) {
           offsets[i] = column.start[i] + column.lens[i];
           for (int j = 0; j < column.vector.length; j++) {
               bytes[dst++] = column.vector[i][j];
           }
        }

        final long bytesAddr = resizeStringData(columnAddr, len);
        final long[] addrs = getAddrs(columnAddr);
        Platform.copyMemory(column.isNull, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);

        Platform.copyMemory(column.start, Platform.INT_ARRAY_OFFSET, null, addrs[1] + 4, numRows * 4L);

        Platform.copyMemory(bytes, Platform.BYTE_ARRAY_OFFSET, null, bytesAddr, len);
    }

    private static void getPixelsDictionaryBoxedResult(int numRows, DictionaryColumnVector column, long columnAddr) {

        int[] offsets = new int[numRows];
        int len = 0;
        for (int i = 0; i < numRows; i++) {
           int id = column.ids[i];
           int strLen = column.dictOffsets[id+1] - column.dictOffsets[id];
           len += strLen;
           offsets[i] = len;
        }

        byte[] bytes = new byte[len];
        int dst = 0;
        for (int i = 0; i < numRows; i++) {
            int id = column.ids[i];
            int strLen = column.dictOffsets[id+1] - column.dictOffsets[id];
            int start = column.dictOffsets[id];
            for(int j = 0; j < strLen; ++j) {
                bytes[dst++] = column.dictArray[start + j];
            }
        }

        final long bytesAddr = resizeStringData(columnAddr, len);
        final long[] addrs = getAddrs(columnAddr);
        Platform.copyMemory(column.isNull, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);

        Platform.copyMemory(offsets, Platform.INT_ARRAY_OFFSET, null, addrs[1] + 4, numRows * 4L);

        Platform.copyMemory(bytes, Platform.BYTE_ARRAY_OFFSET, null, bytesAddr, len);
    }


    private static void getBinaryBoxedResult(int numRows, byte[][] column, long columnAddr) {
        byte[] nulls = new byte[numRows];
        int[] offsets = new int[numRows];
        byte[][] byteRes = new byte[numRows][];
        int offset = 0;
        for (int i = 0; i < numRows; i++) {
            if (column[i] == null) {
                byteRes[i] = emptyBytes;
                nulls[i] = 1;
            } else {
                byteRes[i] = column[i];
            }
            offset += byteRes[i].length;
            offsets[i] = offset;
        }
        copyDataToBinaryColumn(numRows, byteRes, offsets, nulls, columnAddr);
    }

    private static void getBinaryBoxedBlobResult(int numRows, Blob[] column, long columnAddr) {
        byte[] nulls = new byte[numRows];
        int[] offsets = new int[numRows];
        byte[][] byteRes = new byte[numRows][];
        int offset = 0;
        for (int i = 0; i < numRows; i++) {
            if (column[i] == null) {
                byteRes[i] = emptyBytes;
                nulls[i] = 1;
            } else {
                try {
                    int len = (int) column[i].length();
                    if (len == 0) {
                        byteRes[i] = emptyBytes;
                    } else {
                        byteRes[i] = column[i].getBytes(1, len);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
            offset += byteRes[i].length;
            offsets[i] = offset;
        }
        copyDataToBinaryColumn(numRows, byteRes, offsets, nulls, columnAddr);
    }

    private static void getStringBoxedResult(int numRows, String[] column, long columnAddr) {
        byte[] nulls = new byte[numRows];
        int[] offsets = new int[numRows];
        byte[][] byteRes = new byte[numRows][];
        int offset = 0;
        for (int i = 0; i < numRows; i++) {
            if (column[i] == null) {
                byteRes[i] = emptyBytes;
                nulls[i] = 1;
            } else {
                byteRes[i] = column[i].getBytes(StandardCharsets.UTF_8);
            }
            offset += byteRes[i].length;
            offsets[i] = offset;
        }
        copyDataToBinaryColumn(numRows, byteRes, offsets, nulls, columnAddr);
    }

    private static void getDateResult(int numRows, DateColumnVector column, long columnAddr) {

        int[] dataArr = new int[numRows];

//        Arrays.parallelSetAll(dataArr, i -> column.dates[i] + 2440588);
        for(int i = 0; i < numRows; ++i) {
            dataArr[i] = column.dates[i] + 2440588;
        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(column.isNull, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to int array
        Platform.copyMemory(dataArr, Platform.INT_ARRAY_OFFSET, null, addrs[1], numRows * 4L);
    }

    public static void getDecimalResult(int numRows, DecimalColumnVector column, long columnAddr) {
//        byte[] nulls = new byte[numRows];
//        long[] dataArr = new long[numRows];
//        for (int i = 0; i < numRows; i++) {
//            if (column[i] == null) {
//                nulls[i] = 1;
//            } else {
//                dataArr[i] = column[i];
//            }
//        }

        final long[] addrs = getAddrs(columnAddr);
        // memcpy to uint8_t array
        Platform.copyMemory(column.isNull, Platform.BYTE_ARRAY_OFFSET, null, addrs[0], numRows);
        // memcpy to long array
        Platform.copyMemory(column.vector, Platform.INT_ARRAY_OFFSET, null, addrs[1], numRows * 8L);
    }


    public static void getResultFromBoxedArray(int type, int numRows, Object boxedResult, long columnAddr) {
        switch (type) {
//            case TYPE_BOOLEAN: {
//                getBooleanBoxedResult(numRows, (Boolean[]) boxedResult, columnAddr);
//                break;
//            }
//            case TYPE_TINYINT: {
//                getByteBoxedResult(numRows, (Byte[]) boxedResult, columnAddr);
//                break;
//            }
//            case TYPE_SMALLINT: {
//                getShortBoxedResult(numRows, (Short[]) boxedResult, columnAddr);
//                break;
//            }
//            case TYPE_INT: {
//                getIntBoxedResult(numRows, (Integer[]) boxedResult, columnAddr);
//                break;
//            }
            case TYPE_FLOAT: {
                getFloatBoxedResult(numRows, (FloatColumnVector) boxedResult, columnAddr);
                break;
            }
            case TYPE_DOUBLE: {
                getDoubleBoxedResult(numRows, (DoubleColumnVector) boxedResult, columnAddr);
                break;
            }
            case TYPE_BIGINT: {
                getBigIntBoxedResult(numRows, (LongColumnVector) boxedResult, columnAddr);
                break;
            }
//            case TYPE_TIME: {
//                getDoubleTimeResult(numRows, (Time[]) boxedResult, columnAddr);
//                break;
//            }
            case TYPE_DATE: {
                getDateResult(numRows, (DateColumnVector) boxedResult, columnAddr);
                break;
            }
            case TYPE_DECIMAL:
            case TYPE_DECIMAL64: {
                getDecimalResult(numRows, (DecimalColumnVector) boxedResult, columnAddr);
                break;
            }
            case TYPE_VARCHAR: {
//                if (boxedResult instanceof Date[]) {
//                    getStringDateResult(numRows, (Date[]) boxedResult, columnAddr);
//                } else if (boxedResult instanceof LocalDate[]) {
//                    getStringLocalDateResult(numRows, (LocalDate[]) boxedResult, columnAddr);
//                } else if (boxedResult instanceof LocalDateTime[]) {
//                    getStringDateTimeResult(numRows, (LocalDateTime[]) boxedResult, columnAddr);
//                } else if (boxedResult instanceof Timestamp[]) {
//                    getStringTimeStampResult(numRows, (Timestamp[]) boxedResult, columnAddr);
//                } else if (boxedResult instanceof BigDecimal[]) {
//                    getStringDecimalResult(numRows, (BigDecimal[]) boxedResult, columnAddr);
//                } else if (boxedResult instanceof BigInteger[]) {
//                    getStringLargeIntResult(numRows, (BigInteger[]) boxedResult, columnAddr);
//                }
                if (boxedResult instanceof BinaryColumnVector) {
                    getPixelsStringBoxedResult(numRows, (BinaryColumnVector) boxedResult, columnAddr);
                }
                else if (boxedResult instanceof DictionaryColumnVector) {
                    getPixelsDictionaryBoxedResult(numRows, (DictionaryColumnVector) boxedResult, columnAddr);
                }
                else {
                    throw new UnsupportedOperationException("unsupported type:" + boxedResult);
                }
                break;
            }
            case TYPE_CHAR: {
                if (boxedResult instanceof BinaryColumnVector) {
                    getPixelsStringBoxedResult(numRows, (BinaryColumnVector) boxedResult, columnAddr);
                }
                else if (boxedResult instanceof DictionaryColumnVector) {
                    getPixelsDictionaryBoxedResult(numRows, (DictionaryColumnVector) boxedResult, columnAddr);
                }
                else {
                    throw new UnsupportedOperationException("unsupported type:" + boxedResult);
                }
                break;
            }
//            case TYPE_VARBINARY: {
//                if (boxedResult instanceof byte[][]) {
//                    getBinaryBoxedResult(numRows, (byte[][]) boxedResult, columnAddr);
//                } else if (boxedResult instanceof Blob[]) {
//                    getBinaryBoxedBlobResult(numRows, (Blob[]) boxedResult, columnAddr);
//                } else {
//                    throw new UnsupportedOperationException("unsupported type:" + boxedResult);
//                }
//                break;
//            }
            default:
                throw new UnsupportedOperationException("unsupported type:" + type);
        }
    }
}
