package su.sadrobot.yashlang.util;

/*
 * Copyright (C) Anton Moiseev 2026 <github.com/sadr0b0t>
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

import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;

/**
 * NFC helper methods
 */
public class NfcUtil {
    // NFC
    // https://developer.android.com/develop/connectivity/nfc/nfc#java
    // https://www.geeksforgeeks.org/android/nfc-reader-and-writer-kotlin-android-application/
    // https://github.com/marc136/tonuino-nfc-tools/tree/main
    // https://github.com/marc136/tonuino-nfc-tools/blob/main/app/src/main/java/de/mw136/tonuino/nfc/NfcIntentActivity.kt
    // https://github.com/marc136/tonuino-nfc-tools/blob/main/app/src/main/java/de/mw136/tonuino/nfc/TagHelper.kt

    public static String[][] NFC_TECH_LISTS_ARRAY = new String[][]{
            new String[]{NfcA.class.getName()},
            new String[]{NfcB.class.getName()},
            new String[]{NfcF.class.getName()}};

    public static IntentFilter[] createIntentFilterArray() {
        final IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            // вообще, там все равно, есть ли там данные, нужен только id метки
            ndef.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            //e.printStackTrace();
        }
        return new IntentFilter[]{ndef, new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};
    }

    public static String nfcTagIdToString(final byte[] id) {
        // byte array to hex
        final StringBuffer buff = new StringBuffer();
        for (byte b : id) {
            buff.append(String.format("%02X", b));
        }
        return buff.toString();
    }

}
