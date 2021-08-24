package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2021.
 *
 * Copyright (C) Anton Moiseev 2021 <github.com/sadr0b0t>
 * ImportPlaylistsActivity.java is part of YaShlang.
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
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.DataIO;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemSwitchListener;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class ImportPlaylistsActivity extends AppCompatActivity {

    public static final String PARAM_PLAYLISTS_JSON = "PARAM_PLAYLISTS_JSON";

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


    private List<PlaylistInfo> recommendedPlaylists = new ArrayList<>();
    private List<PlaylistInfo> playlistsToAdd = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_import_playlists);

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

        // здесь можно было принять объект List<PlaylistInfo>, но в таком случае пришлось бы
        // сделать его Serializible со всеми вложенными классами, а это целая история
        // поэтому будем принимать строку JSON и загружать объекты из нее
        // пока предполагаем, что валидация содержимого строки и выставление правильных параметров
        // загрузки происходит до отправки сюда, например, внутри экрана ImportDataActivity,
        // хотя базовую обработку ошибок все равно нужно сделать и здесь.
        try {
            recommendedPlaylists = DataIO.loadPlaylistsFromJSON(getIntent().getStringExtra(PARAM_PLAYLISTS_JSON));
        } catch (JSONException e) {
            // молча получим пустой recommendedPlaylists
        }

        final PlaylistInfoArrayAdapter recPlsAdapter = new PlaylistInfoArrayAdapter(this,
                recommendedPlaylists,
                new OnListItemClickListener<PlaylistInfo>() {
                    @Override
                    public void onItemClick(final View view, final int position, final PlaylistInfo item) {
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final PlaylistInfo plInfo) {

                        // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                        // работает только вариант Gravity.RIGHT
                        //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                        final PopupMenu popup = new PopupMenu(ImportPlaylistsActivity.this,
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

                                                Toast.makeText(ImportPlaylistsActivity.this,
                                                        getString(R.string.copied) + ": " + plInfo.getName(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_playlist_url: {
                                                final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ImportPlaylistsActivity.this,
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
                },
                new OnListItemSwitchListener<PlaylistInfo>() {
                    @Override
                    public void onItemCheckedChanged(final CompoundButton buttonView, final int position,
                                                     final PlaylistInfo item, final boolean isChecked) {
                        item.setEnabled(isChecked);
                    }

                    @Override
                    public boolean isItemChecked(final PlaylistInfo item) {
                        return item.isEnabled();
                    }
                });

        playlistList.setAdapter(recPlsAdapter);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        playlistsAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistsToAdd.clear();
                for(final PlaylistInfo plInfo : recommendedPlaylists) {
                    if(plInfo.isEnabled()) {
                        playlistsToAdd.add(plInfo);
                    }
                }
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
                ImportPlaylistsActivity.this.finish();
            }
        });

        if (ConfigOptions.DEVEL_MODE_ON) {
            fetchInfoOnline();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (taskController != null) {
            taskController.cancel();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateControlsVisibility() {
        switch (state) {
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
                        if (taskController.getException() == null) {
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
                for (; plToAddStartIndex < playlistsToAdd.size(); plToAddStartIndex++) {
                    if (taskController.isCanceled()) {
                        allOk = false;
                        break;
                    }

                    final PlaylistInfo plInfo = playlistsToAdd.get(plToAddStartIndex);
                    // подгрузим иконку плейлиста (хотя скорее всего она уже в кеше)
                    try {
                        // иконка канала
                        if (plInfo.getThumbBitmap() == null) {
                            final Bitmap _plThumb = VideoThumbManager.getInstance().loadPlaylistThumb(
                                    ImportPlaylistsActivity.this, plInfo.getThumbUrl());
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
                    final PlaylistInfo existingPlInfo = VideoDatabase.getDbInstance(ImportPlaylistsActivity.this).
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
                                ImportPlaylistsActivity.this, plInfo.getUrl(), taskController);
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
                    ImportPlaylistsActivity.this.finish();
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

                for (final PlaylistInfo plInfo : playlistsToAdd) {
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

