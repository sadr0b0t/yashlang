package su.sadrobot.yashlang.util;

/*
 * Copyright (C) Anton Moiseev 2022 <github.com/sadr0b0t>
 *
 * YaShlang is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YaShlang is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with YaShlang.  If not, see <http://www.gnu.org/licenses/>.
 */


import android.content.Context;

import java.math.RoundingMode;
import java.text.DecimalFormat;

import su.sadrobot.yashlang.R;

public class StringFormatUtil {

    public static String formatFileSize(final Context context, final long sizeBytes) {
        final String sizeStr;
        if(sizeBytes < 1024) {
            sizeStr = sizeBytes + " " + context.getString(R.string.unit_bytes);
        } else if (sizeBytes < 1024 * 1024) {
            final DecimalFormat format = new DecimalFormat("#0.00");
            format.setRoundingMode(RoundingMode.DOWN);
            sizeStr = format.format((double) sizeBytes / 1024) + " " + context.getString(R.string.unit_kb);
        } else if (sizeBytes < 1024 * 1024 * 1024) {
            final DecimalFormat format = new DecimalFormat("#0.00");
            format.setRoundingMode(RoundingMode.DOWN);
            sizeStr = format.format((double) sizeBytes / 1024 / 1024) + " " + context.getString(R.string.unit_mb);
        } else {
            final DecimalFormat format = new DecimalFormat("#0.00");
            format.setRoundingMode(RoundingMode.DOWN);
            sizeStr = format.format((double) sizeBytes / 1024 / 1024 / 1024) + " " + context.getString(R.string.unit_gb);
        }
        return sizeStr;
    }
}
