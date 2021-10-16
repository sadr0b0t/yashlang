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

    public static boolean isYtChannel(final String url) {
        // например:
        // https://www.youtube.com/channel/UCrlFHstLFNA_HsIV7AveNzA
        return url.contains("youtube.com/channel/");
    }

    public static boolean isYtPlaylist(final String url) {
        // например:
        // https://www.youtube.com/playlist?list=PL7DB3215F59FB91BE
        // https://www.youtube.com/watch?v=5NXpdxG4j5k&list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388
        return url.contains("youtube.com") && url.contains("list=");
    }

    public static boolean isPtUser(final String url) {
        // например:
        // https://open.tube/accounts/openmovies/videos
        // https://peer.tube/accounts/animation@vidcommons.org/videos
        // в конец могут попасть параметры:
        // https://peertube.ch/accounts/gabyweber/videos?a-state=42
        //
        // вариант с сокращением:
        // https://github.com/Chocobozzz/PeerTube/releases/tag/v3.3.0-rc.1
        // https://github.com/sadr0b0t/yashlang/issues/109
        // старый вариант: https://mult.sadrobot.su/accounts/sadrobot/videos
        // новый вариант: https://mult.sadrobot.su/a/sadrobot/videos

        // https://docs.oracle.com/javase/10/docs/api/java/util/regex/Pattern.html
        return url.matches(".+/accounts/.+/videos(\\?.+)?") ||
                url.matches(".+/a/.+/videos(\\?.+)?");
    }

    public static boolean isPtChannel(final String url) {
        // например:
        // https://open.tube/video-channels/public_domain_romance/videos
        // https://peer.tube/video-channels/cartoons@vidcommons.org/videos
        // в конец могут попасть параметры:
        // https://peer.tube/video-channels/root_channel@yunopeertube.myddns.me/videos?a-state=42
        //
        // вариант c сокращением:
        // https://github.com/Chocobozzz/PeerTube/releases/tag/v3.3.0-rc.1
        // https://github.com/sadr0b0t/yashlang/issues/109
        // старый вариант: https://mult.sadrobot.su/video-channels/miscmults/videos
        // новый вариант: https://mult.sadrobot.su/c/miscmults/videos

        // https://docs.oracle.com/javase/10/docs/api/java/util/regex/Pattern.html
        return url.matches(".+/video-channels/.+/videos(\\?.+)?") ||
                url.matches(".+/c/.+/videos(\\?.+)?");
    }

    public static boolean isPtPlaylist(final String url) {
        // например:
        // https://peertube.ch/videos/watch/playlist/3e46da13-20ed-4ad3-92d0-9f8dbb08fb95
        // в конец могут попасть параметры:
        // https://peertube.ch/videos/watch/playlist/3e46da13-20ed-4ad3-92d0-9f8dbb08fb95?playlistPosition=1

        // https://docs.oracle.com/javase/10/docs/api/java/util/regex/Pattern.html
        return url.matches(".+/videos/watch/playlist/.+");
    }


    public static boolean isYtVideo(final String url) {
        // например:
        // https://www.youtube.com/watch?v=Tb8jSMhTK0s
        return url.startsWith("https://www.youtube.com/watch?v=");
    }

    public static boolean isPtVideo(final String url) {
        // например (ссылка на страницу в браузере):
        // https://open.tube/videos/watch/0e8f12de-da85-4f10-bb0b-673680e38f61

        // https://docs.oracle.com/javase/10/docs/api/java/util/regex/Pattern.html
        return url.matches(".+/videos/watch/.+");
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
        return iconUrl.replace("=s48-", "=s240-").replace("=s100-", "=s240-");
    }

    public static String fixYtVideoThumbSize(final String thumbUrl) {
        // NewPipeExtractor возвращает иконку для видео такую:
        // https://i.ytimg.com/vi/pevcwilRM8o/hqdefault.jpg?sqp=-oaymwEYCKgBEF5IVfKriqkDCwgBFQAAiEIYAXAB&rs=AOn4CLB8Bx4_LmDq7dowwMZPclW_qixhbQ
        // на сайте ютюб эту же иконку даёт в таком варианте:
        // https://i.ytimg.com/vi/pevcwilRM8o/mqdefault.jpg?sqp=-oaymwEYCKgBEF5IVfKriqkDCwgBFQAAiEIYAXAB&rs=AOn4CLB8Bx4_LmDq7dowwMZPclW_qixhbQ
        // отличие только "hquedefault" и "mquedefault"
        // второй вариант лучше 1-го, на телефоне маленький вариант еще ок, но на планшете уже не ок
        return thumbUrl.replace("hquedefault", "mquedefault");
    }
}
