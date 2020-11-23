package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigurePlaylistActivity.java is part of YaShlang.
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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemSwitchListener;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class ConfigurePlaylistsProfilesFragment extends Fragment {

    private Button enableAllBtn;
    private Button disableAllBtn;
    private Button disableYtBtn;

    //
    private RecyclerView profileList;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_playlists_profiles, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        enableAllBtn = view.findViewById(R.id.enable_all_btn);
        disableAllBtn = view.findViewById(R.id.disable_all_btn);
        disableYtBtn = view.findViewById(R.id.disable_yt_btn);


        profileList = view.findViewById(R.id.profile_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        profileList.setLayoutManager(linearLayoutManager);

        enableAllBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final VideoDatabase videodb = VideoDatabase.getDb(ConfigurePlaylistsProfilesFragment.this.getContext());
                        videodb.playlistInfoDao().setEnabled4All(true);
                        videodb.close();
                    }
                }).start();
            }
        });
        disableAllBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final VideoDatabase videodb = VideoDatabase.getDb(ConfigurePlaylistsProfilesFragment.this.getContext());
                        videodb.playlistInfoDao().setEnabled4All(false);
                        videodb.close();
                    }
                }).start();
            }
        });
        disableYtBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final VideoDatabase videodb = VideoDatabase.getDb(ConfigurePlaylistsProfilesFragment.this.getContext());
                        videodb.playlistInfoDao().setEnabled4Yt(false);
                        videodb.close();
                    }
                }).start();
            }
        });


        setupProfileListAdapter();
    }

    void setupProfileListAdapter() {
        // TODO
    }
}
