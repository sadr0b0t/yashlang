package su.sadrobot.yashlang.controller;


/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ContentLoader.java is part of YaShlang.
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
import android.database.SQLException;

import org.schabi.newpipe.DownloaderTestImpl;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;

import static org.schabi.newpipe.extractor.ServiceList.YouTube;


public class ContentLoader {

    private static int LOAD_PAGE_RETRY_COUNT = 3;


    private static ContentLoader _instance;

    static {
        _instance = new ContentLoader();
    }

    public static ContentLoader getInstance() {
        return _instance;
    }

    private ContentLoader() {
    }

    public interface TaskListener {
        void onStart();

        void onFinish();

        void onStatusChange(final String status, final Exception e);
    }

    public static class TaskController {
        private boolean running = false;
        private boolean canceled = false;
        private String statusMsg = "";
        private Exception exception;

        private TaskListener taskListener;

        public void setTaskListener(TaskListener taskListener) {
            this.taskListener = taskListener;
        }

        public boolean isRunning() {
            return running;
        }

        public void setRunning(boolean running) {
            boolean changed = this.running != running;
            this.running = running;
            if (this.taskListener != null && changed) {
                if (running) {
                    this.taskListener.onStart();
                } else {
                    this.taskListener.onFinish();
                }
            }
        }

        public boolean isCanceled() {
            return canceled;
        }

        public void cancel() {
            canceled = true;
        }

        public void setStatusMsg(final String msg) {
            setStatusMsg(msg, null);
        }

        public void setStatusMsg(final String msg, final Exception e) {
            this.statusMsg = msg;
            this.exception = e;

            if (this.taskListener != null) {
                taskListener.onStatusChange(msg, e);
            }
        }

        public String getStatusMsg() {
            return statusMsg;
        }

        public Exception getException() {
            return exception;
        }
    }

    /**
     * Ищем каналы и плейлисты по имени. В выдачу сначала добавляем каналы, потом плейлисты.
     * @param sstr
     * @return
     * @throws ExtractionException
     * @throws IOException
     */
    public List<PlaylistInfo> searchPlaylists(final String sstr) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java


        final List<PlaylistInfo> playlists = new ArrayList<PlaylistInfo>();
        final List<InfoItem> pageItems = new ArrayList<InfoItem>();

        NewPipe.init(DownloaderTestImpl.getInstance());

        // с YouTube работает только 1й элемент (см YoutubeSearchQueryHandlerFactory.getUrl)
        final List<String> contentFilters = new ArrayList<String>();
        contentFilters.add(YoutubeSearchQueryHandlerFactory.CHANNELS);
        SearchExtractor extractor = YouTube.getSearchExtractor(sstr, contentFilters, "");
        extractor.fetchPage();
        pageItems.addAll(extractor.getInitialPage().getItems());

        // теперь плейлисты
        contentFilters.clear();
        contentFilters.add(YoutubeSearchQueryHandlerFactory.PLAYLISTS);
        extractor = YouTube.getSearchExtractor(sstr, contentFilters, "");
        extractor.fetchPage();
        pageItems.addAll(extractor.getInitialPage().getItems());


        for (final InfoItem infoItem : pageItems) {
            if (infoItem.getInfoType() == InfoItem.InfoType.CHANNEL ||
                    infoItem.getInfoType() == InfoItem.InfoType.PLAYLIST) {
                final String plName = infoItem.getName();
                final String plUrl = infoItem.getUrl();
                final String plThumbUrl = infoItem.getThumbnailUrl();
                final PlaylistInfo.PlaylistType plType;
                if (infoItem.getInfoType() == InfoItem.InfoType.CHANNEL) {
                    plType = PlaylistInfo.PlaylistType.YT_CHANNEL;
                } else {// if(infoItem.getInfoType() == InfoItem.InfoType.PLAYLIST) {
                    plType = PlaylistInfo.PlaylistType.YT_PLAYLIST;
                }

                final PlaylistInfo pl = new PlaylistInfo(plName, plUrl, plThumbUrl, plType);
                playlists.add(pl);
            }
        }

        return playlists;
    }

    public PlaylistInfo getYtPlaylistInfo(final String plUrl) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java

        final PlaylistInfo.PlaylistType plType;

        // Выкачать список всех видео в канале
        NewPipe.init(DownloaderTestImpl.getInstance());
        final ListExtractor<StreamInfoItem> extractor;
        if (PlaylistUrlUtil.isYtChannel(plUrl) ){
            plType = PlaylistInfo.PlaylistType.YT_CHANNEL;
            extractor = YouTube.getChannelExtractor(plUrl);
        } else if(PlaylistUrlUtil.isYtUser(plUrl)) {
            plType = PlaylistInfo.PlaylistType.YT_USER;
            extractor = YouTube.getChannelExtractor(plUrl);
        } else if (PlaylistUrlUtil.isYtPlaylist(plUrl)) {
            plType = PlaylistInfo.PlaylistType.YT_PLAYLIST;
            extractor = YouTube.getPlaylistExtractor(plUrl);
        } else {
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        // грузим реально страницу здесь
        extractor.fetchPage();

        // заберем со траницы то, что нам нужно
        final String plName = extractor.getName();
        final String plThumbUrl;

        if (extractor instanceof ChannelExtractor) {
            plThumbUrl = ((ChannelExtractor) extractor).getAvatarUrl();
        } else if (extractor instanceof PlaylistExtractor) {
            plThumbUrl = ((PlaylistExtractor) extractor).getThumbnailUrl();
        } else {
            // мы сюда никогда не попадем, но ладно
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        return new PlaylistInfo(plName, plUrl, plThumbUrl, plType);
    }

    public long addYtPlaylist(final Context context, final String plUrl, final TaskController taskController) {
        // https://github.com/TeamNewPipe/NewPipeExtractor
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java


        // 1. Не выбрасываем эксепшены,ошибки ловим через TaskController (иначе не сможем задать корректный статус)
        // 2. Добавляем записи в базу данных по мере загрузки страниц (так должно быть правильнее
        // с точки зрения использоания памяти, хотя кто ее сейчас экономит)
        // 3. Все делаем внутри одной транзакции, в случае неудачной загрузки очередной страницы
        // отменяем всю транзакцию
        // 4. Недостаток такого подхода: код работы с базой данных перемешан с кодом выкачивания
        // информации из сети, вариант "выкачать все записи со всех страниц, а потом все их
        // добавить в базу" выглядит красивее

        // канал
        //https://www.youtube.com/channel/UCrlFHstLFNA_HsIV7AveNzA
        // пользователь
        //https://www.youtube.com/user/ClassicCartoonsMedia
        // плейлист
        //https://www.youtube.com/playlist?list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388
        //https://www.youtube.com/watch?v=5NXpdxG4j5k&list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388

        taskController.setRunning(true);

        final AtomicLong plId = new AtomicLong(-1);

        final VideoDatabase videodb = VideoDatabase.getDb(context);
        try {
            videodb.runInTransaction(new Runnable() {
                @Override
                public void run() {

                    // Выкачать список всех видео в канале

                    // для тестов:
                    // канал
                    //final String plUrl = "https://www.youtube.com/channel/UCrlFHstLFNA_HsIV7AveNzA";
                    // пользователь
                    //final String plUrl = "https://www.youtube.com/user/ClassicCartoonsMedia";
                    // плейлист
                    //final String plUrl = "https://www.youtube.com/playlist?list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388";
                    //final String plUrl = "https://www.youtube.com/watch?v=5NXpdxG4j5k&list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388";


                    final PlaylistInfo.PlaylistType plType;

                    NewPipe.init(DownloaderTestImpl.getInstance());
                    final ListExtractor<StreamInfoItem> extractor;
                    try {
                        if (PlaylistUrlUtil.isYtChannel(plUrl) ){
                            plType = PlaylistInfo.PlaylistType.YT_CHANNEL;
                            extractor = YouTube.getChannelExtractor(plUrl);
                        } else if(PlaylistUrlUtil.isYtUser(plUrl)) {
                            plType = PlaylistInfo.PlaylistType.YT_USER;
                            extractor = YouTube.getChannelExtractor(plUrl);
                        } else if (PlaylistUrlUtil.isYtPlaylist(plUrl)) {
                            plType = PlaylistInfo.PlaylistType.YT_PLAYLIST;
                            extractor = YouTube.getPlaylistExtractor(plUrl);
                        } else {
                            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
                        }
                    } catch (ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    taskController.setStatusMsg("Loading page-1...");
                    // загрузить первую страницу
                    try {
                        extractor.fetchPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    // имя канала
                    final String plName;
                    final String plThumbUrl;
                    try {
                        plName = extractor.getName();
                        if (extractor instanceof ChannelExtractor) {
                            plThumbUrl = ((ChannelExtractor) extractor).getAvatarUrl();
                        } else if (extractor instanceof PlaylistExtractor) {
                            plThumbUrl = ((PlaylistExtractor) extractor).getThumbnailUrl();
                        } else {
                            plThumbUrl = "";
                        }
                    } catch (ParsingException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    // создадим запись в таблице плейлистов, чтобы был id
                    final long _plId = videodb.playlistInfoDao().insert(
                            new PlaylistInfo(plName, plUrl, plThumbUrl, plType));

                    // качаем список видео страница за страницей
                    // начинаем с первой страницы
                    ListExtractor.InfoItemsPage<StreamInfoItem> nextPage;
                    try {
                        nextPage = extractor.getInitialPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    int videoItemCount = 0;
                    int fakeTimestamp = VideoItem.FAKE_TIMESTAMP_BLOCK_SIZE;

                    final List<StreamInfoItem> pageItems = new ArrayList<StreamInfoItem>();
                    pageItems.addAll(nextPage.getItems());
                    final List<VideoItem> videoItems = new ArrayList<VideoItem>();

                    // теперь загружаем все ролики - здесь 1я страница
                    // Пропустить несколько первых роликов - для ConfigOptions.DEVEL_MODE_ON
                    int skipItems = 25;
                    for (StreamInfoItem item : pageItems) {
                        if (ConfigOptions.DEVEL_MODE_ON && skipItems > 0) {
                            // пропустим несколько первых, чтобы потестить loadNewPlaylistItems
                            skipItems--;
                        } else {
                            videoItems.add(extractVideoItem(item, _plId, true, fakeTimestamp));
                            fakeTimestamp--;

                            videoItemCount++;
                        }
                    }
                    // сохраняем страницу в базу
                    videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                    taskController.setStatusMsg("Loading page-1...ok");

                    //System.out.println(nextPage.hasNextPage() + ": " + nextPage.getNextPageUrl());
                    // загружать по порядку остальные страницы до тех пор, пока не закончатся
                    int page_n = 1;
                    while (nextPage.hasNextPage()) {
                        pageItems.clear();
                        videoItems.clear();

                        page_n++;
                        taskController.setStatusMsg("Loading page-" + page_n + "...");

                        boolean done = false;
                        // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
                        // ошибку вместо страницы
                        int retryCount = LOAD_PAGE_RETRY_COUNT;
                        Exception retryEx = null;
                        while (!done && retryCount > 0) {
                            try {
                                nextPage = extractor.getPage(nextPage.getNextPage());
                                done = true;
                            } catch (Exception e) {
                                taskController.setStatusMsg("Loading page-" + page_n + "..." +
                                        "[retry: " + (LOAD_PAGE_RETRY_COUNT - retryCount + 1) + "]");
                                retryEx = e;
                                retryCount--;
                            }
                        }

                        if (done) {
                            // загрузили страницу
                            pageItems.addAll(nextPage.getItems());

                            for (StreamInfoItem item : pageItems) {
                                videoItems.add(extractVideoItem(item, _plId, true, fakeTimestamp));
                                fakeTimestamp--;
                                videoItemCount++;
                            }
                            // сохраняем страницу в базу
                            videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                            taskController.setStatusMsg("Loading page-" + page_n + "...ok");
                        } else {
                            // страница так и не загрузилась, обрываем транзакцию с ошибкой
                            final Exception e = new IOException("Error loading page, retry count exceeded", retryEx);
                            taskController.setStatusMsg("Loading page-" + page_n + "...ERROR", e);
                            throw new RuntimeException("Error loading page, retry count exceeded", e);
                        }
                    }

                    // ставим pId здесь, т.к. здесь уже точно все в порядке
                    plId.set(_plId);
                    taskController.setStatusMsg("Added " + videoItemCount + " items");
                }
            });
        } catch (SQLException e) {
            taskController.setStatusMsg("UNEXPECTED DB problem", e);
            e.printStackTrace();
        } catch (Exception e){
            // нам все-таки нужно поймать здесь RuntimeException,
            // статус taskController уже выставлен внутри
        }
        videodb.close();
        taskController.setRunning(false);
        return plId.get();
    }

    public void addYtPlaylistNewItems(final Context context, final long plId,
                                      final String plUrl, final TaskController taskController) {
        // Загрузить новые видео в плейлисте - видео, добавленные после того,
        // как плейлист был сохранен локально или последний раз обновлялся
        // Алгоритм такой:
        // 1) Мы считаем, что самые новые видео появляются первыми начиная с первой страницы
        // 2) Загружаем все видео страница за страницей до тех пор, пока не встретится
        // видео, которое уже было добавлено в локальную базу для этого плейлиста
        // 3) У видео есть дата, но ее использовать сложнее и накладнее, чем предложенный
        // алгоритм: во-первых, при загрузке информации о видео со страницы плейлиста
        // вместо даты загрузки приходит бесполезная поебень вида "1 год назад", "4 месяца назад" и т.п.;
        // на странице видео есть дата получше (например: "12 авг. 2008 г."),
        // но ее парсить тоже не очень удобно с учетом локализации и сокращений.

        taskController.setRunning(true);

        final VideoDatabase videodb = VideoDatabase.getDb(context);

        try {
            videodb.runInTransaction(new Runnable() {
                @Override
                public void run() {

                    final List<VideoItem> videoItems = new ArrayList<VideoItem>();

                    NewPipe.init(DownloaderTestImpl.getInstance());
                    final ListExtractor<StreamInfoItem> extractor;
                    try {
                        if (PlaylistUrlUtil.isYtChannel(plUrl) || PlaylistUrlUtil.isYtUser(plUrl)) {
                            extractor = YouTube.getChannelExtractor(plUrl);
                        } else if (PlaylistUrlUtil.isYtPlaylist(plUrl)) {
                            extractor = YouTube.getPlaylistExtractor(plUrl);
                        } else {
                            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
                        }
                    } catch (ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    // список видео страница за страницей

                    // начинаем с первой страницы
                    taskController.setStatusMsg("Loading page-1...");
                    // загрузить первую страницу
                    try {
                        extractor.fetchPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    ListExtractor.InfoItemsPage<StreamInfoItem> nextPage;
                    try {
                        nextPage = extractor.getInitialPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error", e);
                        throw new RuntimeException(e);
                    }

                    int videoItemCount = 0;
                    long fakeTimestamp = videodb.videoItemDao().getMaxFakeTimestamp(plId) + VideoItem.FAKE_TIMESTAMP_BLOCK_SIZE;
                    final boolean plEnabled = videodb.playlistInfoDao().isEnabled(plId);

                    final List<StreamInfoItem> pageItems = new ArrayList<StreamInfoItem>();
                    pageItems.addAll(nextPage.getItems());


                    boolean foundOld = false;
                    for (StreamInfoItem item : pageItems) {
                        final String ytId = PlaylistUrlUtil.getYtId(item.getUrl());
                        if (videodb.videoItemDao().getByYtId(plId, ytId) == null) {
                            videoItems.add(extractVideoItem(item, plId, plEnabled, fakeTimestamp));
                            fakeTimestamp--;
                            videoItemCount++;
                        } else {
                            foundOld = true;
                            break;
                        }
                    }

                    // сохраняем страницу в базу
                    videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                    taskController.setStatusMsg("Loading page-1...ok");

                    // если на первой странице все ролики оказались новыми, продолжаем с остальными страницами
                    // загружать по порядку остальные страницы до тех пор, пока не закончатся страницы
                    // или не встретим ролик, который уже был добавлен в базу
                    int page_n = 1;
                    while (!foundOld && nextPage.hasNextPage()) {
                        pageItems.clear();
                        videoItems.clear();

                        page_n++;
                        taskController.setStatusMsg("Loading page-" + page_n + "...");

                        boolean done = false;
                        // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
                        // ошибку вместо страницы
                        int retryCount = LOAD_PAGE_RETRY_COUNT;
                        while (!done && retryCount > 0) {
                            try {
                                nextPage = extractor.getPage(nextPage.getNextPage());
                                done = true;
                            } catch (IOException | ExtractionException e) {
                                retryCount--;
                            }
                        }

                        if (done) {
                            // загрузили страницу - проверяем ролики
                            pageItems.addAll(nextPage.getItems());
                            for (StreamInfoItem item : pageItems) {
                                final String ytId = PlaylistUrlUtil.getYtId(item.getUrl());
                                if (videodb.videoItemDao().getByYtId(plId, ytId) == null) {
                                    videoItems.add(extractVideoItem(item, plId, plEnabled, fakeTimestamp));
                                    fakeTimestamp--;
                                    videoItemCount++;
                                } else {
                                    foundOld = true;
                                    break;
                                }
                            }
                            // сохраняем страницу в базу
                            videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));

                            taskController.setStatusMsg("Loading page-" + page_n + "...ok");
                        } else {
                            // страница так и не загрузилась,
                            // обрываем транзакцию с ошибкой
                            final Exception e = new IOException("Error loading page, retry count exceeded");
                            taskController.setStatusMsg("Loading page-" + page_n + "...ERROR", e);
                            throw new RuntimeException("Error loading page, retry count exceeded");
                        }
                    }

                    taskController.setStatusMsg("Added " + videoItemCount + " items");
                }
            });
        } catch (SQLException e) {
            taskController.setStatusMsg("UNEXPECTED DB problem", e);
            e.printStackTrace();
        } catch (Exception e){
            // нам все-таки нужно поймать здесь RuntimeException,
            // статус taskController уже выставлен внутри
        }

        videodb.close();
        taskController.setRunning(false);
    }

    public List<VideoItem> loadYtPlaylistNewItems(final Context context, final PlaylistInfo playlist) throws IOException, ExtractionException {
        // Загрузить новые видео в плейлисте - видео, добавленные после того,
        // как плейлист был сохранен локально или последний раз обновлялся
        // Алгоритм такой:
        // 1) Мы считаем, что самые новые видео появляются первыми начиная с первой страницы
        // 2) Загружаем все видео страница за страницей до тех пор, пока не встретится
        // видео, которое уже было добавлено в локальную базу для этого плейлиста
        // 3) У видео есть дата, но ее использовать сложнее и накладнее, чем предложенный
        // алгоритм: во-первых, при загрузке информации о видео со страницы плейлиста
        // вместо даты загрузки приходит бесполезная поебень вида "1 год назад", "4 месяца назад" и т.п.;
        // на странице видео есть дата получше (например: "12 авг. 2008 г."),
        // но ее парсить тоже не очень удобно с учетом локализации и сокращений.

        final List<VideoItem> videoItems = new ArrayList<VideoItem>();

        final String plUrl = playlist.getUrl();

        NewPipe.init(DownloaderTestImpl.getInstance());
        final ListExtractor<StreamInfoItem> extractor;
        if (PlaylistUrlUtil.isYtChannel(plUrl) || PlaylistUrlUtil.isYtUser(plUrl)) {
            extractor = YouTube.getChannelExtractor(plUrl);
        } else if (PlaylistUrlUtil.isYtPlaylist(plUrl)) {
            extractor = YouTube.getPlaylistExtractor(plUrl);
        } else {
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        // список видео страница за страницей

        // начинаем с первой страницы
        // загрузить первую страницу
        extractor.fetchPage();
        ListExtractor.InfoItemsPage<StreamInfoItem> nextPage = extractor.getInitialPage();

        final List<StreamInfoItem> pageItems = new ArrayList<StreamInfoItem>();
        pageItems.addAll(nextPage.getItems());


        boolean foundOld = false;
        VideoDatabase videodb = VideoDatabase.getDb(context);
        for (StreamInfoItem item : pageItems) {
            final String ytId = PlaylistUrlUtil.getYtId(item.getUrl());
            if (videodb.videoItemDao().getByYtId(playlist.getId(), ytId) == null) {
                videoItems.add(extractVideoItem(item, playlist.getId(), playlist.isEnabled(), 0));
            } else {
                foundOld = true;
                break;
            }
        }
        // закроем базу здесь и откроем заново после того, как будет загружена
        // следующая страница, чтобы не оставить ее открытой в случае какого-нибудь
        // экспепнеша в процессе загрузки страницы
        videodb.close();

        // если на первой странице все ролики оказались новыми, продолжаем с остальными страницами
        // загружать по порядку остальные страницы до тех пор, пока не закончатся страницы
        // или не встретим ролик, который уже был добавлен в базу
        while (!foundOld && nextPage.hasNextPage()) {
            pageItems.clear();

            boolean done = false;
            // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
            // ошибку вместо страницы
            int retryCount = LOAD_PAGE_RETRY_COUNT;
            while (!done && retryCount > 0) {
                try {
                    nextPage = extractor.getPage(nextPage.getNextPage());
                    done = true;
                } catch (IOException | ExtractionException e) {
                    retryCount--;
                }
            }

            if (done) {
                // загрузили страницу - проверяем ролики
                pageItems.addAll(nextPage.getItems());
                videodb = VideoDatabase.getDb(context);
                for (StreamInfoItem item : pageItems) {
                    final String ytId = PlaylistUrlUtil.getYtId(item.getUrl());
                    if (videodb.videoItemDao().getByYtId(playlist.getId(), ytId) == null) {
                        videoItems.add(extractVideoItem(item, playlist.getId(), playlist.isEnabled(), 0));
                    } else {
                        foundOld = true;
                        break;
                    }
                }
                // закроем базу здесь и откроем заново после того, как будет загружена
                // следующая страница, чтобы не оставить ее открытой в случае какого-нибудь
                // экспепнеша в процессе загрузки страницы
                videodb.close();
            } else {
                // страница так и не загрузилась,
                // обрываем транзакцию с ошибкой
                throw new IOException("Error loading page, retry count exceeded");
            }
        }

        return videoItems;
    }

    public VideoItem getYtVideoItem(final String ytId) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamExtractorDefaultTest.java
        NewPipe.init(DownloaderTestImpl.getInstance());
        final YoutubeStreamExtractor extractor = (YoutubeStreamExtractor) YouTube
                .getStreamExtractor(PlaylistUrlUtil.getVideoUrl(ytId));
        extractor.fetchPage();
        return extractVideoItem(extractor);
    }

    public String extractYtStreamUrl(final String ytId) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamExtractorDefaultTest.java
        NewPipe.init(DownloaderTestImpl.getInstance());
        final YoutubeStreamExtractor extractor = (YoutubeStreamExtractor) YouTube
                .getStreamExtractor(PlaylistUrlUtil.getVideoUrl(ytId));
        extractor.fetchPage();
        // выбирать стрим с наилучшим качеством (в конце списка)
        final String streamUrl = extractor.getVideoStreams().size() > 0 ?
                extractor.getVideoStreams().get(extractor.getVideoStreams().size() - 1).getUrl() : null;
//        for (final VideoStream stream : extractor.getVideoStreams()) {
//            System.out.println(stream.getResolution() + " " + stream.getFormat().getName() + " " + stream.getFormat().getMimeType() + " " + stream.getFormat().getSuffix());
//        }
        return streamUrl;
    }

    private VideoItem extractVideoItem(final YoutubeStreamExtractor item) throws ParsingException {
        final String ytId = PlaylistUrlUtil.getYtId(item.getUrl());
        final String name = item.getName();
        final String uploader = item.getUploaderName();
        //final String date = item.getUploadDate();
        final long viewCount = 0;
        final long viewCountExt = item.getViewCount();
        // поймаем здесь
        // org.schabi.newpipe.extractor.exceptions.ParsingException: Could not get video length
        //final long duration = item.getLength();
        final long duration = 0;
        final String thumbUrl = item.getThumbnailUrl();

        return new VideoItem(-1, ytId, name, uploader, viewCount, viewCountExt, duration, thumbUrl);
    }

    /**
     *
     * @param item
     * @param playlistId playlist_id value to set
     * @param enabled enabled flag to set
     * @param fakeTimestamp fake_timestamp value to set
     * @return
     */
    private VideoItem extractVideoItem(final StreamInfoItem item, final long playlistId,
                                       final boolean enabled, final long fakeTimestamp) {
        //final long _playlistId = playlistId;
        final String ytId = PlaylistUrlUtil.getYtId(item.getUrl());
        final String name = item.getName();
        final String uploader = item.getUploaderName();
        //final String date = item.getUploadDate();
        final long viewCount = 0;
        final long viewCountExt = item.getViewCount();
        final long duration = item.getDuration();
        final String thumbUrl = item.getThumbnailUrl();
        //final long _fakeTimestamp = fakeTimestamp;

        return new VideoItem(playlistId, ytId, name, uploader, viewCount, viewCountExt, duration, thumbUrl, enabled, fakeTimestamp);
    }
}
