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

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

/**
 *
 */
public class ExportDataActivity extends AppCompatActivity {

    private RadioButton exportMarkdownRadio;
    private RadioButton exportYtdlScriptRadio;

    private Switch exportPlaylistListSwitch;

    private Switch exportPlaylistItemsSwitch;
    private Switch exportOnlyEnabledPlaylistsSwitch;
    private Switch exportSkipBlockedSwitch;
    private Switch exportPlaylistItemsNamesSwitch;
    private Switch exportPlaylistItemsUrlsSwitch;

    private Switch exportStarredSwitch;
    private Switch exportBlacklistSwitch;

    private TextView saveDirTxt;

    private ProgressBar saveProgress;
    private TextView savedOkTxt;
    private TextView saveErrorTxt;

    private Button exportToFileBtn;

    private Handler handler = new Handler();

    private boolean isSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_export_data);

        exportMarkdownRadio = findViewById(R.id.export_markdown_radio);
        exportYtdlScriptRadio = findViewById(R.id.export_ytdl_script_radio);

        exportPlaylistListSwitch = findViewById(R.id.export_playlist_list_switch);
        exportPlaylistItemsSwitch = findViewById(R.id.export_playlist_items_switch);
        exportOnlyEnabledPlaylistsSwitch = findViewById(R.id.export_only_enabled_playlists);
        exportSkipBlockedSwitch = findViewById(R.id.export_skip_blocked);
        exportPlaylistItemsNamesSwitch = findViewById(R.id.export_playlist_items_names);
        exportPlaylistItemsUrlsSwitch = findViewById(R.id.export_playlist_items_urls);
        exportStarredSwitch = findViewById(R.id.export_starred_switch);
        exportBlacklistSwitch = findViewById(R.id.export_blacklist_switch);

        saveDirTxt = findViewById(R.id.save_dir_txt);

        saveProgress = findViewById(R.id.save_progress);
        savedOkTxt = findViewById(R.id.saved_ok_txt);
        saveErrorTxt = findViewById(R.id.save_error_txt);

        exportToFileBtn = findViewById(R.id.export_btn);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //

        updateControlsEnabledStates();

        exportMarkdownRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportYtdlScriptRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportPlaylistListSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportPlaylistItemsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportPlaylistItemsNamesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportPlaylistItemsUrlsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportStarredSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportBlacklistSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsEnabledStates();
            }
        });

        exportToFileBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSaving = true;
                updateControlsEnabledStates();
                saveProgress.setVisibility(View.VISIBLE);
                savedOkTxt.setVisibility(View.GONE);
                saveErrorTxt.setVisibility(View.GONE);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final String fileExt;
                            if(exportMarkdownRadio.isChecked()) {
                                fileExt = "md";
                            } else if(exportYtdlScriptRadio.isChecked()) {
                                fileExt = "sh";
                            } else {
                                // never here
                                fileExt = "txt";
                            }
                            final String exportString = exportPlaylistsToMarkdownOrYtdlScript();
                            final File file = saveToFile(ExportDataActivity.this, fileExt, exportString);
                            isSaving = false;

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateControlsEnabledStates();
                                    savedOkTxt.setText(getString(R.string.saved_to_file) + ": " + file.getName());
                                    savedOkTxt.setVisibility(View.VISIBLE);
                                    saveProgress.setVisibility(View.GONE);
                                }
                            });
                        } catch (final IOException e) {
                            isSaving = false;

                            //e.printStackTrace();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateControlsEnabledStates();
                                    saveErrorTxt.setText(getString(R.string.failed_to_save) + ":\n" + e.getMessage());
                                    saveErrorTxt.setVisibility(View.VISIBLE);
                                    saveProgress.setVisibility(View.GONE);
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        saveDirTxt.setText(this.getString(R.string.save_dir) + ": " + this.getExternalFilesDir(null).getAbsolutePath());
    }


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateControlsEnabledStates() {
        if(isSaving) {
            exportToFileBtn.setEnabled(false);

            exportMarkdownRadio.setEnabled(false);
            exportYtdlScriptRadio.setEnabled(false);

            exportPlaylistListSwitch.setEnabled(false);
            exportPlaylistItemsSwitch.setEnabled(false);
            exportOnlyEnabledPlaylistsSwitch.setEnabled(false);
            exportSkipBlockedSwitch.setEnabled(false);
            exportPlaylistItemsNamesSwitch.setEnabled(false);
            exportPlaylistItemsUrlsSwitch.setEnabled(false);
            exportStarredSwitch.setEnabled(false);
            exportBlacklistSwitch.setEnabled(false);
        } else {
            if (!exportPlaylistListSwitch.isChecked() &&
                    !exportStarredSwitch.isChecked() &&
                    !exportBlacklistSwitch.isChecked() &&
                    (!exportPlaylistItemsSwitch.isChecked() ||
                            (!exportYtdlScriptRadio.isChecked() &&
                                    !exportPlaylistItemsNamesSwitch.isChecked() &&
                                    !exportPlaylistItemsUrlsSwitch.isChecked()))) {
                exportToFileBtn.setEnabled(false);
            } else {
                exportToFileBtn.setEnabled(true);
            }

            exportMarkdownRadio.setEnabled(true);
            exportYtdlScriptRadio.setEnabled(true);

            exportPlaylistListSwitch.setEnabled(!exportYtdlScriptRadio.isChecked());
            exportPlaylistItemsSwitch.setEnabled(true);
            exportOnlyEnabledPlaylistsSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked());
            exportSkipBlockedSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked());
            exportPlaylistItemsNamesSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked() && !exportYtdlScriptRadio.isChecked());
            exportPlaylistItemsUrlsSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked() && !exportYtdlScriptRadio.isChecked());
            exportStarredSwitch.setEnabled(true);
            exportBlacklistSwitch.setEnabled(true);
        }
    }

    private String exportPlaylistsToMarkdownOrYtdlScript() {
        final StringBuilder stringBuilder = new StringBuilder();

        if(exportYtdlScriptRadio.isChecked()) {
            stringBuilder.append("#!/bin/sh").append("\n");
        }

        if (exportPlaylistListSwitch.isChecked() && !exportYtdlScriptRadio.isChecked()) {
            stringBuilder.append("# Playlists").append("\n");

            final List<PlaylistInfo> playlists = VideoDatabase.getDbInstance(this).
                    playlistInfoDao().getAll();

            for (final PlaylistInfo plInfo : playlists) {
                stringBuilder.append("[").append(plInfo.getName()).append("]");
                stringBuilder.append("(").append(plInfo.getUrl()).append(")");
                stringBuilder.append("  \n");
            }
            stringBuilder.append("  \n");
        }

        if (exportPlaylistItemsSwitch.isChecked()) {
            stringBuilder.append("# Playlists with items").append("\n");

            final List<PlaylistInfo> playlists = exportOnlyEnabledPlaylistsSwitch.isChecked() ?
                    VideoDatabase.getDbInstance(this).playlistInfoDao().getEnabled() :
                    VideoDatabase.getDbInstance(this).playlistInfoDao().getAll();

            for (final PlaylistInfo plInfo : playlists) {
                stringBuilder.append("# ");
                stringBuilder.append("[").append(plInfo.getName()).append("]");
                stringBuilder.append("(").append(plInfo.getUrl()).append(")");
                stringBuilder.append("\n");

                final List<VideoItem> videoItems = exportSkipBlockedSwitch.isChecked() ?
                        VideoDatabase.getDbInstance(this).
                                videoItemDao().getByPlaylist(plInfo.getId()) :
                        VideoDatabase.getDbInstance(this).
                        videoItemDao().getByPlaylistAll(plInfo.getId());
                for (final VideoItem videoItem : videoItems) {

                    if (exportYtdlScriptRadio.isChecked()) {
                        stringBuilder.append("youtube-dl " + videoItem.getItemUrl());
                        stringBuilder.append("\n");
                    } else if (exportPlaylistItemsNamesSwitch.isChecked() && exportPlaylistItemsUrlsSwitch.isChecked()) {
                        stringBuilder.append("[").append(videoItem.getName()).append("]");
                        stringBuilder.append("(").append(videoItem.getItemUrl()).append(")");
                        stringBuilder.append("  \n");
                    } else if (exportPlaylistItemsNamesSwitch.isChecked()) {
                        stringBuilder.append(videoItem.getName());
                        stringBuilder.append("  \n");
                    } else if (exportPlaylistItemsUrlsSwitch.isChecked()) {
                        stringBuilder.append(videoItem.getItemUrl());
                        stringBuilder.append("  \n");
                    }
                }
                stringBuilder.append("  \n");
            }
        }

        if (exportStarredSwitch.isChecked()) {
            stringBuilder.append("# Starred").append("\n");

            final List<VideoItem> videoItems = VideoDatabase.getDbInstance(this).
                    videoItemDao().getStarred();
            for (final VideoItem videoItem : videoItems) {
                if (exportYtdlScriptRadio.isChecked()) {
                    stringBuilder.append("youtube-dl " + videoItem.getItemUrl());
                    stringBuilder.append("\n");
                } else {
                    stringBuilder.append("[").append(videoItem.getName()).append("]");
                    stringBuilder.append("(").append(videoItem.getItemUrl()).append(")");
                    stringBuilder.append("  \n");
                }
            }
            stringBuilder.append("  \n");
        }

        if (exportBlacklistSwitch.isChecked()) {
            stringBuilder.append("# Blacklist").append("\n");

            final List<VideoItem> videoItems = VideoDatabase.getDbInstance(this).
                    videoItemDao().getBlacklist();
            for (final VideoItem videoItem : videoItems) {
                if (exportYtdlScriptRadio.isChecked()) {
                    stringBuilder.append("youtube-dl " + videoItem.getItemUrl());
                    stringBuilder.append("\n");
                } else {
                    stringBuilder.append("[").append(videoItem.getName()).append("]");
                    stringBuilder.append("(").append(videoItem.getItemUrl()).append(")");
                    stringBuilder.append("  \n");
                }
            }
            stringBuilder.append("  \n");
        }

        return stringBuilder.toString();
    }

    private File saveToFile(final Context context, final String fileExt, final String contentStr) throws IOException {
        // https://www.zoftino.com/saving-files-to-internal-storage-&-external-storage-in-android
        // https://stackoverflow.com/questions/51565897/saving-files-in-android-for-beginners-internal-external-storage

        // пользователь не сможет жмякать кнопку больше раза в секунду,
        // поэтому идентификатор с секундной точностью можно считать уникальным
        // (а даже если жмякнет, ничего страшного - перезапишет только что сохраненный файл)
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

        final String fileName = "yashlang-video-db-%s.%ext".replace("%ext", fileExt).replace("%s", timestamp);

        final File file = new File(context.getExternalFilesDir(null), fileName);
        final FileWriter fw = new FileWriter(file);

        System.out.println(file.getAbsolutePath());

        try {
            file.createNewFile();
            fw.write(contentStr);
            fw.flush();
        } finally {
            fw.close();
        }
        return file;
    }
}
