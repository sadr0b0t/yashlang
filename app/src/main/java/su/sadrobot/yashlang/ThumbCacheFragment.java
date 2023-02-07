package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2023.
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import su.sadrobot.yashlang.controller.ThumbCacheFsManager;
import su.sadrobot.yashlang.util.StringFormatUtil;

/**
 *
 */
public class ThumbCacheFragment extends Fragment {

    private RadioButton vidThumbCacheNoneRadio;
    private RadioButton vidThumbCacheAllRadio;
    private RadioButton vidThumbCacheWithOfflineStreamsRadio;

    private TextView thumbCacheDirPathTxt;
    private TextView thumbCacheFsSizeTxt;
    private Button thumbCacheClearBnt;

    private final Handler handler = new Handler();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_thumb_cache, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vidThumbCacheNoneRadio = view.findViewById(R.id.vid_thumb_cache_none_radio);
        vidThumbCacheAllRadio = view.findViewById(R.id.vid_thumb_cache_all_radio);
        vidThumbCacheWithOfflineStreamsRadio = view.findViewById(R.id.vid_thumb_cache_with_offline_streams_radio);
        thumbCacheDirPathTxt = view.findViewById(R.id.thumb_cache_dir_path_txt);
        thumbCacheFsSizeTxt = view.findViewById(R.id.thumb_cache_fs_size_txt);
        thumbCacheClearBnt = view.findViewById(R.id.thumb_cache_clear_btn);

        thumbCacheDirPathTxt.setText(ThumbCacheFsManager.getThumbCacheDir(getContext()).getPath());

        vidThumbCacheNoneRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoThumbCacheStrategy(
                            ThumbCacheFragment.this.getContext(),
                            ConfigOptions.VideoThumbCacheStrategy.NONE);
                }
            }
        });

        vidThumbCacheAllRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoThumbCacheStrategy(
                            ThumbCacheFragment.this.getContext(),
                            ConfigOptions.VideoThumbCacheStrategy.ALL);
                }
            }
        });

        vidThumbCacheWithOfflineStreamsRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoThumbCacheStrategy(
                            ThumbCacheFragment.this.getContext(),
                            ConfigOptions.VideoThumbCacheStrategy.WITH_OFFLINE_STREAMS);
                }
            }
        });

        thumbCacheClearBnt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(ThumbCacheFragment.this.getContext())
                        .setTitle(getString(R.string.clear_thumb_cache_title))
                        .setMessage(getString(R.string.clear_thumb_cache_message))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                ThumbCacheFsManager.clearThumbCache(ThumbCacheFragment.this.getContext());
                                updateThumbCacheFsInfo();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null).show();
            }
        });

        // выставлять значения RadioButton нужно в onResume, а не в onCreate,
        // иначе можно словить нетривильаный неочевидный глюк, но только если таб не первый не счету
        switch (ConfigOptions.getVideoThumbCacheStrategy(this.getContext())) {
            case NONE:
                vidThumbCacheNoneRadio.setChecked(true);
                vidThumbCacheNoneRadio.setSelected(true);
                break;
            case ALL:
                vidThumbCacheAllRadio.setChecked(true);
                vidThumbCacheAllRadio.setSelected(true);
                break;
            case WITH_OFFLINE_STREAMS:
                vidThumbCacheWithOfflineStreamsRadio.setChecked(true);
                vidThumbCacheWithOfflineStreamsRadio.setSelected(true);
                break;
        }

        updateThumbCacheFsInfo();
    }

    private void updateThumbCacheFsInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long cacheTotalFsSize = ThumbCacheFsManager.getThumbCacheFsSize(getContext());

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        thumbCacheFsSizeTxt.setText(StringFormatUtil.formatFileSize(
                                ThumbCacheFragment.this.getContext(),
                                cacheTotalFsSize));
                    }
                });
            }
        }).start();
    }
}
