package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * AddRecommendedPlaylistsActivity.java is part of YaShlang.
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
            // Замечание: размер иконки канала можно задавать любой вообще в параметре: *=s240-*
            // (например: *=s240-* или *=s160-*)
            // У нас иконки примерно 100x100 везде, но будем брать с запасом 240x240, чтобы хайрез

            // Классические и современные мультфильмы и детские фильмы
            // Проект телеканала «Россия», ВГТРК Официальный сайт  https://russia.tv
            new PlaylistInfo("СМОТРИМ. Русские мультфильмы",
                    "https://youtube.com/channel/UCU06hfFzcBjQl9-Ih2SvpoQ",
                    "https://yt3.ggpht.com/a/AGF-l7-gkH8i4mTtUaGQuzHz2JriteeLSioPlqzS2Q=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),

            // много разных мультиков (в т.ч. с зачищенного союзмультифильма)
            new PlaylistInfo("Дети",
                    "https://www.youtube.com/channel/UCmLgLTTZgMA6NJPlJFeF92g",
                    "https://yt3.ggpht.com/a/AATXAJzKiY6Zo17GSTg1biFDETEAGXw_sx_uX4C58O3_=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),

            new PlaylistInfo("Киножурнал Ералаш",
                    "https://www.youtube.com/c/eralash",
                    "https://yt3.ggpht.com/a/AATXAJyrcud4u0wZRamlOQyHYV0pREVXNpPFfgs9dYec0g=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Спокойной ночи, малыши!",
                    "https://www.youtube.com/channel/UCoVS2PU1QJpXrO9oTyke2cw",
                    "https://yt3.ggpht.com/a/AGF-l7_uiPDLitSDNNS9ehGNte53CdDDml2D7lK99g=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),

            // новые мультики
            new PlaylistInfo("Гора самоцветов (Mountain of Gems)",
                    "https://www.youtube.com/channel/UCexc-emEni9lvU5jbTqLEkw",
                    "https://yt3.ggpht.com/a/AGF-l7_xkC44VNzYDmi61jX6bSLyXw4GE3DprxAPZA=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("TVSmeshariki",
                    "https://www.youtube.com/user/TVSmeshariki",
                    "https://yt3.ggpht.com/a/AGF-l7_2xR6oqSIrUzTH_LzZdBXwyFPCPtM0SKKsBQ=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Малышарики",
                    "https://www.youtube.com/c/malyshariki",
                    "https://yt3.ggpht.com/a/AATXAJyQZYqZ7qX4g_jh9nKhoFESAbEobUb8iLAB0OO8gw=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Маша и Медведь",
                    "https://www.youtube.com/user/MashaMedvedTV",
                    "https://yt3.ggpht.com/a/AGF-l78kf4dz0_5f84x2Wjmle_4oQ8gz7dRApCZeRw=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Везуха! / LUCKY!",
                    "https://www.youtube.com/user/VezuhaTV",
                    "https://yt3.ggpht.com/a/AGF-l7_dooQIqq5Ap1knpH0keV-3x4iYwSHH9lX4Mg=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Фиксики",
                    "https://www.youtube.com/user/fixiki",
                    "https://yt3.ggpht.com/a/AATXAJw0P592ZMPs7XcqwOGPwud033lYjJP1WAlineUvhg=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Мульт",
                    "https://www.youtube.com/c/kanalmult",
                    "https://yt3.ggpht.com/a/AATXAJwyQdAsaS_XEqp5l6iSDGSKyqqz-3XgK2jrlBmiBQ=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Барбоскины",
                    "https://www.youtube.com/user/barboskinyonline",
                    "https://yt3.ggpht.com/a/AATXAJwXuqcyOX1d5m0SK_3aGQDYvuCUNmJQTp1Cg8M93Q=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Поросенок - сериал студии \"Пилот\"",
                    "https://www.youtube.com/user/pigletseries",
                    "https://yt3.ggpht.com/a/AGF-l7-ST5Lc17u7l2CMR3NL12FB5Qs4HBK-bipBEw=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Студия Пилот - Фильмы для взрослых",
                    "https://www.youtube.com/channel/UCjUys-_zDFCFOub0KXTRfiQ",
                    "https://yt3.ggpht.com/a/AGF-l7-000FEVT1_uU9M1T9_3w2m0wPOgdF-yLOM8g=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Unknown Soviet cartoons 1939-1942",
                    "https://www.youtube.com/playlist?list=PL7DB3215F59FB91BE",
                    "https://i.ytimg.com/vi/2pXUlfN59GM/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLCDlO70xqMO6cF8dPGI6aXBDmFEeA",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),

            // NewPipeExtractor (как минимум в версии 0.20.1) не работает с названиями каналов в UTF
            // https://github.com/TeamNewPipe/NewPipeExtractor/issues/435
            new PlaylistInfo("Студия Диафильм",
                    //"https://www.youtube.com/c/СтудияДиафильм",
                    "https://www.youtube.com/channel/UCmFS6kwGK_ewWGLM6Kedjrw",
                    "https://yt3.ggpht.com/a/AATXAJx5kRDv3zUNvBD0SxZXq0C1NVMAr-AAMX1OX0VAkg=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Все Самые Лучшие Диафильмы и мультфильмы",
                    "https://www.youtube.com/user/DiafilmyMultfilmy",
                    "https://yt3.ggpht.com/a/AATXAJwre_bV4gNmA7ZbgoxSBO2JemLHlzGbrnBSSsXvXg=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),


            // Союзмультфильм удОлил со всех каналов все мультики почти
            // Канал ClassicCartoonsMedia закрыл доступ к советским мультфильмам Союзмультфильм
            // (более 1 тыс роликов ранее доступных на канале переведено в закрытый доступ)
            // https://habr.com/ru/news/t/522318/
            new PlaylistInfo("Союзмультфильм",
                    "https://www.youtube.com/c/Soyuzmultfilm",
                    "https://yt3.ggpht.com/a/AATXAJxZuJCvh4_p_zuVvZpit2zZ4TBK2FeBvjbHrdmF-g=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Мультики студии Союзмультфильм",
                    "https://www.youtube.com/user/ClassicCartoonsMedia",
                    "https://yt3.ggpht.com/a/AGF-l79QQ2pizTFB3G61q3I4ryzr659sbHFt5TffzQ=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),

            // Немного иностранных
            new PlaylistInfo("Губка Боб Квадратные Штаны | Все Серии | Nickelodeon Россия",
                    "https://www.youtube.com/playlist?list=PLBTgeBUAoPL3MOem4yPmMN-z8e74dcaFT",
                    "https://i.ytimg.com/vi/hF3SPkkNLHU/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLCW_zPLUmRdOATy3SeeoXZxIWAJ9A",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),

            new PlaylistInfo("Анимационные сериалы",
                    "https://www.youtube.com/user/KanalDisneyCartoons",
                    "https://yt3.ggpht.com/a/AATXAJw8y6JXryHGNALfFyw9AWeKCASVmLFnDW5winSllA=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),


            new PlaylistInfo("Мифы Древней Греции",
                    "https://www.youtube.com/playlist?list=PLcUOA4FFMK6Os4O89ScOGO8jl13T-n1Bv",
                    "https://i.ytimg.com/vi/Qg0ACKG8yq0/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLAf6f_bYqjy1kQZ3lsH3XBqxnCruQ",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),


            // Некоторые фильмы могут не попасть в выкачанный список, хотя доступны по прямой ссылке
            // (судя по всему, это позволяют настройки ютюба)
            // Например, Укращение огня, 1972:
            // https://www.youtube.com/watch?v=vwBKEKqLOLY&feature=emb_title
            new PlaylistInfo("Киноконцерн \"Мосфильм\"",
                    "https://www.youtube.com/user/mosfilm",
                    "https://yt3.ggpht.com/a/AGF-l7-FqSADElUC3tgnze1kWQXADQwaIRFsN9po=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Мосфильм - для детей",
                    "https://www.youtube.com/c/MosfilmForKids",
                    "https://yt3.ggpht.com/a/AATXAJwPEIA26tSEzQaAcIO9IhvPb9-Rjfkmg_hvlsV7=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("LFV (Ленфильм)",
                    "https://www.youtube.com/user/LenfilmVideo",
                    "https://yt3.ggpht.com/a/AGF-l7-mV3IOoSod_k4Vnm4EZGDrhPyyYCx5QyHRfw=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Odesa Film Studio",
                    "https://www.youtube.com/user/OdessAnimationStudio",
                    "https://yt3.ggpht.com/a/AGF-l7_OBaYaHE5t6jEAOHe5aMWT9irqGIGzOBcjHw=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Все советские фильмы киностудии «Беларусьфильм»",
                    "https://www.youtube.com/user/belarusfilmRV",
                    "https://yt3.ggpht.com/a/AGF-l78CYlekYxhAcGRkYtO8AQSVFlrhI7uLNwKBiw=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("RVISION: Советские фильмы",
                    "https://www.youtube.com/user/RVisionGorky",
                    "https://yt3.ggpht.com/a/AGF-l79HNmYEEnJXLGyCnf1kQPSM0TAQs_EKH762=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Классика советского кино (официальный канал)",
                    "https://www.youtube.com/user/ClassicFilmRVisionTV",
                    "https://yt3.ggpht.com/a/AGF-l78fTaV6q6jL0CZuHFG1O-zQrBfFftDOxvQcAQ=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("RVISION: Фильмы и сериалы",
                    "https://www.youtube.com/c/RVisionChannel/",
                    "https://yt3.ggpht.com/a/AATXAJxsyKVxC4NKsqb1glTO0VbR2OLo_xptzYP1AOo5=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),

            new PlaylistInfo("Советская фантастика.",
                    "https://www.youtube.com/playlist?list=PLT9f4X3_Znfo5fryLC0x6EWiuc3ebH5wJ",
                    "https://i.ytimg.com/vi/YFcKh6phfAo/hqdefault.jpg?sqp=-oaymwEXCPYBEIoBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLBaSMUurhuhfEotCVYFsVp0chHw0Q",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),
            new PlaylistInfo("te1ex",
                    "https://www.youtube.com/channel/UC2eD6PqWYex9bW4Z89IgdXA",
                    "https://yt3.ggpht.com/a/AGF-l79qobzUSWp3_HKVVQLS7vEPVUSSo5xlzpBIsg=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("НаучФильм",
                    "https://www.youtube.com/channel/UCmxvI8c0l6EiMlmlT5EGgjQ",
                    "https://yt3.ggpht.com/a/AGF-l7_HzHG6WEsXyRt1b4dtLzSZywimDMaeRHBUcg=s240-c-k-c0xffffffff-no-rj-mo",
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
                    "https://yt3.ggpht.com/a/AGF-l79kBZBW4cY6OXj737Ef5LioChPz88HwcJRWeg=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Учебные документальные фильмы СССР",
                    "https://www.youtube.com/playlist?list=PLfmFpq8sdXGo1Ow-BYKLMkS1uu6V-gpNg",
                    "https://i.ytimg.com/vi/QNPsvXhx798/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLDum9KSVOOjK9y5akSysOu3PRfXJw",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),

            new PlaylistInfo("Киножурнал Фитиль (официальный канал)",
                    "https://www.youtube.com/user/FitilOfficial",
                    "https://yt3.ggpht.com/a/AGF-l7-f8u9owggUKS6VVXyGEmtLNvIsT-6cvti4eg=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER),

            new PlaylistInfo("Маски-шоу",
                    "https://www.youtube.com/channel/UCVgBv2Q-V2S7UgeonH4Pg0w",
                    "https://yt3.ggpht.com/a/AATXAJy3V0P0Mr_2R18YRSbhm11dVwUF_Y92PskOtTaL=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),

            new PlaylistInfo("Gorodok TV",
                    "https://www.youtube.com/c/GorodokTV",
                    "https://yt3.ggpht.com/a/AATXAJwWC-ZdpF4vMOUTXD_-PYrrWYHi0qPdjO4H6UEdUg=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),

            new PlaylistInfo("Студия Самарафильм",
                    "https://www.youtube.com/channel/UCLBOPmSYOdq6C5HRlq3Ki5w",
                    "https://yt3.ggpht.com/a/AATXAJz3Dwm6gto3vPu_E4Psas8gnPTH_9lUxcsEYWGK=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),


            // Телевидение
            new PlaylistInfo("Советское телевидение. ГОСТЕЛЕРАДИОФОНД России",
                    "https://www.youtube.com/c/gtrftv",
                    "https://yt3.ggpht.com/a/AATXAJzRnIyB3c_jQzRe48NsNJfdbCBW3-F3Wg9i25f4=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),

            new PlaylistInfo("Classic TV Channel",
                    "https://www.youtube.com/channel/UCNCAEXQK6M8wU0kOcFa6gng",
                    "https://yt3.ggpht.com/a/AATXAJxATE9pRoGhJ6OxkE-xv80LreHme2q2dWz02uue=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),


            // Разное

            // Много хорошей старых мультиков и старой хроники, но разбавлено современными записями
            // и несколько роликов с военной хроникой с трупами, которую, наверное (пока), не буду включать
//            new PlaylistInfo("Stic (Политика. Кинохроники Эпохи: СССР, Белое движение, Царизм. Спектакли и телеспектакли СССР",
//                    "https://www.youtube.com/c/Stic",
//                    "https://yt3.ggpht.com/a/AATXAJyNr6MZe5TwUhJeN9wKnqhBiBAwjnBKY9E0iug8=s240-c-k-c0x00ffffff-no-rj",
//                    PlaylistInfo.PlaylistType.YT_USER),

            // Разные мультики, хроника и еще клипы Виары в начале
            new PlaylistInfo("Интересный кинозал",
                    "https://www.youtube.com/c/v1977",
                    "https://yt3.ggpht.com/a/AATXAJyF9HvvJZXnJ8gmHYu-51Yo7CanuVvjkdQ1hit6uA=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER)

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
                    "https://yt3.ggpht.com/a/AGF-l78g2YdH_JzOK91UMTfXqI4CYR2IxMHxSnFhyw=s240-c-k-c0xffffffff-no-rj-mo",
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

