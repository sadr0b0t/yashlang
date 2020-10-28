package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * SearchOnlinePlaylistActivity.java is part of YaShlang.
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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class SearchOnlinePlaylistActivity extends AppCompatActivity {

    public static final int REQUEST_SEARCH_PLAYLIST = 1;

    public static final String RESULT_PLAYLIST_URL = "RESULT_PLAYLIST_URL";


    private EditText playlistSearchInput;
    private RecyclerView playlistList;

    private Handler handler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_search_online_playlist);

        playlistSearchInput = findViewById(R.id.playlist_search_input);
        playlistList = findViewById(R.id.playlist_list);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if(ConfigOptions.DEVEL_MODE_ON) {
            playlistSearchInput.setText("Союзмультфильм");
        }

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistList.setLayoutManager(linearLayoutManager);

        playlistSearchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                updateSearchSuggestionsBg(playlistSearchInput.getText().toString());

                return false;
            }
        });

        playlistSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSearchSuggestionsBg(s.toString());
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * Update search suggestions dropdown in background
     * @param sstr
     */
    private void updateSearchSuggestionsBg(final String sstr) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO: потестировать автокомплит, как-то все глючно работает
                // (добавили з - один результат, удалили после нее другую букву - другой)
                // загрузить список видео
                List<PlaylistInfo> _playlists;
                try {
                    _playlists = ContentLoader.getInstance().searchYtPlaylists(sstr);

                } catch (Exception e) {
                    _playlists = new ArrayList<>();
                }

                final List<PlaylistInfo> playlists = _playlists;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistList.setAdapter(new PlaylistInfoArrayAdapter(SearchOnlinePlaylistActivity.this, playlists,
                                new OnListItemClickListener<PlaylistInfo>() {
                                    @Override
                                    public void onItemClick(final View view, final int position, final PlaylistInfo item) {
                                        final Intent intent = new Intent();
                                        intent.putExtra(RESULT_PLAYLIST_URL, item.getUrl());
                                        setResult(REQUEST_SEARCH_PLAYLIST, intent);
                                        finish();
                                    }
                                    @Override
                                    public boolean onItemLongClick(final View view, final int position, final PlaylistInfo item) {
                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                        final ClipData clip = ClipData.newPlainText(item.getUrl(), item.getUrl());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(SearchOnlinePlaylistActivity.this,
                                                getString(R.string.copied) + ": " + item.getUrl(),
                                                Toast.LENGTH_LONG).show();
                                        return true;
                                    }
                                }, null));
                    }
                });
            }
        }).start();
    }
}
