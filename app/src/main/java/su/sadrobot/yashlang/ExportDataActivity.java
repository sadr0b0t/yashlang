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

import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import su.sadrobot.yashlang.controller.DataIO;

/**
 *
 */
public class ExportDataActivity extends AppCompatActivity {

    private RadioButton exportMarkdownRadio;
    private RadioButton exportYtdlScriptRadio;
    private RadioButton exportJsonRadio;

    private Switch exportPlaylistListSwitch;
    private Switch exportOnlyEnabledPlaylists1Switch;
    private Switch exportProfilesSwitch;

    private Switch exportPlaylistItemsSwitch;
    private Switch exportOnlyEnabledPlaylists2Switch;
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
    private Button exportToClipboardBtn;

    private final Handler handler = new Handler();

    private enum State {
        INITIAL, DATA_EXPORT_PROGRESS, DATA_EXPORT_ERROR, DATA_EXPORT_OK
    }

    private State state = State.INITIAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_export_data);

        exportMarkdownRadio = findViewById(R.id.export_markdown_radio);
        exportYtdlScriptRadio = findViewById(R.id.export_ytdl_script_radio);
        exportJsonRadio = findViewById(R.id.export_json_radio);

        exportPlaylistListSwitch = findViewById(R.id.export_playlist_list_switch);
        exportOnlyEnabledPlaylists1Switch = findViewById(R.id.export_only_enabled_playlists1);
        exportProfilesSwitch = findViewById(R.id.export_profiles_switch);
        exportPlaylistItemsSwitch = findViewById(R.id.export_playlist_items_switch);
        exportOnlyEnabledPlaylists2Switch = findViewById(R.id.export_only_enabled_playlists2);
        exportSkipBlockedSwitch = findViewById(R.id.export_skip_blocked);
        exportPlaylistItemsNamesSwitch = findViewById(R.id.export_playlist_items_names);
        exportPlaylistItemsUrlsSwitch = findViewById(R.id.export_playlist_items_urls);
        exportStarredSwitch = findViewById(R.id.export_starred_switch);
        exportBlacklistSwitch = findViewById(R.id.export_blacklist_switch);

        saveDirTxt = findViewById(R.id.save_dir_txt);

        saveProgress = findViewById(R.id.save_progress);
        savedOkTxt = findViewById(R.id.saved_ok_txt);
        saveErrorTxt = findViewById(R.id.save_error_txt);

        exportToFileBtn = findViewById(R.id.export_to_file_btn);
        exportToClipboardBtn = findViewById(R.id.export_to_clipboard_btn);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //
        updateControlsStates();

        exportMarkdownRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportYtdlScriptRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportJsonRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportPlaylistListSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportProfilesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportPlaylistItemsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportPlaylistItemsNamesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportPlaylistItemsUrlsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportStarredSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportBlacklistSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsStates();
            }
        });

        exportToFileBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                state = State.DATA_EXPORT_PROGRESS;
                updateControlsStates();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final String fileExt;
                            if (exportMarkdownRadio.isChecked()) {
                                fileExt = "md";
                            } else if (exportYtdlScriptRadio.isChecked()) {
                                fileExt = "sh";
                            } else if (exportJsonRadio.isChecked()) {
                                fileExt = "json";
                            } else {
                                // never here
                                fileExt = "txt";
                            }
                            final String exportString;
                            if (exportJsonRadio.isChecked()) {
                                exportString = exportPlaylistsToJSON();
                            } else {
                                exportString = exportPlaylistsToMarkdownOrYtdlScript();
                            }
                            final File file = DataIO.saveToFile(ExportDataActivity.this, fileExt, exportString);

                            state = State.DATA_EXPORT_OK;

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateControlsStates();
                                    savedOkTxt.setText(getString(R.string.saved_to_file) + ": " + file.getName());

                                    Toast.makeText(ExportDataActivity.this,
                                            getString(R.string.saved_to_file) + ": " + file.getName(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (final Exception e) {
                            state = State.DATA_EXPORT_ERROR;

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateControlsStates();
                                    saveErrorTxt.setText(getString(R.string.failed_to_save) + ":\n" + e.getMessage());

                                    Toast.makeText(ExportDataActivity.this,
                                            getString(R.string.failed_to_save) + ":\n" + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        exportToClipboardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state = State.DATA_EXPORT_PROGRESS;
                updateControlsStates();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final String label;
                            if (exportMarkdownRadio.isChecked()) {
                                label = "md";
                            } else if (exportYtdlScriptRadio.isChecked()) {
                                label = "sh";
                            } else if (exportJsonRadio.isChecked()) {
                                label = "json";
                            } else {
                                // never here
                                label = "txt";
                            }

                            final String exportString;
                            if (exportJsonRadio.isChecked()) {
                                exportString = exportPlaylistsToJSON();
                            } else {
                                exportString = exportPlaylistsToMarkdownOrYtdlScript();
                            }

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                    final ClipData clip = ClipData.newPlainText(label, exportString);
                                    try {
                                        clipboard.setPrimaryClip(clip);

                                        state = State.DATA_EXPORT_OK;

                                        updateControlsStates();
                                        savedOkTxt.setText(getString(R.string.copied));

                                        Toast.makeText(ExportDataActivity.this,
                                                getString(R.string.copied),
                                                Toast.LENGTH_LONG).show();
                                    } catch (Exception e) {
                                        // для JSON скорее всего поймаем TransactionTooLargeException
                                        state = State.DATA_EXPORT_ERROR;

                                        updateControlsStates();
                                        saveErrorTxt.setText(getString(R.string.failed_to_save) + ":\n" + e.getMessage());

                                        Toast.makeText(ExportDataActivity.this,
                                                getString(R.string.failed_to_save) + ":\n" + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        } catch (final Exception e) {
                            state = State.DATA_EXPORT_ERROR;

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateControlsStates();
                                    saveErrorTxt.setText(getString(R.string.failed_to_save) + ":\n" + e.getMessage());

                                    Toast.makeText(ExportDataActivity.this,
                                            getString(R.string.failed_to_save) + ":\n" + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        saveDirTxt.setText(this.getString(R.string.save_dir) + ": " + this.getExternalFilesDir(null).getAbsolutePath());

        updateControlsStates();
    }


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateControlsStates() {
        if (state == State.INITIAL) {
            saveProgress.setVisibility(View.GONE);
            saveErrorTxt.setVisibility(View.GONE);
            savedOkTxt.setVisibility(View.GONE);
        } else if (state == State.DATA_EXPORT_PROGRESS) {
            saveProgress.setVisibility(View.VISIBLE);
            saveErrorTxt.setVisibility(View.GONE);
            savedOkTxt.setVisibility(View.GONE);
        } else if (state == State.DATA_EXPORT_ERROR) {
            saveProgress.setVisibility(View.GONE);
            saveErrorTxt.setVisibility(View.VISIBLE);
            savedOkTxt.setVisibility(View.GONE);
        } else if (state == State.DATA_EXPORT_OK) {
            saveProgress.setVisibility(View.GONE);
            saveErrorTxt.setVisibility(View.GONE);
            savedOkTxt.setVisibility(View.VISIBLE);
        }

        if (state == State.DATA_EXPORT_PROGRESS) {
            exportToFileBtn.setEnabled(false);
            exportToClipboardBtn.setEnabled(false);

            exportMarkdownRadio.setEnabled(false);
            exportYtdlScriptRadio.setEnabled(false);
            exportJsonRadio.setEnabled(false);

            exportPlaylistListSwitch.setEnabled(false);
            exportOnlyEnabledPlaylists1Switch.setEnabled(false);
            exportProfilesSwitch.setEnabled(false);
            exportPlaylistItemsSwitch.setEnabled(false);
            exportOnlyEnabledPlaylists2Switch.setEnabled(false);
            exportSkipBlockedSwitch.setEnabled(false);
            exportPlaylistItemsNamesSwitch.setEnabled(false);
            exportPlaylistItemsUrlsSwitch.setEnabled(false);
            exportStarredSwitch.setEnabled(false);
            exportBlacklistSwitch.setEnabled(false);
        } else {
            exportMarkdownRadio.setEnabled(true);
            exportYtdlScriptRadio.setEnabled(true);
            exportJsonRadio.setEnabled(true);

            if (exportMarkdownRadio.isChecked()) {
                exportPlaylistListSwitch.setEnabled(true);
                exportOnlyEnabledPlaylists1Switch.setEnabled(exportPlaylistListSwitch.isChecked());
                exportProfilesSwitch.setEnabled(true);
                exportPlaylistItemsSwitch.setEnabled(true);
                exportOnlyEnabledPlaylists2Switch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportSkipBlockedSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportPlaylistItemsNamesSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportPlaylistItemsUrlsSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportStarredSwitch.setEnabled(true);
                exportBlacklistSwitch.setEnabled(true);

                if (exportPlaylistListSwitch.isChecked() ||
                        exportProfilesSwitch.isChecked() ||
                        exportStarredSwitch.isChecked() ||
                        exportBlacklistSwitch.isChecked() ||
                        (exportPlaylistItemsSwitch.isChecked() &&
                                (exportPlaylistItemsNamesSwitch.isChecked() ||
                                        exportPlaylistItemsUrlsSwitch.isChecked()))) {
                    exportToFileBtn.setEnabled(true);
                    exportToClipboardBtn.setEnabled(true);
                } else {
                    exportToFileBtn.setEnabled(false);
                    exportToClipboardBtn.setEnabled(false);
                }
            } else if (exportYtdlScriptRadio.isChecked()) {
                exportPlaylistListSwitch.setEnabled(false);
                exportOnlyEnabledPlaylists1Switch.setEnabled(false);
                exportProfilesSwitch.setEnabled(false);
                exportPlaylistItemsSwitch.setEnabled(true);
                exportOnlyEnabledPlaylists2Switch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportSkipBlockedSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportPlaylistItemsNamesSwitch.setEnabled(false);
                exportPlaylistItemsUrlsSwitch.setEnabled(false);
                exportStarredSwitch.setEnabled(true);
                exportBlacklistSwitch.setEnabled(true);

                if (exportStarredSwitch.isChecked() ||
                        exportBlacklistSwitch.isChecked() ||
                        exportPlaylistItemsSwitch.isChecked()) {
                    exportToFileBtn.setEnabled(true);
                    exportToClipboardBtn.setEnabled(true);
                } else {
                    exportToFileBtn.setEnabled(false);
                    exportToClipboardBtn.setEnabled(false);
                }
            } else {// if(exportJsonRadio.isChecked()) {
                exportPlaylistListSwitch.setEnabled(true);
                exportOnlyEnabledPlaylists1Switch.setEnabled(exportPlaylistListSwitch.isChecked());
                exportProfilesSwitch.setEnabled(true);
                exportPlaylistItemsSwitch.setEnabled(true);
                exportOnlyEnabledPlaylists2Switch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportSkipBlockedSwitch.setEnabled(exportPlaylistItemsSwitch.isChecked());
                exportPlaylistItemsNamesSwitch.setEnabled(false);
                exportPlaylistItemsUrlsSwitch.setEnabled(false);
                exportStarredSwitch.setEnabled(true);
                exportBlacklistSwitch.setEnabled(true);

                if (exportPlaylistListSwitch.isChecked() ||
                        exportProfilesSwitch.isChecked() ||
                        exportStarredSwitch.isChecked() ||
                        exportBlacklistSwitch.isChecked() ||
                        (exportPlaylistItemsSwitch.isChecked() &&
                                (exportPlaylistItemsNamesSwitch.isChecked() ||
                                        exportPlaylistItemsUrlsSwitch.isChecked()))) {
                    exportToFileBtn.setEnabled(true);
                    exportToClipboardBtn.setEnabled(true);
                } else {
                    exportToFileBtn.setEnabled(false);
                    exportToClipboardBtn.setEnabled(false);
                }
            }
        }
    }

    private String exportPlaylistsToJSON() throws Exception {
        return DataIO.exportPlaylistsToJSON(
                this,
                exportPlaylistListSwitch.isChecked(),
                exportOnlyEnabledPlaylists1Switch.isChecked(),
                exportProfilesSwitch.isChecked(),
                exportPlaylistItemsSwitch.isChecked(),
                exportOnlyEnabledPlaylists2Switch.isChecked(),
                exportSkipBlockedSwitch.isChecked(),
                exportStarredSwitch.isChecked(),
                exportBlacklistSwitch.isChecked()).toString();
    }

    private String exportPlaylistsToMarkdownOrYtdlScript() {
        return DataIO.exportPlaylistsToMarkdownOrYtdlScript(
                this,
                exportYtdlScriptRadio.isChecked(),
                exportPlaylistListSwitch.isChecked(),
                exportOnlyEnabledPlaylists1Switch.isChecked(),
                exportProfilesSwitch.isChecked(),
                exportPlaylistItemsSwitch.isChecked(),
                exportOnlyEnabledPlaylists2Switch.isChecked(),
                exportSkipBlockedSwitch.isChecked(),
                exportPlaylistItemsNamesSwitch.isChecked(),
                exportPlaylistItemsUrlsSwitch.isChecked(),
                exportStarredSwitch.isChecked(),
                exportBlacklistSwitch.isChecked());
    }
}
