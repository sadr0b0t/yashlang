package su.sadrobot.yashlang.util;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * PlaylistUrlUtil.java is part of YaShlang.
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

public class PlaylistUrlUtil {
    public static String getYtPlaylistUid(final String url) {
        // https://www.youtube.com/playlist?list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388
        return url
                .replace("https://www.youtube.com/playlist?list=", "")
                .replace("http://www.youtube.com/playlist?list=", "")
                .replace("www.youtube.com/playlist?list=", "")
                .replace("youtube.com/playlist?list=", "");
    }

    public static String getYtChannelUid(final String url) {
        // https://www.youtube.com/channel/UCrlFHstLFNA_HsIV7AveNzA
        return url
                .replace("https://www.youtube.com/channel/", "")
                .replace("http://www.youtube.com/channel/", "")
                .replace("www.youtube.com/channel/", "")
                .replace("youtube.com/channel/", "");
    }

    public static String getYtUserUid(final String url) {
        // https://www.youtube.com/user/ClassicCartoonsMedia
        return url
                .replace("https://www.youtube.com/user/", "")
                .replace("http://www.youtube.com/user/", "")
                .replace("www.youtube.com/user/", "")
                .replace("youtube.com/user/", "");
    }

    public static boolean isYtPlaylist(final String url) {
        // TODO: this parser is too dummy
        return url.contains("youtube.com/playlist?list=");
    }

    public static boolean isYtChannel(final String url) {
        // TODO: this parser is too dummy
        return url.contains("youtube.com/channel/");
    }

    public static boolean isYtUser(final String url) {
        // TODO: this parser is too dummy
        return url.contains("youtube.com/user/");
    }
}
