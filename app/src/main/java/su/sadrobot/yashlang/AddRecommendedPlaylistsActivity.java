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


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import su.sadrobot.yashlang.view.OnListItemClickListener;
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


    private enum State {
        INITIAL_RECOMMENDED,
        PLAYLIST_ADD_PROGRESS, PLAYLIST_ADD_ERROR, PLAYLIST_ADD_OK
    }

    private State state = State.INITIAL_RECOMMENDED;

    private ContentLoader.TaskController taskController;
    private int plToAddStartIndex = 0;


    private PlaylistInfo[] recommendedPlaylists = {

            // PeerTube

            new PlaylistInfo("OldGoodCartoons",
                    "https://open.tube/accounts/olddaycartoons/videos",
                    "https://open.tube/lazy-static/avatars/679a4755-914a-48e4-83a9-f47f12445b2f.png",
                    PlaylistInfo.PlaylistType.PT_USER),

            // канал зачистил ролики на vidcommons.org, но на open.tube сохранилось зеркало
            // https://vidcommons.org/accounts/animation/videos
            // но при этом на open.tube у этого канала постоянно меняется адрес иконки, а на
            // vidcommons.org сам канал с иконкой сохранился, пропали только видео
            new PlaylistInfo("Animation",
                    "https://open.tube/accounts/animation@vidcommons.org/videos",
                    //"https://open.tube/lazy-static/avatars/dd21503d-f183-4256-906d-8ce8cfdaf056.jpg",
                    "https://vidcommons.org/lazy-static/avatars/5c481106-8156-4c43-af59-e308f4a3d37c.jpg",
                    PlaylistInfo.PlaylistType.PT_USER),

            new PlaylistInfo("OpenMovies \uD83D\uDCFD",
                    "https://open.tube/accounts/openmovies/videos",
                    "https://open.tube/lazy-static/avatars/a5a7dff0-27ed-4079-9a91-165a30fb5532.jpg",
                    PlaylistInfo.PlaylistType.PT_USER),

            new PlaylistInfo("films",
                    "https://vidcommons.org/accounts/films/videos",
                    "https://vidcommons.org/lazy-static/avatars/bb912df2-a136-4590-96a2-e5de06db9ccd.jpg",
                    PlaylistInfo.PlaylistType.PT_USER),

            // YouTube

            // Замечание: размер иконки канала YouTube можно задавать любой вообще в параметре: *=s240-*
            // (например: *=s240-* или *=s160-*)
            // У нас иконки примерно 100x100 везде, но будем брать с запасом 240x240, чтобы хайрез

            // Классические и современные мультфильмы и детские фильмы
            // Проект телеканала «Россия», ВГТРК Официальный сайт  https://russia.tv
            new PlaylistInfo("СМОТРИМ. Русские мультфильмы",
                    "https://youtube.com/channel/UCU06hfFzcBjQl9-Ih2SvpoQ",
                    "https://yt3.ggpht.com/a/AGF-l7-gkH8i4mTtUaGQuzHz2JriteeLSioPlqzS2Q=s240-c-k-c0xffffffff-no-rj-mo",
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
            new PlaylistInfo("Лунтик",
                    "https://www.youtube.com/user/luntik",
                    "https://yt3.ggpht.com/a/AATXAJyVWXneCE7S5Ajw3w8V93Jsx9SfAPHdDuXdk5PBMQ=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Барбоскины",
                    "https://www.youtube.com/user/barboskinyonline",
                    "https://yt3.ggpht.com/a/AATXAJwXuqcyOX1d5m0SK_3aGQDYvuCUNmJQTp1Cg8M93Q=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Белка и Стрелка",
                    "https://www.youtube.com/user/BelkaiStrelkaTV",
                    "https://yt3.ggpht.com/a/AATXAJzwki_bJdaDKYCRB7sxAhv2JfU2EtEMI7AyT7Ak=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Три Кота",
                    //"https://www.youtube.com/c/ТриКота",
                    "https://www.youtube.com/channel/UCBZNnwQOBirwpeWncqTj_KQ",
                    "https://yt3.ggpht.com/a/AATXAJxCV4vup0-vMjQKxNnW08VyTaTFWaANTKgwByzh=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo(" Фееринки",
                    "https://www.youtube.com/c/Feerinki",
                    "https://yt3.ggpht.com/a/AATXAJymGprV35Gc9fiDHfYbMre7UJXyLUL0w8K-rhWs=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Сказочный Патруль",
                    "https://www.youtube.com/channel/UC_T5XbinlhZfQ9reWLAGjxw",
                    "https://yt3.ggpht.com/a/AATXAJwaO6CVjY5-f3W326XUjIxiYNrAldxM9bEIeJip=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Три богатыря",
                    "https://www.youtube.com/c/TriBogatirya",
                    "https://yt3.ggpht.com/ytc/AAUvwngRn4X-CM7QSKZcBOG3cCeAqJrE3VJB7qe-paINPg=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Царевны",
                    "https://www.youtube.com/c/tsarevny",
                    "https://yt3.ggpht.com/a/AATXAJxIfhN__Zmwd6Mrj97u4jg-d0n1uZGfwe9JBz0khw=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Ангел Бэби",
                    "https://www.youtube.com/channel/UCGc6IG2eIhFa5vu2QtRz69g",
                    "https://yt3.ggpht.com/a/AATXAJwlsJTeu094JtXx9zrTk9HHumtskBSKJVaTlPHo=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Новаторы (мультфильм)",
                    "https://www.youtube.com/c/NovatoryMult",
                    "https://yt3.ggpht.com/a/AATXAJz5UyZkhTYF75ba9JAIrYSyetDiAfo5yrtssAV28A=s240-c-k-c0x00ffffff-no-rj",
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

            new PlaylistInfo("Мульт",
                    "https://www.youtube.com/c/kanalmult",
                    "https://yt3.ggpht.com/a/AATXAJwyQdAsaS_XEqp5l6iSDGSKyqqz-3XgK2jrlBmiBQ=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("ANI",
                    "https://www.youtube.com/channel/UCn98L71jY8M_K4gR8tevQtQ",
                    "https://yt3.ggpht.com/a/AATXAJyFac96LJdfdbwm6p57pB7IxuxJN4yMd6e0IuJj1A=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("MetronomeFilmsComp",
                    "https://www.youtube.com/c/MetronomeFilmsComp",
                    "https://yt3.ggpht.com/a/AATXAJyCOIaEjOEwL53zL-_y2pcBxx-WDyB3Y6_AYeBz=s240-c-k-c0x00ffffff-no-rj",
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

            // много разных мультиков (в т.ч. с зачищенного союзмультифильма)
            new PlaylistInfo("Дети",
                    "https://www.youtube.com/channel/UCmLgLTTZgMA6NJPlJFeF92g",
                    "https://yt3.ggpht.com/a/AATXAJzKiY6Zo17GSTg1biFDETEAGXw_sx_uX4C58O3_=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Мультфильмы",
                    "https://www.youtube.com/channel/UCfuWYqd9Xqiwx_NwaSL3EbA",
                    "https://yt3.ggpht.com/a/AATXAJwVv0o3IXga7FpJgZfLyKzArMlOsSxdbHGGQgIJVA=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Planeta Karapuz",
                    "https://www.youtube.com/channel/UCGndH2dKiqPJ_iaqZMKWl9w",
                    "https://yt3.ggpht.com/a/AATXAJzV0jrSWbXRF_hzJjShWiGCEtJp3Y0vS3NF2JtU=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Лучшие фантастические мультфильмы СССР",
                    "https://www.youtube.com/playlist?list=PLItHd89hEUs6Yo6AhKf8Q5UcT5h-0vlh-",
                    "https://i.ytimg.com/vi/7NdzkLgFLUw/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLC76_hDUW8qE_YbRcGYKSrM5uhn0A",
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

            // Аудиокниги
            new PlaylistInfo("Аудиокниги Русская классика для больших и маленьких",
                    "https://www.youtube.com/channel/UCFXqyNz0gQvcEwHaeOafINg",
                    "https://yt3.ggpht.com/a/AATXAJx0n3HaVimIBp_K3rczxr6YpaEuImWFcI7CIWsy=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Лучшие детские сказки! Аудиосказки! Слушать!",
                    //"https://www.youtube.com/c/СюрреализмЖивописьмодаинтерьер",
                    "https://www.youtube.com/channel/UCDSdnhIkczZ-M6uGGzVTvzw",
                    "https://yt3.ggpht.com/a/AATXAJwXkNiZNM0ZkAYZ3ULGI3IhjEaA4Moz1g3GYqG7SQ=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Сказочник",
                    "https://www.youtube.com/channel/UCnu6yEMRmmidQTN4E4CNhJw",
                    "https://yt3.ggpht.com/a/AATXAJzZAOUKMiS4Z9f-n5tXIebx6IGtX0pVtDXmIL5u=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),

            // Немного иностранных
            new PlaylistInfo("Губка Боб Квадратные Штаны | Все Серии | Nickelodeon Россия",
                    "https://www.youtube.com/playlist?list=PLBTgeBUAoPL3MOem4yPmMN-z8e74dcaFT",
                    "https://i.ytimg.com/vi/hF3SPkkNLHU/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLCW_zPLUmRdOATy3SeeoXZxIWAJ9A",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),
            new PlaylistInfo("Свинка Пеппа Русский - Официальный канал",
                    "https://www.youtube.com/c/PeppaPigRUS",
                    "https://yt3.ggpht.com/a/AATXAJy7nFTPfNP3SBIx9jQ2gZztpwH0D5tSJbUx2N03=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Peppa Pig - Official Channel",
                    "https://www.youtube.com/c/peppapig",
                    "https://yt3.ggpht.com/a/AATXAJz2Mp-hik97LLAcGmvZy0TvoRzheUjn9rliXuQW5Q=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Маленькое королевство Бена и Холли - Pусский",
                    "https://www.youtube.com/c/BenHollyRUS",
                    "https://yt3.ggpht.com/a/AATXAJx97F7sEeOUm3EuyQbkbRYau7dznc48SaBuagernQ=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Анимационные сериалы",
                    "https://www.youtube.com/user/KanalDisneyCartoons",
                    "https://yt3.ggpht.com/a/AATXAJw8y6JXryHGNALfFyw9AWeKCASVmLFnDW5winSllA=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Minuscule",
                    "https://www.youtube.com/c/Minuscule",
                    "https://yt3.ggpht.com/a/AATXAJwTzd2iNVMUstIV5eesp-lV28UQz2N4tsu1yMFvoA=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),

            new PlaylistInfo("Мифы Древней Греции",
                    "https://www.youtube.com/playlist?list=PLcUOA4FFMK6Os4O89ScOGO8jl13T-n1Bv",
                    "https://i.ytimg.com/vi/Qg0ACKG8yq0/hqdefault.jpg?sqp=-oaymwEXCNACELwBSFryq4qpAwkIARUAAIhCGAE=&rs=AOn4CLAf6f_bYqjy1kQZ3lsH3XBqxnCruQ",
                    PlaylistInfo.PlaylistType.YT_PLAYLIST),


            // Некоторые фильмы могут не попасть в выкачанный список, хотя доступны по прямой ссылке
            // (судя по всему, это позволяют настройки ютюба)
            // Например, Укращение огня, 1972:
            // https://www.youtube.com/watch?v=vwBKEKqLOLY&feature=emb_title
            new PlaylistInfo("Золотая коллекция русского кино",
                    "https://www.youtube.com/channel/UCOVlL3Oo72Sr6jLXA-Dvzag",
                    "https://yt3.ggpht.com/ytc/AAUvwniyFZxBH0NUsmMALvmpCp2LTB56W5tpwtlCcKEs=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
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
                    "https://www.youtube.com/c/RVisionChannel",
                    "https://yt3.ggpht.com/a/AATXAJxsyKVxC4NKsqb1glTO0VbR2OLo_xptzYP1AOo5=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Советские фильмы / Кино СССР",
                    "https://www.youtube.com/c/retrofilmi",
                    "https://yt3.ggpht.com/a/AATXAJyGNStjBuwdiVhQmP-7RDF8e2gGVlv2Zuw0HOY1gA=s240-c-k-c0x00ffffff-no-rj",
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

            // Сатира, юмор - шоу

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
            new PlaylistInfo("Тележурнал Каламбур",
                    "https://www.youtube.com/user/kalamburVideo",
                    "https://yt3.ggpht.com/a/AATXAJyXXNq_bud5ECjqkAVXtzqD932wp-4eMzp6_6li=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("MoscowTheaters",
                    "https://www.youtube.com/user/MoscowTheaters",
                    "https://yt3.ggpht.com/ytc/AAUvwniYAKZQ0HhfbUpRx63-oMmyE512FubmF93sxfYt=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),

            // История, телевидение
            new PlaylistInfo("Студия Самарафильм",
                    "https://www.youtube.com/channel/UCLBOPmSYOdq6C5HRlq3Ki5w",
                    "https://yt3.ggpht.com/a/AATXAJz3Dwm6gto3vPu_E4Psas8gnPTH_9lUxcsEYWGK=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),

            new PlaylistInfo("Советское телевидение. ГОСТЕЛЕРАДИОФОНД России",
                    "https://www.youtube.com/c/gtrftv",
                    "https://yt3.ggpht.com/a/AATXAJzRnIyB3c_jQzRe48NsNJfdbCBW3-F3Wg9i25f4=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Советское радио. ГОСТЕЛЕРАДИОФОНД России",
                    "https://www.youtube.com/channel/UCM6oyrdQzBf-egEmlkJyQNg",
                    "https://yt3.ggpht.com/ytc/AAUvwnhzHuBK28S7DxNTqvK1XChbwDw3tuBbkAlcVaMb=s240-c-k-c0x00ffffff-no-rj",
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
            new PlaylistInfo("Советские мультфильмы",
                    "https://www.youtube.com/channel/UCWJs8oN4lpgPnKM_L-w5gCA",
                    "https://yt3.ggpht.com/a/AATXAJxYnV0WpdYAHl2nnIC64dH8ZY8quSlnSYTE7RuK=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Интересный кинозал",
                    "https://www.youtube.com/c/v1977",
                    "https://yt3.ggpht.com/a/AATXAJyF9HvvJZXnJ8gmHYu-51Yo7CanuVvjkdQ1hit6uA=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),

            // Телевидение - образование, научпоп, дети
            new PlaylistInfo("Телеканал Карусель",
                    "https://www.youtube.com/c/tvkarusel",
                    "https://yt3.ggpht.com/a/AATXAJxCk3tckKhWYSQmbQoWX0Vejzi4cdngoA0bR8ps=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Наука 2.0",
                    "https://www.youtube.com/c/NaukaTV",
                    "https://yt3.ggpht.com/a/AATXAJxCxKX8Rfv9A481SgY7idXoeCfQmEkeuJYI-Obpjg=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("В мире животных",
                    "https://www.youtube.com/channel/UC-zzTUrk6tBL3Ex20os3jng",
                    "https://yt3.ggpht.com/a/AATXAJxbUnXR4WUYEpr6kJUk_hQxlApPuqE2TpbhzvU=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Дарвиновский музей",
                    //"https://www.youtube.com/c/ГосударственныйДарвиновскиймузей",
                    "https://www.youtube.com/channel/UC0I4VnqZNLttX3BC0P7c1wQ",
                    "https://yt3.ggpht.com/a/AATXAJx2TDS_I0_pTZu-oUiD4J3BhR03Ly4Ot0E9nl-JIw=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),
            new PlaylistInfo("Дарвиновский музей Дети",
                    //"https://www.youtube.com/c/дарвиновский_музей",
                    "https://www.youtube.com/channel/UCFuc62jJlyvNW8YlHc89gDQ",
                    "https://yt3.ggpht.com/a/AATXAJyruHA7WVC68hZ5g47m5wNh2r16g6UwaI4ugdtI=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_CHANNEL),

            // Оригами, DIY
            new PlaylistInfo("Origami Russia",
                    "https://www.youtube.com/c/OrigamiRussia",
                    "https://yt3.ggpht.com/a/AATXAJyFCTszn2UgcOldTEDIF52Y_8gcGe2FUUN62L-4JA=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Hello Origami",
                    "https://www.youtube.com/c/HelloOrigami",
                    "https://yt3.ggpht.com/ytc/AAUvwnhkyXWXbL-6aaT1cSfuDwwyNaTi6vXarWv5-3Et=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("Origami with Jo Nakashima",
                    "https://www.youtube.com/c/JoNakashimaBR",
                    "https://yt3.ggpht.com/a/AATXAJz_8dnTDY6lyMqn-j8ndGAfmxprfPUseZPLmMSsMNI=s240-c-k-c0x00ffffff-no-rj",
                    PlaylistInfo.PlaylistType.YT_USER),
            new PlaylistInfo("123 Easy Paper Crafts 5 minute paper craft",
                    "https://www.youtube.com/channel/UCXUH2dmTnXMSxmJfp4-LT8A",
                    "https://yt3.ggpht.com/a/AATXAJwnXFEoLRk3FgT_o_oGwjwJJdB_tiHW3Vh-PzBx=s240-c-k-c0x00ffffff-no-rj",
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
                    "https://yt3.ggpht.com/a/AGF-l78g2YdH_JzOK91UMTfXqI4CYR2IxMHxSnFhyw=s240-c-k-c0xffffffff-no-rj-mo",
                    PlaylistInfo.PlaylistType.YT_USER));
        }
        final PlaylistInfoArrayAdapter recPlsAdapter = new PlaylistInfoArrayAdapter(this,
                plList, new OnListItemClickListener<PlaylistInfo>() {
            @Override
            public void onItemClick(final View view, final int position, final PlaylistInfo item) {
            }

            @Override
            public boolean onItemLongClick(final View view, final int position, final PlaylistInfo plInfo) {

                // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                // работает только вариант Gravity.RIGHT
                //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                final PopupMenu popup = new PopupMenu(AddRecommendedPlaylistsActivity.this,
                        view.findViewById(R.id.playlist_name_txt));
                popup.getMenuInflater().inflate(R.menu.playlist_item_actions, popup.getMenu());
                popup.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem item) {
                                switch (item.getItemId()) {
                                    case R.id.action_copy_playlist_name: {
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(AddRecommendedPlaylistsActivity.this,
                                                getString(R.string.copied) + ": " + plInfo.getName(),
                                                Toast.LENGTH_LONG).show();
                                        break;
                                    }
                                    case R.id.action_copy_playlist_url: {
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(AddRecommendedPlaylistsActivity.this,
                                                getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                Toast.LENGTH_LONG).show();
                                        break;
                                    }
                                }
                                return true;
                            }
                        }
                );
                popup.show();
                return true;
            }
        }, null);
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
    public void onDestroy() {
        super.onDestroy();

        if(taskController != null) {
            taskController.cancel();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateControlsVisibility() {
        switch (state){
            case INITIAL_RECOMMENDED:
                recommendedPlaylistsView.setVisibility(View.VISIBLE);
                playlistsAddProgressView.setVisibility(View.GONE);

                break;
            case PLAYLIST_ADD_PROGRESS:
                recommendedPlaylistsView.setVisibility(View.GONE);
                playlistsAddProgressView.setVisibility(View.VISIBLE);

                playlistAddProgress.setVisibility(View.VISIBLE);
                playlistAddErrorView.setVisibility(View.GONE);

                break;
            case PLAYLIST_ADD_ERROR:
                recommendedPlaylistsView.setVisibility(View.GONE);
                playlistsAddProgressView.setVisibility(View.VISIBLE);

                playlistAddProgress.setVisibility(View.GONE);
                playlistAddErrorView.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_ADD_OK:
                recommendedPlaylistsView.setVisibility(View.GONE);
                playlistsAddProgressView.setVisibility(View.VISIBLE);

                playlistAddProgress.setVisibility(View.GONE);
                playlistAddErrorView.setVisibility(View.GONE);

                break;
        }
    }

    private void addPlaylistsBg() {
        this.state = State.PLAYLIST_ADD_PROGRESS;
        updateControlsVisibility();

        // канал или плейлист
        taskController = new ContentLoader.TaskController();
        taskController.setTaskListener(new ContentLoader.TaskListener() {
            @Override
            public void onStart() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {

                        playlistAddStatusTxt.setText(taskController.getStatusMsg());
                        state = State.PLAYLIST_ADD_PROGRESS;
                        updateControlsVisibility();
                    }
                });
            }

            @Override
            public void onFinish() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistAddStatusTxt.setText(taskController.getStatusMsg());
                        if(taskController.getException() == null) {
                            state = State.PLAYLIST_ADD_OK;
                        } else {
                            state = State.PLAYLIST_ADD_ERROR;
                        }
                        updateControlsVisibility();
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
                            playlistAddErrorTxt.setText(e.getMessage()
                                    + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
                            state = State.PLAYLIST_ADD_ERROR;
                            updateControlsVisibility();
                        }
                    }
                });
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {

                boolean allOk = true;

                // начинаем с индекса plToAddStartIndex (например, если продолжаем после ошибки)
                for (; plToAddStartIndex < recommendedPlaylists.length; plToAddStartIndex++) {
                    if(taskController.isCanceled()) {
                        allOk = false;
                        break;
                    }

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
                    final PlaylistInfo existingPlInfo = VideoDatabase.getDbInstance(AddRecommendedPlaylistsActivity.this).
                            playlistInfoDao().findByUrl(plInfo.getUrl());
                    if (existingPlInfo != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                playlistAddStatusTxt.setText(getString(R.string.playlist_add_status_already_added));
                            }
                        });
                    } else {
                        // добавляем
                        final long plId = ContentLoader.getInstance().addPlaylist(
                                AddRecommendedPlaylistsActivity.this, plInfo.getUrl(), taskController);
                        if (plId == PlaylistInfo.ID_NONE) {
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
                        Thread.sleep(ConfigOptions.ADD_RECOMMENDED_PLAYLISTS_DELAY_MS);
                    } catch (final InterruptedException e) {
                    }
                }
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
                        final PlaylistInfo plInfo_ = ContentLoader.getInstance().getPlaylistInfo(plInfo.getUrl());
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

