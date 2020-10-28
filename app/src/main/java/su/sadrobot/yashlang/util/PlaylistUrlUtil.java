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
        // например:
        // https://www.youtube.com/playlist?list=PL7DB3215F59FB91BE
        // https://www.youtube.com/watch?v=5NXpdxG4j5k&list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388
        return url.contains("youtube.com") && url.contains("list=");
    }

    public static boolean isYtChannel(final String url) {
        // например:
        // https://www.youtube.com/channel/UCrlFHstLFNA_HsIV7AveNzA
        return url.contains("youtube.com/channel/");
    }

    public static boolean isYtUser(final String url) {
        // например:
        // https://www.youtube.com/c/eralash
        // https://www.youtube.com/user/TVSmeshariki

        // обратить внимание: варианты  плйлистов youtube.com/user/ и youtube.com/c/ не обязательно взаимозаменяемые
        // например: www.youtube.com/user/Soyuzmultfilm и www.youtube.com/c/Soyuzmultfilm - разные пользователи
        // или: https://www.youtube.com/c/eralash есть, а https://www.youtube.com/user/eralash вообще не существует
        // (но иногда совпадают)
        return url.contains("youtube.com/user/") || url.contains("youtube.com/c/");
    }

    public static boolean isPtChannel(final String url) {
        // TODO: может не сработать для каналов на других доменах
        // например:
        // https://peer.tube/video-channels/cartoons@vidcommons.org/videos
        return url.contains("peer.tube/video-channels") && url.contains("/videos");
    }

    public static boolean isPtUser(final String url) {
        // TODO: может не сработать для каналов на других доменах
        // например:
        // https://peer.tube/accounts/animation@vidcommons.org/videos
        return url.contains("peer.tube/accounts") && url.contains("/videos");
    }


    public static boolean isYtVideo(final String url) {
        // например:
        // https://www.youtube.com/watch?v=Tb8jSMhTK0s
        return url.startsWith("https://www.youtube.com/watch?v=");
    }

    public static boolean isPtVideo(final String url) {
        // TODO: может не сработать для каналов на других доменах
        // например:
        // https://peer.tube/api/v1/videos/a5bcc9ab-221c-4e25-afdb-88d837741b61
        return url.startsWith("https://peer.tube") && url.contains("/videos/");
    }

    /**
     * Удалить "https://" и "www." из адреса для краткости и красоты
     * @param url
     * @return
     */
    public static String cleanupUrl(final String url) {
        return url.replaceFirst("https://", "").replaceFirst("www.", "");
    }

    /**
     * Исправить адрес иконки канала YouTube так, чтобы иконка была большего размера
     * @param iconUrl
     * @return
     */
    public static String fixYtChannelAvatarSize(final String iconUrl) {
        // Пример ссылки на иконку:
        // https://yt3.ggpht.com/a/AGF-l7_hKI23Rm_DGUcoN7JFm2tKQl2maXaQdAJbqA=s110-c-k-c0xffffffff-no-rj-mo
        // Размер иконки канала YouTube можно задавать любой вообще в параметре: *=s110-*
        // В какой-то момент getAvatarUrl стал возвращать слишком маленькую иконку (s48)
        // (скорее всего, её такую возвращает ютюб, т.к. NewPipeExtractor парсит страницы)
        // У нас иконки примерно 100x100 везде, но будем брать с запасом 240x240, чтобы хайрез
        return iconUrl.replace("=s48-", "=s240-");
    }
}
