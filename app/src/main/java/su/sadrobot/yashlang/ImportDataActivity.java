package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2021.
 *
 * Copyright (C) Anton Moiseev 2021 <github.com/sadr0b0t>
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

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import su.sadrobot.yashlang.controller.DataIO;
import su.sadrobot.yashlang.model.PlaylistInfo;

/**
 *
 */
public class ImportDataActivity extends AppCompatActivity {

    private static final int CODE_REQUEST_FILE = 1;

    private EditText loadFilePathTxt;
    private TextView exportDirLocationTxt;
    private ImageButton openFileBtn;

    private ProgressBar loadProgress;
    private TextView loadedOkTxt;
    private TextView loadErrorTxt;

    private Button loadFileBtn;
    private Button loadFromClipboardBtn;
    private Button importDataBtn;

    private final Handler handler = new Handler();

    private enum State {
        INITIAL, DATA_LOAD_PROGRESS,DATA_LOAD_ERROR, DATA_LOAD_OK
    }

    private State state = State.INITIAL;
    private Uri contentUri;
    private String loadedFileContent;
    private List<PlaylistInfo> loadedPlaylists;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_import_data);

        loadFilePathTxt = findViewById(R.id.load_file_path_txt);
        exportDirLocationTxt = findViewById(R.id.export_dir_location_txt);
        openFileBtn = findViewById(R.id.open_file_btn);

        loadProgress = findViewById(R.id.load_progress);
        loadedOkTxt = findViewById(R.id.loaded_ok_txt);
        loadErrorTxt = findViewById(R.id.load_error_txt);

        loadFileBtn = findViewById(R.id.load_file_btn);
        loadFromClipboardBtn = findViewById(R.id.load_from_clipboard_btn);
        importDataBtn = findViewById(R.id.import_btn);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        loadFilePathTxt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateControlsVisibility();
            }
        });

        openFileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // https://riptutorial.com/android/example/14425/showing-a-file-chooser-and-reading-the-result
                // other:
                // https://developer.android.com/guide/topics/providers/document-provider.html
                // https://stackoverflow.com/questions/41846284/standart-file-chooser-component-in-android-how-to
                // https://stackoverflow.com/questions/58550549/how-to-use-intent-action-open-document-in-android-pie
                // https://stackoverflow.com/questions/13209494/how-to-get-the-full-file-path-from-uri
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

                // Update with mime types
                intent.setType("*/*");

                // Only pick openable and local files.
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

                // REQUEST_CODE = <some-integer>
                startActivityForResult(intent, CODE_REQUEST_FILE);
            }
        });

        loadFileBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadedFileContent = null;
                loadedPlaylists = null;
                state = State.DATA_LOAD_PROGRESS;
                updateControlsVisibility();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            loadedFileContent = DataIO.loadFromUri(ImportDataActivity.this, contentUri);
                            // здесь небольшой (возможно, временный) хак - если исходная строка
                            // слишком длинная (например, если в файле сохранены списки роликов),
                            // то её не получится передать в активити через интент, поэтому
                            // возьмем здесь только необходимое - список плейлистов без роликов,
                            // в таком случае строка будет достаточно короткой.
                            final JSONObject jsonRoot = new JSONObject(loadedFileContent);
                            final JSONArray jsonPlaylists = jsonRoot.getJSONArray("playlists");
                            final JSONObject jsonRoot1 = new JSONObject();
                            jsonRoot1.put("playlists", jsonPlaylists);
                            loadedFileContent = jsonRoot1.toString();

                            loadedPlaylists = DataIO.loadPlaylistsFromJSON(loadedFileContent);

                            if(loadedPlaylists.size() > 0) {
                                state = State.DATA_LOAD_OK;

                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        loadedOkTxt.setText(getString(R.string.loaded_file) + ": " + contentUri.getPath());
                                        updateControlsVisibility();
                                    }
                                });
                            } else {
                                state = State.DATA_LOAD_ERROR;

                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        loadErrorTxt.setText(getString(R.string.no_playlists_to_import));
                                        updateControlsVisibility();
                                    }
                                });
                            }
                        } catch (final Exception e) {
                            state = State.DATA_LOAD_ERROR;

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    loadErrorTxt.setText(getString(R.string.failed_to_load) + ":\n" + e.getMessage());
                                    updateControlsVisibility();
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        loadFromClipboardBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadedFileContent = null;
                loadedPlaylists = null;
                state = State.DATA_LOAD_PROGRESS;
                updateControlsVisibility();

                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard.getPrimaryClip().getItemCount() > 0) {
                    loadedFileContent = clipboard.getPrimaryClip().getItemAt(0).getText().toString();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // здесь небольшой (возможно, временный) хак - если исходная строка
                                // слишком длинная (например, если в файле сохранены списки роликов),
                                // то её не получится передать в активити через интент, поэтому
                                // возьмем здесь только необходимое - список плейлистов без роликов,
                                // в таком случае строка будет достаточно короткой.
                                final JSONObject jsonRoot = new JSONObject(loadedFileContent);
                                final JSONArray jsonPlaylists = jsonRoot.getJSONArray("playlists");
                                final JSONObject jsonRoot1 = new JSONObject();
                                jsonRoot1.put("playlists", jsonPlaylists);
                                loadedFileContent = jsonRoot1.toString();

                                loadedPlaylists = DataIO.loadPlaylistsFromJSON(loadedFileContent);

                                if(loadedPlaylists.size() > 0) {
                                    state = State.DATA_LOAD_OK;

                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            loadedOkTxt.setText(getString(R.string.loaded_from_clipboard_ok));
                                            updateControlsVisibility();
                                        }
                                    });
                                } else {
                                    state = State.DATA_LOAD_ERROR;

                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            loadErrorTxt.setText(getString(R.string.no_playlists_to_import));
                                            updateControlsVisibility();
                                        }
                                    });
                                }
                            } catch (final Exception e) {
                                state = State.DATA_LOAD_ERROR;

                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        loadErrorTxt.setText(getString(R.string.failed_to_load) + ":\n" + e.getMessage());
                                        updateControlsVisibility();
                                    }
                                });
                            }
                        }
                    }).start();

                } else {
                    state = State.DATA_LOAD_ERROR;
                    loadErrorTxt.setText(getString(R.string.clipboard_is_empty));
                    updateControlsVisibility();
                }
            }
        });

        importDataBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(ImportDataActivity.this, ImportPlaylistsActivity.class);
                intent.putExtra(ImportPlaylistsActivity.PARAM_PLAYLISTS_JSON, loadedFileContent);
                startActivity(intent);
            }
        });

        //
        exportDirLocationTxt.setText(getText(R.string.export_dir_location).toString().replace("%s",
                getExternalFilesDir(null).getAbsolutePath()));

        //
        updateControlsVisibility();
    }


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // If the user doesn't pick a file just return
        if (requestCode != CODE_REQUEST_FILE || resultCode != RESULT_OK) {
            return;
        }

        contentUri = data.getData();
        loadFilePathTxt.setText(contentUri.getPath());
    }

    private void updateControlsVisibility() {
        final boolean fileUriSet = contentUri != null;

        if(state == State.INITIAL) {
            loadProgress.setVisibility(View.GONE);
            loadErrorTxt.setVisibility(View.GONE);
            loadedOkTxt.setVisibility(View.GONE);

            loadFilePathTxt.setEnabled(true);
            if(fileUriSet) {
                loadFileBtn.setEnabled(true);
            } else {
                loadFileBtn.setEnabled(false);
            }
            importDataBtn.setEnabled(false);
        } else if (state == State.DATA_LOAD_PROGRESS) {
            loadProgress.setVisibility(View.VISIBLE);
            loadErrorTxt.setVisibility(View.GONE);
            loadedOkTxt.setVisibility(View.GONE);

            loadFilePathTxt.setEnabled(false);
            loadFileBtn.setEnabled(false);
            importDataBtn.setEnabled(false);
        } else if (state == State.DATA_LOAD_ERROR) {
            loadProgress.setVisibility(View.GONE);
            loadErrorTxt.setVisibility(View.VISIBLE);
            loadedOkTxt.setVisibility(View.GONE);

            loadFilePathTxt.setEnabled(true);
            if(fileUriSet) {
                loadFileBtn.setEnabled(true);
            } else {
                loadFileBtn.setEnabled(false);
            }
            importDataBtn.setEnabled(false);
        } else if(state == State.DATA_LOAD_OK) {
            loadProgress.setVisibility(View.GONE);
            loadErrorTxt.setVisibility(View.GONE);
            loadedOkTxt.setVisibility(View.VISIBLE);

            loadFilePathTxt.setEnabled(true);
            if(fileUriSet) {
                loadFileBtn.setEnabled(true);
            } else {
                loadFileBtn.setEnabled(false);
            }
            importDataBtn.setEnabled(true);
        }
    }
}
