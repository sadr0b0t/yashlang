package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * WatchVideoActivity.java is part of YaShlang.
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

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.Stack;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;


public class WatchVideoActivity extends AppCompatActivity {
    // https://github.com/google/ExoPlayer
    // https://exoplayer.dev/
    // https://exoplayer.dev/hello-world.html
    // https://exoplayer.dev/ui-components.html
    // https://androidwave.com/play-youtube-video-in-exoplayer/


    // https://github.com/PierfrancescoSoffritti/android-youtube-player
    // How to hide "more videos" row when video paused?
    // https://github.com/PierfrancescoSoffritti/android-youtube-player/issues/226
    // https://github.com/PierfrancescoSoffritti/android-youtube-player/blob/master/core-sample-app/src/main/java/com/pierfrancescosoffritti/androidyoutubeplayer/core/sampleapp/examples/iFramePlayerOptionsExample/IFramePlayerOptionsExampleActivity.java
    //
    // https://stackoverflow.com/questions/15833889/options-for-replacing-the-deprecated-gallery
    // https://gist.github.com/devunwired/8cbe094bb7a783e37ad1
    // https://github.com/falnatsheh/EcoGallery
    // https://commonsware.com/blog/2012/08/20/multiple-view-viewpager-options.html
    // https://developer.android.com/reference/android/support/v7/widget/RecyclerView.html
    // https://androidclarified.com/viewpager-example-sliding-images/
    // https://www.journaldev.com/10096/android-viewpager-example-tutorial


    /**
     * Загрузить информацию о видео по ID из базы
     */
    public static final String PARAM_VIDEO_ID = "PARAM_VIDEO_ID";
    /**
     * Загрузить информацию о видео онлайн
     */
    public static final String PARAM_VIDEO_YTID = "PARAM_VIDEO_YTID";


    private PlayerView videoPlayerView;
    private View videoInfoView;
    private TextView videoInfoTxt;
    private Switch starredSwitch;
    private Button goAwayBtn;
    private Button prevVideoBtn;
    private RecyclerView videoList;

    private DefaultDataSourceFactory videoDataSourceFactory;

    private VideoItem currentVideo;
    private Stack<VideoItem> playbackHistory = new Stack<VideoItem>();

    private boolean stateFullscreen = false;

    // рекомендации
    private LiveData<PagedList<VideoItem>> videoItemsLiveData;
    private VideoDatabase videodb;

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_watch_video);

        videoPlayerView = findViewById(R.id.video_player_view);
        videoInfoView = findViewById(R.id.video_info_view);
        videoInfoTxt = findViewById(R.id.video_info_txt);
        starredSwitch = findViewById(R.id.starred_switch);
        goAwayBtn = findViewById(R.id.go_away_btn);
        prevVideoBtn = findViewById(R.id.prev_video_btn);
        videoInfoTxt = findViewById(R.id.video_info_txt);
        videoList = findViewById(R.id.video_recommend_list);

        // Ручные настройки анимации вместо параметра
        //     android:animateLayoutChanges="true"
        // https://stackoverflow.com/questions/19943466/android-animatelayoutchanges-true-what-can-i-do-if-the-fade-out-effect-is-un
//        final LayoutTransition lt = new LayoutTransition();
//        lt.disableTransitionType(LayoutTransition.DISAPPEARING);
//        contentView.setLayoutTransition(lt);

        prevVideoBtn.setEnabled(false);

        goAwayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WatchVideoActivity.this.finish();
            }
        });

        prevVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playbackHistory.size() > 1) {
                    playbackHistory.pop();
                    playVideoItem(playbackHistory.pop());

                    if (playbackHistory.size() <= 1) {
                        prevVideoBtn.setEnabled(false);
                    }
                }
            }
        });

        // Плеер
        final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        final TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        final TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        final SimpleExoPlayer exoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        videoDataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "yashlang"), bandwidthMeter);

        videoPlayerView.setUseController(true);
        videoPlayerView.requestFocus();
        videoPlayerView.setPlayer(exoPlayer);

        // Мы можем запретить прятать элементы управления по клику на области рядом с видео, но
        // мы не можем запретить показывать их по клику на этой же области, поэтому пусть
        // пока и прятать будет можно.
        // Хотелось изначально: будем прятать элементы управления в полноэкранном режиме при клике по плееру
        // и всегда показывать в режиме с уменьшенным экраном видео с кнопками управления
        // и списком рекомендаций.
        // (Если оставить флаг true, кнопки управления с полосой прокрутки можно будет дополнительно
        // прятать кликом слева или справа от видео)
        //videoPlayerView.setControllerHideOnTouch(false);

        // https://stackoverflow.com/questions/52365953/exoplayer-playerview-onclicklistener-not-working
        videoPlayerView.getVideoSurfaceView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFullscreen();
            }
        });

        videoPlayerView.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchSetPlayWhenReady(final Player player, final boolean playWhenReady) {
                // https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
                // определить, что пользователь кникнул на паузу
                if (playWhenReady) {
                    // Play button clicked
                } else {
                    //System.out.println("#### PAUSED AT " + player.getCurrentPosition());
                    // Paused button clicked
                    saveVideoCurrPos();
                }
                return super.dispatchSetPlayWhenReady(player, playWhenReady);
            }

            @Override
            public boolean dispatchSeekTo(final Player player, final int windowIndex, final long positionMs) {
                saveVideoCurrPos();
                return super.dispatchSeekTo(player, windowIndex, positionMs);
            }
        });

        // Рекомендации
        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(
                getApplicationContext(), RecyclerView.HORIZONTAL, false);
        videoList.setLayoutManager(linearLayoutManager);

        // подключимся к базе один раз при создании активити,
        // закрывать подключение в onDestroy
        videodb = VideoDatabase.getDb(WatchVideoActivity.this);

        // загружаем видео
        // если передан параметр videoId, то загружаем видео по id из базы, если videoId
        // нет, но есть videoYtId, то используем его
        final long videoId = super.getIntent().getLongExtra(PARAM_VIDEO_ID, -1);
        final String videoYtId = super.getIntent().getStringExtra(PARAM_VIDEO_YTID);
        if (videoId != -1) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final VideoItem videoItem = videodb.videoItemDao().getById(videoId);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playVideoItem(videoItem);
                        }
                    });
                }
            }).start();
        } else if (videoYtId != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // грузим информацию из онлайна
                    VideoItem _videoItem;
                    try {
                        _videoItem = ContentLoader.getInstance().getYtVideoItem(videoYtId);
                    } catch (ExtractionException | IOException e) {
                        _videoItem = null;
                        //e.printStackTrace();
                    }

                    final VideoItem videoItem = _videoItem;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playVideoItem(videoItem);
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        videoPlayerView.getPlayer().setPlayWhenReady(false);
        saveVideoCurrPos();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // В onResume, т.к. после сворачивания и разворачивания приложения (или после выключения и
        // включения экрана) панель навигации появляется опять.
        // Чтобы в полном экране спрятать виртуальную панельку навигации не достаточно флагов в styles.xml
        // https://stackoverflow.com/questions/14178237/setsystemuivisibilitysystem-ui-flag-layout-hide-navigation-does-not-work
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        //
        setupVideoListAdapter();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (videodb != null) {
            videodb.close();
        }

        videoPlayerView.getPlayer().release();
    }

    private void toggleFullscreen() {
        stateFullscreen = !stateFullscreen;
        if (stateFullscreen) {
            goAwayBtn.setVisibility(View.GONE);
            prevVideoBtn.setVisibility(View.GONE);
            videoInfoView.setVisibility(View.GONE);
            videoList.setVisibility(View.GONE);

            videoPlayerView.hideController();
            // продолжить играть, если была пауза
            videoPlayerView.getPlayer().setPlayWhenReady(true);
        } else {
            goAwayBtn.setVisibility(View.VISIBLE);
            prevVideoBtn.setVisibility(View.VISIBLE);
            videoInfoView.setVisibility(View.VISIBLE);
            videoList.setVisibility(View.VISIBLE);

            videoPlayerView.showController();
        }
    }

    // Сохраним текущую позицию видео в базу
    private void saveVideoCurrPos() {
        // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
        // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
        final VideoItem _currentVideo = currentVideo;
        final long _currentPos = videoPlayerView.getPlayer().getCurrentPosition();
        // для текущего кэша, да
        if(currentVideo != null) {
            currentVideo.setPausedAt(_currentPos);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (_currentVideo != null) {
                    videodb.videoItemDao().setPausedAt(_currentVideo.getId(), _currentPos);
                }
            }
        }).start();
    }

    private void playVideoItem(final VideoItem videoItem) {
        saveVideoCurrPos();
        this.currentVideo = videoItem;
        if (videoItem != null) {
            if (playbackHistory.size() == 0 || !videoItem.getYtId().equals(playbackHistory.peek())) {
                playbackHistory.push(videoItem);
            }
            if (playbackHistory.size() > 1) {
                prevVideoBtn.setEnabled(true);
            }

            // показать информацию о ролике
            videoInfoTxt.setText(videoItem.getName());

            // передобавлять слушателя здесь, чтобы лишний раз не перезаписывать
            // звездочку в базе
            starredSwitch.setOnCheckedChangeListener(null);
            starredSwitch.setChecked(videoItem.isStarred());
            starredSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                    // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
                    // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
                    final VideoItem _currentVideo = currentVideo;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (_currentVideo != null) {
                                videodb.videoItemDao().setStarred(_currentVideo.getId(), isChecked);
                                // обновим кэш
                                _currentVideo.setStarred(isChecked);
                            }
                        }
                    }).start();
                }
            });

            // теперь то, что в фоне
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // посчитать просмотр
                        videodb.videoItemDao().countView(videoItem.getId());

                        // загрузить поток видео
                        final String vidStreamUrl = ContentLoader.getInstance().extractYtStreamUrl(videoItem.getYtId());
                        if (vidStreamUrl != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    playVideoStream(vidStreamUrl, videoItem.getPausedAt());
                                }
                            });
                        } else {
                            // TODO: здесь может быть NULL для некоторых роликов
                            // (например, см ролик "Топ мультиков Союзмультфильм")
                            // Автоматически блеклистить?
                            // Но если блеклистим автоматом, то нужно решить, что показывать
                            // вместо этого видео
                        }
                    } catch (ExtractionException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private void playVideoStream(final String streamUrl, final long seekTo) {
        // https://exoplayer.dev/
        // https://github.com/google/ExoPlayer
        // https://androidwave.com/play-youtube-video-in-exoplayer/

        // датасорсы к видео в плеере NewPipe:
        // - про продолжение с установленной позиции в коде не вижу или не нашел
        // - (как играть видео конкретно с ютюба не вижу тоже, там ацкий ООП)
        // - короче, толку от них ноль, пусть будут пока ссылки для справки
        // https://github.com/TeamNewPipe/NewPipe/blob/master/app/src/main/java/org/schabi/newpipe/player/helper/PlayerDataSource.java
        // https://github.com/TeamNewPipe/NewPipe/blob/master/app/src/main/java/org/schabi/newpipe/player/resolver/PlaybackResolver.java

        Uri mp4VideoUri = Uri.parse(streamUrl);
        MediaSource videoSource;
        if (streamUrl.toUpperCase().contains("M3U8")) {
            videoSource = new HlsMediaSource(mp4VideoUri, videoDataSourceFactory, null, null);
        } else {
            mp4VideoUri = Uri.parse(streamUrl);
            videoSource = new ExtractorMediaSource(mp4VideoUri, videoDataSourceFactory, new DefaultExtractorsFactory(),
                    null, null);
        }

        // Поставим на паузу старое видео, пока готовим новое
        videoPlayerView.getPlayer().setPlayWhenReady(false);
        // Prepare the player with the source.
        ((SimpleExoPlayer) videoPlayerView.getPlayer()).prepare(videoSource);

        // Укажем текущую позицию сразу при загрузке видео
        // (в коментах что-то пишут что-то про датасорсы, которые поддерживают или не поддерживают
        // переходы seek при загрузке, похоже, что это фигня - просто делаем seek сразу после загрузки)
        // Exoplayer plays new Playlist from the beginning instead of provided position
        // https://github.com/google/ExoPlayer/issues/4375
        // How to load stream in the desired position? #2197
        // https://github.com/google/ExoPlayer/issues/2197
        // в этом месте нормлаьный duration еще не доступен, поэтому его не проверяем
        //if(seekTo > 0 && seekTo < videoPlayerView.getPlayer().getDuration()) {
        if(seekTo > 0) {
            // на 5 секунд раньше
            videoPlayerView.getPlayer().seekTo(seekTo - 5000 > 0 ? seekTo - 5000 : 0);
        }
        videoPlayerView.getPlayer().setPlayWhenReady(true);
    }


    private void setupVideoListAdapter() {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(
                this, new OnListItemClickListener<VideoItem>() {
            @Override
            public void onItemClick(final View view, final int position, final VideoItem item) {
                playVideoItem(item);
            }

            @Override
            public boolean onItemLongClick(View view, int position, VideoItem item) {
                Toast.makeText(WatchVideoActivity.this, position + ":" +
                                item.getId() + ":" + item.getThumbUrl(),
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }, null, VideoItemPagedListAdapter.ORIENTATION_HORIZONTAL);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory =
                videodb.videoItemDao().recommendVideosDs();

        videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

        videoItemsLiveData.observe(this, new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }
}
