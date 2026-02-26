package su.sadrobot.yashlang;

/*
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


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.DataIO;
import su.sadrobot.yashlang.controller.PlaylistImportTask;
import su.sadrobot.yashlang.controller.PlaylistInfoActions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.service.PlaylistsImportService;
import su.sadrobot.yashlang.view.ListItemCheckedProvider;
import su.sadrobot.yashlang.view.ListItemSwitchController;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class ImportPlaylistsActivity extends AppCompatActivity {

    public static final String PARAM_PLAYLISTS_JSON = "PARAM_PLAYLISTS_JSON";

    private Toolbar toolbar;

    private View recommendedPlaylistsView;
    private Button playlistsAddBtn;
    private RecyclerView playlistList;

    private final Handler handler = new Handler();
    // достаточно одного фонового потока
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();


    private enum State {
        LOAD_INITIAL_RECOMMENDED, INITIAL_RECOMMENDED
    }

    private State state = State.INITIAL_RECOMMENDED;

    private PlaylistsImportService playlistsImportService;
    private ServiceConnection playlistImportServiceConnection;

    private List<PlaylistInfo> recommendedPlaylists = new ArrayList<>();
    // плейлисты из списка рекомендованных, которые уже есть в локальной базе данных
    private final Set<PlaylistInfo> playlistsInDb = new HashSet<>();
    // или в процессе добавления в сервисе PlaylistImportService
    private final Set<PlaylistInfo> playlistsInImportService = new HashSet<>();
    private final List<PlaylistInfo> playlistsToAdd = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_import_playlists);

        toolbar = findViewById(R.id.toolbar);

        recommendedPlaylistsView = findViewById(R.id.recommended_playlists_view);
        playlistsAddBtn = findViewById(R.id.playlists_add_btn);
        playlistList = findViewById(R.id.playlist_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistList.setLayoutManager(linearLayoutManager);
        playlistList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        playlistsAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistsToAdd.clear();
                for (final PlaylistInfo plInfo : recommendedPlaylists) {
                    if (plInfo.isEnabled()) {
                        playlistsToAdd.add(plInfo);
                    }
                }
                addPlaylistsBg();
            }
        });

        // загрузим после подключения к PlaylistsImportService
        //loadPlaylistsBg();
    }

    @Override
    protected void onResume() {
        super.onResume();

        playlistImportServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                playlistsImportService = ((PlaylistsImportService.PlaylistsImportServiceBinder) service).getService();
                loadPlaylistsBg();

                // здесь будем смотреть, есть ли плейлисты из списка рекомендованных
                // среди задачх импорта, если есть, то пока не разрешем их добавлять
                // и помечаем их не галкой, а песочными часами
                playlistsImportService.setServiceListener(new PlaylistsImportService.PlaylistsImportServiceListener() {
                    @Override
                    public void onImportPlaylistTaskAdded(final PlaylistImportTask importTask) {
                        loadPlaylistsBg();
                    }

                    @Override
                    public void onImportPlaylistTaskRemoved(final PlaylistImportTask importTask) {
                        loadPlaylistsBg();
                    }
                });
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                playlistsImportService.removeServiceListener();
                playlistsImportService = null;
            }
        };

        this.bindService(
                new Intent(this, PlaylistsImportService.class),
                playlistImportServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        if (playlistsImportService != null) {
            playlistsImportService.stopIfFinished();
        }
        this.unbindService(playlistImportServiceConnection);

        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.import_playlists_actions);

        toolbar.setOnMenuItemClickListener(
                new Toolbar.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return onOptionsItemSelected(item);
                    }
                });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_all:
                for (final PlaylistInfo plInfo : recommendedPlaylists) {
                    if (!playlistsInDb.contains(plInfo)) {
                        plInfo.setEnabled(true);
                    }
                }
                playlistList.getAdapter().notifyDataSetChanged();
                break;
            case R.id.action_select_none:
                for (final PlaylistInfo plInfo : recommendedPlaylists) {
                    if (!playlistsInDb.contains(plInfo)) {
                        plInfo.setEnabled(false);
                    }
                }
                playlistList.getAdapter().notifyDataSetChanged();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateControlsVisibility() {
        switch (state) {
            case LOAD_INITIAL_RECOMMENDED:
                recommendedPlaylistsView.setVisibility(View.INVISIBLE);
                break;
            case INITIAL_RECOMMENDED:
                recommendedPlaylistsView.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void loadPlaylistsBg() {
        this.state = State.LOAD_INITIAL_RECOMMENDED;
        updateControlsVisibility();

        // здесь нужно в фоне обратиться к базе данных, чтобы определить, добавлен плейлист
        // в локальную базу или нет (чтобы для добавленных рисовать галочку вместо переключателя
        // и не добавлять их еще раз)
        // дополнительно к базе данных, будем смотреть в сервисе PlaylistImportService, есть
        // ли плейлист в
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // здесь можно было принять объект List<PlaylistInfo>, но в таком случае пришлось бы
                // сделать его Serializable со всеми вложенными классами, а это целая история
                // поэтому будем принимать строку JSON и загружать объекты из нее
                // пока предполагаем, что валидация содержимого строки и выставление правильных параметров
                // загрузки происходит до отправки сюда, например, внутри экрана ImportDataActivity,
                // хотя базовую обработку ошибок все равно нужно сделать и здесь.
                try {
                    recommendedPlaylists = DataIO.loadPlaylistsFromJSON(getIntent().getStringExtra(PARAM_PLAYLISTS_JSON));
                } catch (JSONException e) {
                    // молча получим пустой recommendedPlaylists
                }

                if (ConfigOptions.DEVEL_MODE_ON) {
                    fetchInfoOnline();
                }

                // фоновый поток нужен здесь, т.к. обращаемя к базе данных
                // пробежим по всем плейлистам и соберем те, которые уже есть в локальное базе данных
                playlistsInDb.clear();
                playlistsInImportService.clear();
                for (PlaylistInfo plInfo : recommendedPlaylists) {
                    final PlaylistInfo plInfoExistingInDb = VideoDatabase.getDbInstance(ImportPlaylistsActivity.this).
                            playlistInfoDao().findByUrl(plInfo.getUrl());

                    if (plInfoExistingInDb != null) {
                        // добавлять второй раз не надо
                        // включить обратно не получится, т.к. переключалка для этих плейлистов будет скрыта
                        plInfo.setEnabled(false);
                        playlistsInDb.add(plInfo);
                    } else if (playlistsImportService != null) {
                        // проверить в сервисе импорта плейлистов и если есть, тоже не добавлять.
                        // Рисовать этот плейлист буедм не гаолочкой, а песочными часами.
                        // От сервиса будем еще ловить события добавления/удаления задач, и если после
                        // удаления задачи плейлист окажется в базе, то его уже будем рисовать галочкой
                        // (будет достаточно перестроить этот список)
                        if (playlistsImportService.getImportTaskIdForPlaylistUrl(plInfo.getUrl()) != PlaylistImportTask.ID_NONE) {
                            // добавлять еще раз, пока не завершилась текущая попытка, не надо
                            // включить обратно не получится, т.к. переключалка для этих плейлистов будет скрыта
                            plInfo.setEnabled(false);
                            playlistsInImportService.add(plInfo);
                        }
                    }
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        final PlaylistInfoArrayAdapter recPlsAdapter = new PlaylistInfoArrayAdapter(ImportPlaylistsActivity.this,
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
                                                                PlaylistInfoActions.actionCopyPlaylistName(
                                                                        ImportPlaylistsActivity.this,
                                                                        plInfo);
                                                                break;
                                                            }
                                                            case R.id.action_copy_playlist_url: {
                                                                PlaylistInfoActions.actionCopyPlaylistUrl(
                                                                        ImportPlaylistsActivity.this,
                                                                        plInfo);
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
                                new ListItemSwitchController<PlaylistInfo>() {
                                    @Override
                                    public void onItemCheckedChanged(final CompoundButton buttonView, final int position,
                                                                     final PlaylistInfo item, final boolean isChecked) {
                                        item.setEnabled(isChecked);
                                    }

                                    @Override
                                    public boolean isItemChecked(final PlaylistInfo item) {
                                        return item.isEnabled();
                                    }

                                    @Override
                                    public boolean showItemCheckbox(final PlaylistInfo item) {
                                        return !playlistsInDb.contains(item) && !playlistsInImportService.contains(item);
                                    }
                                },
                                new ListItemCheckedProvider<PlaylistInfo>() {
                                    @Override
                                    public boolean isItemChecked(final PlaylistInfo item) {
                                        return playlistsInDb.contains(item);
                                    }
                                },
                                new ListItemCheckedProvider<PlaylistInfo>() {
                                    @Override
                                    public boolean isItemChecked(final PlaylistInfo item) {
                                        return playlistsInImportService.contains(item);
                                    }
                                });

                        playlistList.setAdapter(recPlsAdapter);

                        ImportPlaylistsActivity.this.state = ImportPlaylistsActivity.State.INITIAL_RECOMMENDED;
                        updateControlsVisibility();
                    }
                });
            }
        });
    }

    private void addPlaylistsBg() {
        for (final PlaylistInfo plInfo : playlistsToAdd) {
            PlaylistsImportService.addPlaylist(this, plInfo.getUrl(), plInfo);
        }
        startActivity(new Intent(this, ImportPlaylistsProgressActivity.class));
    }

    /**
     * Напечатать информацию о плейлистах в консоль (для режима разработки)
     */
    private void fetchInfoOnline() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                for (final PlaylistInfo plInfo : new ArrayList<>(playlistsToAdd)) {
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
