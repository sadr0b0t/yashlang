package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
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
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.controller.DataIO;
import su.sadrobot.yashlang.model.PlaylistInfo;

/**
 *
 */
public class AddRecommendedPlaylistsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_recommended_playlists);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // после переноса списка рекомендованных плейлистов в файл json, эта активити,
        // в сущености, становится фантомной - её цель загрузить список плейлистов
        // и передать дальше в AddRecommendedPlaylistsActivity, а потом по возвращении
        // сразу завершиться, так, по сути, не показвшись пользователю на переднем плане
        loadPlaylists();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        finish();
    }

    private void loadPlaylists() {
        // грузим не в фоне, чтобы экран сразу перескочил на следующий без мигания
        try {
            final String loadedFileContent = DataIO.loadFromResource(ConfigOptions.RECOMMENDED_PLAYLISTS_RES_PATH);

            // здесь небольшой (возможно, временный) хак - загрузим строку JSON в список объектов
            // сделаем со списком манипуляции, а потом снова пересохраним и отправим параметром
            // в следующую активити уже для добавления плейлистов.
            // Заодно (при загрузке JSON) срежем всё лишнее - комментарии и объекты c полем _ignore
            final List<PlaylistInfo> loadedPlaylists = DataIO.loadPlaylistsFromJSON(loadedFileContent);

            final List<PlaylistInfo> plList = new ArrayList<>();
            plList.addAll(loadedPlaylists);
            if (ConfigOptions.DEVEL_MODE_ON) {
                plList.add(new PlaylistInfo("Фонд Рабочей Академии",
                        "https://www.youtube.com/user/fondrabakademii",
                        "https://yt3.ggpht.com/a/AGF-l78g2YdH_JzOK91UMTfXqI4CYR2IxMHxSnFhyw=s240-c-k-c0xffffffff-no-rj-mo",
                        PlaylistInfo.PlaylistType.YT_USER));

                plList.add(new PlaylistInfo("proletariantv",
                        "https://video.ploud.fr/a/proletariantv/videos",
                        "https://video.ploud.fr/lazy-static/avatars/f0f4e02e-91e7-437c-9c35-b9d34177818b.jpg",
                        PlaylistInfo.PlaylistType.PT_USER));
            }

            final String recommendedPlaylistsJson = DataIO.exportPlaylistsToJSON(plList).toString();

            final Intent intent = new Intent(AddRecommendedPlaylistsActivity.this, ImportPlaylistsActivity.class);
            intent.putExtra(ImportPlaylistsActivity.PARAM_PLAYLISTS_JSON, recommendedPlaylistsJson);
            startActivityForResult(intent, 0);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }
}
