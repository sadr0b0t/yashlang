package su.sadrobot.yashlang.util;


import androidx.room.TypeConverter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

// via Brijesh Thumar https://github.com/Thumar
// https://github.com/Thumar/RoomPersistenceLibrary/blob/master/app/src/main/java/com/app/androidkt/librarymanagment/utils/TimestampConverter.java
// https://androidkt.com/datetime-datatype-sqlite-using-room/

public class TimestampConverter {
    public static final String TIME_STAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final DateFormat df = new SimpleDateFormat(TIME_STAMP_FORMAT);

    @TypeConverter
    public static Date fromTimestamp(final String value) {
        if (value != null) {
            try {
                return df.parse(value);
            } catch (Exception e) {
                // время от времени вылетаю разные невоспроизводимые эксепшены,
                // среди прочего: ParseException, NumberFormatException, ArrayIndexOutOfBoundsException
                //e.printStackTrace();
            }
            return null;
        } else {
            return null;
        }
    }

    @TypeConverter
    public static String dateToTimestamp(final Date value) {
        return value == null ? null : df.format(value);
    }
}
