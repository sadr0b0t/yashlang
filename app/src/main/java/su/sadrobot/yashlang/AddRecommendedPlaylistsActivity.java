package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * AddPlaylistActivity.java is part of YaShlang.
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

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class AddRecommendedPlaylistsActivity extends AppCompatActivity {

    private View recommendedPlaylistsView;
    private Button playlistsAddBtn;
    private RecyclerView playlistList;

    private View playlistsAddProgressView;
    private ImageView playlistAddPlThumbImg;
    private TextView playlistAddPlNameTxt;
    private TextView playlistAddPlUrlTxt;
    private TextView playlistAddStatusTxt;
    private ProgressBar playlistAddProgress;

    private View playlistAddErrorView;
    private TextView playlistAddErrorTxt;
    private Button playlistAddRetryBtn;
    private Button playlistAddSkipBtn;
    private Button playlistAddCancelBtn;

    private Handler handler = new Handler();

    private int plToAddStartIndex = 0;


    private PlaylistInfo[] recommendedPlaylists = {
            new PlaylistInfo("Мультики студии Союзмультфильм",
                    "https://www.youtube.com/user/ClassicCartoonsMedia",
                    "https://yt3.ggpht.com/a/AGF-l79QQ2pizTFB3G61q3I4ryzr659sbHFt5TffzQ=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Киножурнал Ералаш",
                    "https://www.youtube.com/channel/UC0u6gAESA0XmSJQaAyDTTVg",
                    "https://yt3.ggpht.com/a/AGF-l7_hKI23Rm_DGUcoN7JFm2tKQl2maXaQdAJbqA=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Спокойной ночи, малыши!",
                    "https://www.youtube.com/channel/UCoVS2PU1QJpXrO9oTyke2cw",
                    "https://yt3.ggpht.com/a/AGF-l7_uiPDLitSDNNS9ehGNte53CdDDml2D7lK99g=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("СМОТРИМ. Русские мультфильмы",
                    "https://youtube.com/channel/UCU06hfFzcBjQl9-Ih2SvpoQ",
                    "https://yt3.ggpht.com/a/AGF-l7-gkH8i4mTtUaGQuzHz2JriteeLSioPlqzS2Q=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Гора самоцветов (Mountain of Gems)",
                    "https://www.youtube.com/channel/UCexc-emEni9lvU5jbTqLEkw",
                    "https://yt3.ggpht.com/a/AGF-l7_xkC44VNzYDmi61jX6bSLyXw4GE3DprxAPZA=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("TVSmeshariki",
                    "https://www.youtube.com/user/TVSmeshariki",
                    "https://yt3.ggpht.com/a/AGF-l7_2xR6oqSIrUzTH_LzZdBXwyFPCPtM0SKKsBQ=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Малышарики",
                    "https://www.youtube.com/channel/UCn8CnDy4-uzkxXzXPSzQZ0A",
                    "https://yt3.ggpht.com/a/AGF-l78cHA23WW5sKEE2qr5zzRSP9ncYeHfK-0Gf7g=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Маша и Медведь",
                    "https://www.youtube.com/user/MashaMedvedTV",
                    "https://yt3.ggpht.com/a/AGF-l78kf4dz0_5f84x2Wjmle_4oQ8gz7dRApCZeRw=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Мульт",
                    "https://www.youtube.com/channel/UCM3vklq_KgWZDmbfg_0Yg_A",
                    "https://yt3.ggpht.com/a/AGF-l7-BrNjRT8XO5uPU6nnIju-t4aI4ZsRYGFfllA=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Везуха! / LUCKY!",
                    "https://www.youtube.com/user/VezuhaTV",
                    "https://yt3.ggpht.com/a/AGF-l7_dooQIqq5Ap1knpH0keV-3x4iYwSHH9lX4Mg=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Поросенок - сериал студии \"Пилот\"",
                    "https://www.youtube.com/user/pigletseries",
                    "https://yt3.ggpht.com/a/AGF-l7-ST5Lc17u7l2CMR3NL12FB5Qs4HBK-bipBEw=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Студия Пилот - Фильмы для взрослых",
                    "https://www.youtube.com/channel/UCjUys-_zDFCFOub0KXTRfiQ",
                    "https://yt3.ggpht.com/a/AGF-l7-000FEVT1_uU9M1T9_3w2m0wPOgdF-yLOM8g=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Студия Диафильм",
                    "https://www.youtube.com/channel/UCmFS6kwGK_ewWGLM6Kedjrw",
                    "https://yt3.ggpht.com/a/AGF-l7_1rGU24Nl1-PN3JAKtBqm6wK0Qa6tEy4QJSQ=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            // Некоторые фильмы могут не попасть в выкачанный список, хотя доступны по прямой ссылке
            // (судя по всему, это позволяют настройки ютюба)
            // Например, Укращение огня, 1972:
            // https://www.youtube.com/watch?v=vwBKEKqLOLY&feature=emb_title
            new PlaylistInfo("Киноконцерн \"Мосфильм\"",
                    "https://www.youtube.com/user/mosfilm",
                    "https://yt3.ggpht.com/a/AGF-l7-FqSADElUC3tgnze1kWQXADQwaIRFsN9po=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("LFV (Ленфильм)",
                    "https://www.youtube.com/user/LenfilmVideo",
                    "https://yt3.ggpht.com/a/AGF-l7-mV3IOoSod_k4Vnm4EZGDrhPyyYCx5QyHRfw=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Odesa Film Studio",
                    "https://www.youtube.com/user/OdessAnimationStudio",
                    "https://yt3.ggpht.com/a/AGF-l7_OBaYaHE5t6jEAOHe5aMWT9irqGIGzOBcjHw=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Все советские фильмы киностудии «Беларусьфильм»",
                    "https://www.youtube.com/user/belarusfilmRV",
                    "https://yt3.ggpht.com/a/AGF-l78CYlekYxhAcGRkYtO8AQSVFlrhI7uLNwKBiw=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("RVISION: Советские фильмы",
                    "https://www.youtube.com/user/RVisionGorky",
                    "https://yt3.ggpht.com/a/AGF-l79HNmYEEnJXLGyCnf1kQPSM0TAQs_EKH762=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Классика советского кино (официальный канал)",
                    "https://www.youtube.com/user/ClassicFilmRVisionTV",
                    "https://yt3.ggpht.com/a/AGF-l78fTaV6q6jL0CZuHFG1O-zQrBfFftDOxvQcAQ=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Советская фантастика.",
                    "https://www.youtube.com/playlist?list=PLT9f4X3_Znfo5fryLC0x6EWiuc3ebH5wJ",
                    "https://i.ytimg.com/vi/YFcKh6phfAo/hqdefault.jpg?sqp=-oaymwEXCPYBEIoBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLBaSMUurhuhfEotCVYFsVp0chHw0Q",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),
            new PlaylistInfo("te1ex",
                    "https://www.youtube.com/channel/UC2eD6PqWYex9bW4Z89IgdXA",
                    "https://yt3.ggpht.com/a/AGF-l79qobzUSWp3_HKVVQLS7vEPVUSSo5xlzpBIsg=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("НаучФильм",
                    "https://www.youtube.com/channel/UCmxvI8c0l6EiMlmlT5EGgjQ",
                    "https://yt3.ggpht.com/a/AGF-l7_HzHG6WEsXyRt1b4dtLzSZywimDMaeRHBUcg=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Центрнаучфильм",
                    "https://www.youtube.com/playlist?list=PLpQN9JX87qrk70dGehlt-9mCOETA8f0gB",
                    "https://i.ytimg.com/vi/DPgfwfmIdlY/hqdefault.jpg?sqp=-oaymwEXCPYBEIoBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLCYv38YczbV94aQnmXchOc8QDVHYg",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),
            new PlaylistInfo("Фильмы киностудии Центрнаучфильм (ЦНФ)",
                    "https://www.youtube.com/playlist?list=PLX9DLhODFdBihVNRHvkGhQJSv6IwQtbGo",
                    "https://i.ytimg.com/vi/7miKYIP5FAQ/hqdefault.jpg?sqp=-oaymwEXCPYBEIoBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLD8Dt6ls5ndAsbq7eIPaIBlyQnQWA",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),
            new PlaylistInfo("Обучающие Видео",
                    "https://www.youtube.com/channel/UCZeBpzAG7iQKVcS60x2Q0sQ",
                    "https://yt3.ggpht.com/a/AGF-l79kBZBW4cY6OXj737Ef5LioChPz88HwcJRWeg=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL)
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_recommended_playlists);

        recommendedPlaylistsView = findViewById(R.id.recommended_playlists_view);
        playlistsAddBtn = findViewById(R.id.playlists_add_btn);
        playlistList = findViewById(R.id.playlist_list);

        playlistsAddProgressView = findViewById(R.id.playlists_add_progress_view);
        playlistAddPlThumbImg = findViewById(R.id.playlist_add_pl_thumb_img);
        playlistAddPlNameTxt = findViewById(R.id.playlist_add_pl_name_txt);
        playlistAddPlUrlTxt = findViewById(R.id.playlist_add_pl_url_txt);
        playlistAddStatusTxt = findViewById(R.id.playlist_add_status_txt);
        playlistAddProgress = findViewById(R.id.playlist_add_progress);

        playlistAddErrorView = findViewById(R.id.playlist_add_error_view);
        playlistAddErrorTxt = findViewById(R.id.playlist_add_error_txt);
        playlistAddRetryBtn = findViewById(R.id.playlist_add_retry_btn);
        playlistAddSkipBtn = findViewById(R.id.playlist_add_skip_btn);
        playlistAddCancelBtn = findViewById(R.id.playlist_add_cancel_btn);


        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistList.setLayoutManager(linearLayoutManager);

        final List<PlaylistInfo> plList = new ArrayList<>();
        Collections.addAll(plList, recommendedPlaylists);
        if (ConfigOptions.DEVEL_MODE_ON) {
            plList.add(new PlaylistInfo("Фонд Рабочей Академии",
                    "https://www.youtube.com/user/fondrabakademii",
                    "https://yt3.ggpht.com/a/AGF-l78g2YdH_JzOK91UMTfXqI4CYR2IxMHxSnFhyw=s100-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER));
        }
        final PlaylistInfoArrayAdapter recPlsAdapter = new PlaylistInfoArrayAdapter(this,
                plList, null, null);
        playlistList.setAdapter(recPlsAdapter);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        playlistsAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addPlaylistsBg();
            }
        });

        playlistAddRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // добавление продолжится с текущего недобавленного плейлиста
                // (см индекс plToAddStartIndex)
                addPlaylistsBg();
            }
        });

        playlistAddSkipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // пропустить неудачный плейлист
                plToAddStartIndex++;
                addPlaylistsBg();

            }
        });

        playlistAddCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddRecommendedPlaylistsActivity.this.finish();
            }
        });

        if(ConfigOptions.DEVEL_MODE_ON) {
            fetchInfoOnline();
        }
    }


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void addPlaylistsBg() {
        recommendedPlaylistsView.setVisibility(View.GONE);
        playlistsAddProgressView.setVisibility(View.VISIBLE);
        playlistAddErrorView.setVisibility(View.GONE);

        // канал или плейлист
        final ContentLoader.TaskController taskController = new ContentLoader.TaskController();
        taskController.setTaskListener(new ContentLoader.TaskListener() {
            @Override
            public void onStart() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //addPlaylistBtn.setEnabled(false);
                        playlistAddProgress.setVisibility(View.VISIBLE);
                        playlistAddStatusTxt.setText(taskController.getStatusMsg());
                    }
                });
            }

            @Override
            public void onFinish() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //addPlaylistBtn.setEnabled(true);
                        playlistAddProgress.setVisibility(View.GONE);
                        playlistAddStatusTxt.setText(taskController.getStatusMsg());
                    }
                });
            }

            @Override
            public void onStatusChange(final String status, final Exception e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistAddStatusTxt.setText(status);
                        if (e != null) {
                            playlistAddErrorView.setVisibility(View.VISIBLE);
                            playlistAddErrorTxt.setText(e.getMessage()
                                    + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
                        }
                    }
                });
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {

                boolean allOk = true;

                final VideoDatabase videodb = VideoDatabase.getDb(AddRecommendedPlaylistsActivity.this);
                // начинаем с индекса plToAddStartIndex (например, если продолжаем после ошибки)
                for (; plToAddStartIndex < recommendedPlaylists.length; plToAddStartIndex++) {
                    final PlaylistInfo plInfo = recommendedPlaylists[plToAddStartIndex];
                    // подгрузим иконку плейлиста (хотя скорее всего она уже в кеше)
                    try {
                        // иконка канала
                        if (plInfo.getThumbBitmap() == null) {
                            final Bitmap _plThumb = VideoThumbManager.getInstance().loadPlaylistThumb(
                                    AddRecommendedPlaylistsActivity.this, plInfo.getThumbUrl());
                            plInfo.setThumbBitmap(_plThumb);
                        }
                    } catch (final Exception e) {
                        // если не загрузилась - плохой признак скорее, но здесь ничего страшного,
                        // игнорируем
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playlistAddPlThumbImg.setImageBitmap(plInfo.getThumbBitmap());
                            playlistAddPlNameTxt.setText(plInfo.getName());
                            playlistAddPlUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(plInfo.getUrl()));
                        }
                    });

                    // проверим, что список еще не добавлен в базу
                    if (videodb.playlistInfoDao().findByUrl(plInfo.getUrl()) != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                playlistAddStatusTxt.setText(getString(R.string.playlist_add_status_already_added));
                            }
                        });
                    } else {
                        // добавляем
                        final long plId = ContentLoader.getInstance().addYtPlaylist(
                                AddRecommendedPlaylistsActivity.this, plInfo.getUrl(), taskController);
                        if (plId == -1) {
                            // Плейлист не добавлен - завершаему эту попытку, экран не закрываем
                            // (в колбэк таск-контроллера еще раньше должно прийти событие с ошибкой,
                            // он покажет экран ошибки с сообщением и предложениями попробовать еще,
                            // пропустить или завершить добавление)
                            allOk = false;
                            break;
                        }
                    }
                    try {
                        // сделаем небольшую паузу между двумя плейлистами, чтобы успеть разглядеть
                        // сообщение о том, что плейлист добавлен, например.
                        // (пользователь не будет часто добавлять плейлисты в этом диалоге, поэтому
                        // здесь это ок)
                        Thread.sleep(1000);
                    } catch (final InterruptedException e) {
                    }
                }
                videodb.close();

                if (allOk) {
                    // все добавили, выходим
                    AddRecommendedPlaylistsActivity.this.finish();
                }
            }
        }).start();
    }

    /**
     * Напечатать информацию о плейлистах в консоль (для режима разработки)
     */
    private void fetchInfoOnline() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                for(final PlaylistInfo plInfo : recommendedPlaylists) {
                    try {
                        final PlaylistInfo plInfo_ = ContentLoader.getInstance().getYtPlaylistInfo(plInfo.getUrl());
                        System.out.println(plInfo_.getName());
                        System.out.println(plInfo_.getUrl());
                        System.out.println(plInfo_.getThumbUrl());
                        System.out.println(plInfo_.getType());
                    } catch (IOException | ExtractionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}

