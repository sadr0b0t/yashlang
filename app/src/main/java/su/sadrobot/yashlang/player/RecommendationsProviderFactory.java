package su.sadrobot.yashlang.player;

/*
 * Copyright (C) Anton Moiseev 2023 <github.com/sadr0b0t>
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemArrayAdapter;
import su.sadrobot.yashlang.view.VideoItemMultPlaylistsOnlyNewOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemOnlyNewOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

public class RecommendationsProviderFactory {

    /**
     * Строка поиска для списка рекомендаций в режиме "результат поиска по запросу" или
     * фильтр плейлиста в режиме "плейлист по идентификатору"
     * (PARAM_RECOMMENDATIONS_MODE=RecommendationsMode.SEARCH_STR или RecommendationsMode.PLAYLIST_ID).
     * Список рекомендаций - все видео, найденные в базе по поисковой строке.
     *
     */
    public static final String PARAM_SEARCH_STR = "PARAM_SEARCH_STR";

    /**
     * Айди плейлиста для списка рекомендаций в режиме "плейлист по идентификатору"
     * (PARAM_RECOMMENDATIONS_MODE=RecommendationsMode.PLAYLIST_ID или RecommendationsMode.PLAYLIST_NEW).
     * Список рекомендаций - плейлист.
     */
    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";

    /**
     * Адрес плейлиста для списка рекомендаций в режиме "плейлист онлайн по адресу"
     * (PARAM_RECOMMENDATIONS_MODE=RecommendationsMode.PLAYLIST_URL).
     * Список рекомендаций - плейлист, загруженный онлайн.
     */
    public static final String PARAM_PLAYLIST_URL = "PARAM_PLAYLIST_URL";

    /**
     * Показыывать в рекомендациях все видео, а не только разрешенные (enabled): true/false
     * (в режиме PLAYLIST_ID)
     * По умолчанию: false
     */
    public static final String PARAM_SHOW_ALL = "PARAM_SHOW_ALL";

    /**
     * Перемешать рекоменации: true/false
     * (в режиме PLAYLIST_ID)
     * По умолчанию: false
     */
    public static final String PARAM_SHUFFLE = "PARAM_SHUFFLE";

    /**
     * Сортировать рекомендации: ConfigOptions.SortBy: TIME_ADDED, NAME, DURATION
     * (в режиме PLAYLIST_ID)
     * По умолчанию: false
     */
    public static final String PARAM_SORT_BY = "PARAM_SORT_BY";

    /**
     * Сортировать рекомендации по возрастанию или по убыванию: true/false
     * (в режиме PLAYLIST_ID)
     * По умолчанию: false (сортировать по убыванию)
     */
    public static final String PARAM_SORT_DIR_ASCENDING = "PARAM_SORT_DIR_ASCENDING";

    /**
     * Режимы для списка рекомендаций
     */
    public enum RecommendationsMode {
        OFF, RANDOM, PLAYLIST_ID, PLAYLIST_URL, PLAYLIST_NEW, ALL_NEW, SEARCH_STR, STARRED
    }


    public static RecommendationsProvider buildRecommendationsProvider(
            final RecommendationsMode recommendationsMode, final long firstItemId, final Intent intent) {
        final RecommendationsProvider recommendationsProvider;
        switch (recommendationsMode) {
            case SEARCH_STR: {
                final String searchStr = intent.getStringExtra(PARAM_SEARCH_STR);
                final boolean shuffle = intent.getBooleanExtra(PARAM_SHUFFLE, false);

                if (!shuffle) {
                    recommendationsProvider = buildVideoListSearchPagedListAdapter(searchStr);
                } else {
                    recommendationsProvider = buildVideoListSearchShuffleArrayAdapter(searchStr);
                }

                break;
            }
            case STARRED: {
                final boolean shuffle = intent.getBooleanExtra(PARAM_SHUFFLE, false);

                if (!shuffle) {
                    recommendationsProvider = buildVideoListStarredPagedListAdapter();
                } else {
                    recommendationsProvider = buildVideoListStarredShuffleArrayAdapter();
                }

                break;
            }
            case PLAYLIST_ID: {
                final long playlistId = intent.getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);
                final boolean showAll = intent.getBooleanExtra(PARAM_SHOW_ALL, false);
                final boolean shuffle = intent.getBooleanExtra(PARAM_SHUFFLE, false);
                final String searchStr = intent.getStringExtra(PARAM_SEARCH_STR);
                final ConfigOptions.SortBy sortBy = intent.hasExtra(PARAM_SORT_BY) ?
                        ConfigOptions.SortBy.valueOf(intent.getStringExtra(PARAM_SORT_BY)) : null;
                final boolean sortDirAsc = intent.getBooleanExtra(PARAM_SORT_DIR_ASCENDING, false);

                if (!shuffle) {
                    recommendationsProvider = buildVideoListPlaylistPagedListAdapter(playlistId, showAll, searchStr, sortBy, sortDirAsc, firstItemId);
                } else {
                    recommendationsProvider = buildVideoListPlaylistShuffleArrayAdapter(playlistId, searchStr, firstItemId);
                }

                break;
            }
            case PLAYLIST_URL: {
                final String playlistUrl = intent.getStringExtra(PARAM_PLAYLIST_URL);
                recommendationsProvider = buildVideoListPlaylistOnlinePagedListAdapter(playlistUrl);

                break;
            }
            case PLAYLIST_NEW: {
                final long playlistId = intent.getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);
                recommendationsProvider = buildVideoListPlaylistNewPagedListAdapter(playlistId);

                break;
            }
            case ALL_NEW: {
                recommendationsProvider = buildVideoListAllNewPagedListAdapter();

                break;
            }
            case RANDOM: {
                recommendationsProvider = buildVideoListRandomArrayAdapter(firstItemId);

                break;
            }
            case OFF:
            default:
                recommendationsProvider = null;
        }
        return recommendationsProvider;
    }

    /**
     * Случайные рекомандации внизу под основным видео. ArrayAdapter, а не PagedListAdapter
     * потому, что в случае с PagedListAdapter выдача рекомендаций будет автоматом обновляться
     * при каждой записи в базу (например, при переключении видео с сохранением текущей позиции
     * или при клике на кнопку со звездочкой).
     */
    private static RecommendationsProvider buildVideoListRandomArrayAdapter(final long firstItemId) {
        return new RecommendationsProvider() {

            // рекомендации
            private VideoItemArrayAdapter videoListAdapter;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {

                final List<VideoItem> videoItems;
                if (firstItemId == VideoItem.ID_NONE) {
                    videoItems =VideoDatabase.getDbInstance(dataContext).
                            videoItemPubListsDao().recommendVideos(ConfigOptions.RECOMMENDED_RANDOM_LIM);
                } else {
                    videoItems =VideoDatabase.getDbInstance(dataContext).
                            videoItemPubListsDao().recommendVideosWithFirstItem(ConfigOptions.RECOMMENDED_RANDOM_LIM, firstItemId);
                }

                videoListAdapter = new VideoItemArrayAdapter(
                        uiContext, videoItems, null, null, VideoItemArrayAdapter.ORIENTATION_HORIZONTAL);

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListSearchShuffleArrayAdapter(final String searchStr) {
        return new RecommendationsProvider() {

            // рекомендации
            private VideoItemArrayAdapter videoListAdapter;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                final List<VideoItem> videoItems = VideoDatabase.getDbInstance(dataContext).
                        videoItemPubListsDao().searchVideosShuffle(searchStr, ConfigOptions.RECOMMENDED_RANDOM_LIM);
                videoListAdapter = new VideoItemArrayAdapter(
                        uiContext, videoItems, null, null, VideoItemArrayAdapter.ORIENTATION_HORIZONTAL);

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListStarredShuffleArrayAdapter() {
        return new RecommendationsProvider() {

            // рекомендации
            private VideoItemArrayAdapter videoListAdapter;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                final List<VideoItem> videoItems = VideoDatabase.getDbInstance(dataContext).
                        videoItemPubListsDao().getStarredShuffle(ConfigOptions.RECOMMENDED_RANDOM_LIM);
                videoListAdapter = new VideoItemArrayAdapter(
                        uiContext, videoItems, null, null, VideoItemArrayAdapter.ORIENTATION_HORIZONTAL);

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListPlaylistShuffleArrayAdapter(
            final long playlistId, final String searchStr, final long firstItemId) {
        return new RecommendationsProvider() {

            // рекомендации
            private VideoItemArrayAdapter videoListAdapter;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                final List<VideoItem> videoItems;
                if (searchStr != null) {
                    if (firstItemId == VideoItem.ID_NONE) {
                        videoItems = VideoDatabase.getDbInstance(dataContext).
                                videoItemPubListsDao().getByPlaylistShuffle(playlistId, searchStr, ConfigOptions.RECOMMENDED_RANDOM_LIM);
                    } else {
                        videoItems = VideoDatabase.getDbInstance(dataContext).
                                videoItemPubListsDao().getByPlaylistShuffleWithFirstItem(playlistId, searchStr, ConfigOptions.RECOMMENDED_RANDOM_LIM, firstItemId);
                    }
                } else {
                    if (firstItemId == VideoItem.ID_NONE) {
                        videoItems = VideoDatabase.getDbInstance(dataContext).
                                videoItemPubListsDao().getByPlaylistShuffle(playlistId, ConfigOptions.RECOMMENDED_RANDOM_LIM);
                    } else {
                        videoItems = VideoDatabase.getDbInstance(dataContext).
                                videoItemPubListsDao().getByPlaylistShuffleWithFirstItem(playlistId, ConfigOptions.RECOMMENDED_RANDOM_LIM, firstItemId);
                    }
                }
                videoListAdapter = new VideoItemArrayAdapter(
                        uiContext, videoItems, null, null, VideoItemArrayAdapter.ORIENTATION_HORIZONTAL);

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListSearchPagedListAdapter(final String searchStr) {
        return new RecommendationsProvider() {
            // контекст
            private LifecycleOwner currLifecycleOwner;
            private RecyclerView.AdapterDataObserver currDataObserver;

            // рекомендации
            private VideoItemPagedListAdapter videoListAdapter;
            private LiveData<PagedList<VideoItem>> videoItemsLiveData;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                videoListAdapter = new VideoItemPagedListAdapter(
                        uiContext, null, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

                final DataSource.Factory factory = VideoDatabase.getDbInstance(dataContext).videoItemPubListsDao().searchVideosDs(searchStr);

                // Initial page size to fetch can also be configured here too
                final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
                videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // почистить старые значения
                if (videoItemsLiveData != null) {
                    videoItemsLiveData.removeObservers(currLifecycleOwner);
                }
                currLifecycleOwner = lifecycleOwner;

                if (currDataObserver != null) {
                    videoListAdapter.unregisterAdapterDataObserver(currDataObserver);
                }
                currDataObserver = dataObserver;

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                // события изменения данных
                videoListAdapter.registerAdapterDataObserver(dataObserver);

                // LifecycleOwner сейча AppCompatActivity - WatchVideoActivity
                // если наследовать PlayerService от LifecycleService, то это участок не будет
                // зависить от текущей Activity и можно будет перенести эту часть кода в createVideoListAdapter,
                // но пока пусть буюет так
                // https://stackoverflow.com/questions/57229877/how-to-cast-lifecycleowner-on-service-class
                videoItemsLiveData.observe(lifecycleOwner, new Observer<PagedList<VideoItem>>() {
                    @Override
                    public void onChanged(@Nullable PagedList<VideoItem> videos) {
                        videoListAdapter.submitList(videos);
                    }
                });

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListStarredPagedListAdapter() {
        return new RecommendationsProvider() {
            // контекст
            private LifecycleOwner currLifecycleOwner;
            private RecyclerView.AdapterDataObserver currDataObserver;

            // рекомендации
            private VideoItemPagedListAdapter videoListAdapter;
            private LiveData<PagedList<VideoItem>> videoItemsLiveData;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                videoListAdapter = new VideoItemPagedListAdapter(
                        uiContext, null, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

                final DataSource.Factory factory = VideoDatabase.getDbInstance(dataContext).videoItemPubListsDao().getStarredDs();

                // Initial page size to fetch can also be configured here too
                final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
                videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // почистить старые значения
                if (videoItemsLiveData != null) {
                    videoItemsLiveData.removeObservers(currLifecycleOwner);
                }
                currLifecycleOwner = lifecycleOwner;

                if (currDataObserver != null) {
                    videoListAdapter.unregisterAdapterDataObserver(currDataObserver);
                }
                currDataObserver = dataObserver;

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                // события изменения данных
                videoListAdapter.registerAdapterDataObserver(dataObserver);

                // LifecycleOwner сейча AppCompatActivity - WatchVideoActivity
                // если наследовать PlayerService от LifecycleService, то это участок не будет
                // зависить от текущей Activity и можно будет перенести эту часть кода в createVideoListAdapter,
                // но пока пусть буюет так
                // https://stackoverflow.com/questions/57229877/how-to-cast-lifecycleowner-on-service-class
                videoItemsLiveData.observe(lifecycleOwner, new Observer<PagedList<VideoItem>>() {
                    @Override
                    public void onChanged(@Nullable PagedList<VideoItem> videos) {
                        videoListAdapter.submitList(videos);
                    }
                });

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListPlaylistPagedListAdapter(
            final long playlistId, final boolean showAll,
            final String searchStr,
            final ConfigOptions.SortBy sortBy, final boolean sortDirAsc,
            long firstItemId) {
        return new RecommendationsProvider() {
            // контекст
            private LifecycleOwner currLifecycleOwner;
            private RecyclerView.AdapterDataObserver currDataObserver;

            // рекомендации
            private VideoItemPagedListAdapter videoListAdapter;
            private LiveData<PagedList<VideoItem>> videoItemsLiveData;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                videoListAdapter = new VideoItemPagedListAdapter(
                        uiContext, null, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

                final DataSource.Factory factory;

                if (showAll) {
                    if (searchStr != null && !searchStr.isEmpty()) {
                        factory = VideoDatabase.getDbInstance(dataContext).videoItemDao().getByPlaylistAllDs(playlistId, searchStr);
                    } else {
                        factory = VideoDatabase.getDbInstance(dataContext).videoItemDao().getByPlaylistAllDs(playlistId);
                    }
                } else {
                    if (sortBy == null) {
                        if (firstItemId == VideoItem.ID_NONE) {
                            factory = VideoDatabase.getDbInstance(dataContext).videoItemPubListsDao().getByPlaylistDs(playlistId);
                        } else {
                            factory = VideoDatabase.getDbInstance(dataContext).videoItemPubListsDao().getByPlaylistWithFirstItemDs(playlistId, firstItemId);
                        }
                    } else if (sortBy == ConfigOptions.SortBy.NAME) {
                        if (sortDirAsc) {
                            factory = VideoDatabase.getDbInstance(
                                    dataContext).videoItemPubListsDao().getByPlaylistSortByNameAscDs(playlistId, searchStr);
                        } else {
                            factory = VideoDatabase.getDbInstance(
                                    dataContext).videoItemPubListsDao().getByPlaylistSortByNameDescDs(playlistId, searchStr);
                        }
                    } else if (sortBy == ConfigOptions.SortBy.DURATION) {
                        if (sortDirAsc) {
                            factory = VideoDatabase.getDbInstance(
                                    dataContext).videoItemPubListsDao().getByPlaylistSortByDurationAscDs(playlistId, searchStr);
                        } else {
                            factory = VideoDatabase.getDbInstance(
                                    dataContext).videoItemPubListsDao().getByPlaylistSortByDurationDescDs(playlistId, searchStr);
                        }
                    } else { // TIME_ADDED
                        if (sortDirAsc) {
                            factory = VideoDatabase.getDbInstance(
                                    dataContext).videoItemPubListsDao().getByPlaylistSortByTimeAddedAscDs(playlistId, searchStr);
                        } else {
                            factory = VideoDatabase.getDbInstance(
                                    dataContext).videoItemPubListsDao().getByPlaylistSortByTimeAddedDescDs(playlistId, searchStr);
                        }
                    }
                }

                // Initial page size to fetch can also be configured here too
                final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
                videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // почистить старые значения
                if (videoItemsLiveData != null) {
                    videoItemsLiveData.removeObservers(currLifecycleOwner);
                }
                currLifecycleOwner = lifecycleOwner;

                if (currDataObserver != null) {
                    videoListAdapter.unregisterAdapterDataObserver(currDataObserver);
                }
                currDataObserver = dataObserver;

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                // события изменения данных
                videoListAdapter.registerAdapterDataObserver(dataObserver);

                // LifecycleOwner сейча AppCompatActivity - WatchVideoActivity
                // если наследовать PlayerService от LifecycleService, то это участок не будет
                // зависить от текущей Activity и можно будет перенести эту часть кода в createVideoListAdapter,
                // но пока пусть буюет так
                // https://stackoverflow.com/questions/57229877/how-to-cast-lifecycleowner-on-service-class
                videoItemsLiveData.observe(lifecycleOwner, new Observer<PagedList<VideoItem>>() {
                    @Override
                    public void onChanged(@Nullable PagedList<VideoItem> videos) {
                        videoListAdapter.submitList(videos);
                    }
                });

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListPlaylistOnlinePagedListAdapter(final String playlistUrl) {
        return new RecommendationsProvider() {
            // контекст
            private LifecycleOwner currLifecycleOwner;
            private RecyclerView.AdapterDataObserver currDataObserver;

            // рекомендации
            private VideoItemPagedListAdapter videoListAdapter;
            private LiveData<PagedList<VideoItem>> videoItemsLiveData;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                videoListAdapter = new VideoItemPagedListAdapter(
                        uiContext, null, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

                final DataSource.Factory factory = new VideoItemOnlineDataSourceFactory(dataContext, playlistUrl, false, null);

                // Initial page size to fetch can also be configured here too
                final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
                videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // почистить старые значения
                if (videoItemsLiveData != null) {
                    videoItemsLiveData.removeObservers(currLifecycleOwner);
                }
                currLifecycleOwner = lifecycleOwner;

                if (currDataObserver != null) {
                    videoListAdapter.unregisterAdapterDataObserver(currDataObserver);
                }
                currDataObserver = dataObserver;

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                // события изменения данных
                videoListAdapter.registerAdapterDataObserver(dataObserver);

                // LifecycleOwner сейча AppCompatActivity - WatchVideoActivity
                // если наследовать PlayerService от LifecycleService, то это участок не будет
                // зависить от текущей Activity и можно будет перенести эту часть кода в createVideoListAdapter,
                // но пока пусть буюет так
                // https://stackoverflow.com/questions/57229877/how-to-cast-lifecycleowner-on-service-class
                videoItemsLiveData.observe(lifecycleOwner, new Observer<PagedList<VideoItem>>() {
                    @Override
                    public void onChanged(@Nullable PagedList<VideoItem> videos) {
                        videoListAdapter.submitList(videos);
                    }
                });

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListPlaylistNewPagedListAdapter(final long playlistId) {
        return new RecommendationsProvider() {
            // контекст
            private LifecycleOwner currLifecycleOwner;
            private RecyclerView.AdapterDataObserver currDataObserver;

            // рекомендации
            private VideoItemPagedListAdapter videoListAdapter;
            private LiveData<PagedList<VideoItem>> videoItemsLiveData;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                videoListAdapter = new VideoItemPagedListAdapter(
                        uiContext, null, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

                final DataSource.Factory factory = new VideoItemOnlyNewOnlineDataSourceFactory(dataContext, playlistId, false, null);

                // Initial page size to fetch can also be configured here too
                final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
                videoItemsLiveData = new LivePagedListBuilder(factory, config).build();
                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // почистить старые значения
                if (videoItemsLiveData != null) {
                    videoItemsLiveData.removeObservers(currLifecycleOwner);
                }
                currLifecycleOwner = lifecycleOwner;

                if (currDataObserver != null) {
                    videoListAdapter.unregisterAdapterDataObserver(currDataObserver);
                }
                currDataObserver = dataObserver;

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                // события изменения данных
                videoListAdapter.registerAdapterDataObserver(dataObserver);

                // LifecycleOwner сейча AppCompatActivity - WatchVideoActivity
                // если наследовать PlayerService от LifecycleService, то это участок не будет
                // зависить от текущей Activity и можно будет перенести эту часть кода в createVideoListAdapter,
                // но пока пусть буюет так
                // https://stackoverflow.com/questions/57229877/how-to-cast-lifecycleowner-on-service-class
                videoItemsLiveData.observe(lifecycleOwner, new Observer<PagedList<VideoItem>>() {
                    @Override
                    public void onChanged(@Nullable PagedList<VideoItem> videos) {
                        videoListAdapter.submitList(videos);
                    }
                });

                videoList.setAdapter(videoListAdapter);
            }
        };
    }

    private static RecommendationsProvider buildVideoListAllNewPagedListAdapter() {
        return new RecommendationsProvider() {
            // контекст
            private LifecycleOwner currLifecycleOwner;
            private RecyclerView.AdapterDataObserver currDataObserver;

            // рекомендации
            private VideoItemPagedListAdapter videoListAdapter;
            private LiveData<PagedList<VideoItem>> videoItemsLiveData;

            @Override
            public RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext) {
                videoListAdapter = new VideoItemPagedListAdapter(
                        uiContext, null, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

                final List<Long> plIds = VideoDatabase.getDbInstance(dataContext).playlistInfoDao().getAllIds();
                final DataSource.Factory factory = new VideoItemMultPlaylistsOnlyNewOnlineDataSourceFactory(
                                dataContext, plIds, false, null);

                // Initial page size to fetch can also be configured here too
                final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
                videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

                return videoListAdapter;
            }

            @Override
            public RecyclerView.Adapter getVideoListAdapter() {
                return videoListAdapter;
            }

            @Override
            public void setupVideoList(
                    final Activity context, final LifecycleOwner lifecycleOwner,
                    final RecyclerView videoList, final RecyclerView.AdapterDataObserver dataObserver,
                    final OnListItemClickListener<VideoItem> onItemClickListener) {

                // почистить старые значения
                if (videoItemsLiveData != null) {
                    videoItemsLiveData.removeObservers(currLifecycleOwner);
                }
                currLifecycleOwner = lifecycleOwner;

                if (currDataObserver != null) {
                    videoListAdapter.unregisterAdapterDataObserver(currDataObserver);
                }
                currDataObserver = dataObserver;

                // настроить адаптер
                videoListAdapter.setContext(context);
                videoListAdapter.setOnItemClickListener(onItemClickListener);

                // события изменения данных
                videoListAdapter.registerAdapterDataObserver(dataObserver);

                // LifecycleOwner сейча AppCompatActivity - WatchVideoActivity
                // если наследовать PlayerService от LifecycleService, то это участок не будет
                // зависить от текущей Activity и можно будет перенести эту часть кода в createVideoListAdapter,
                // но пока пусть буюет так
                // https://stackoverflow.com/questions/57229877/how-to-cast-lifecycleowner-on-service-class
                videoItemsLiveData.observe(lifecycleOwner, new Observer<PagedList<VideoItem>>() {
                    @Override
                    public void onChanged(@Nullable PagedList<VideoItem> videos) {
                        videoListAdapter.submitList(videos);
                    }
                });

                videoList.setAdapter(videoListAdapter);
            }
        };
    }
}
