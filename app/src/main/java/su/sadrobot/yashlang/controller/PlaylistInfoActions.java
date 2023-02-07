package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import su.sadrobot.yashlang.ConfigurePlaylistActivity;
import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;

public class PlaylistInfoActions {

    public interface OnPlaylistDeletedListener {
        void onPlaylistDeleted();
    }

    public interface OnPlaylistEnabledChangeListener {
        void onPlaylistEnabledChange();
    }

    public static void actionConfigurePlaylist(final Context context, final long playlistId) {
        final Intent intent = new Intent(context, ConfigurePlaylistActivity.class);
        intent.putExtra(ConfigurePlaylistActivity.PARAM_PLAYLIST_ID, playlistId);
        context.startActivity(intent);
    }

    public static void actionCopyPlaylistName(final Context context, final PlaylistInfo plInfo) {
        final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
        clipboard.setPrimaryClip(clip);

        Toast.makeText(context,
                context.getString(R.string.copied) + ": " + plInfo.getName(),
                Toast.LENGTH_LONG).show();
    }

    public static void actionCopyPlaylistUrl(final Context context, final PlaylistInfo plInfo) {
        final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
        clipboard.setPrimaryClip(clip);

        Toast.makeText(context,
                context.getString(R.string.copied) + ": " + plInfo.getUrl(),
                Toast.LENGTH_LONG).show();
    }

    public static void actionDeletePlaylist(
            final Context context, final long playlistId,
            final OnPlaylistDeletedListener callback) {
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.delete_playlist_title))
                .setMessage(context.getString(R.string.delete_playlist_message))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final VideoDatabase videodb = VideoDatabase.getDbInstance(context);
                                final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(playlistId);
                                videodb.playlistInfoDao().delete(plInfo);

                                if (callback != null) {
                                    callback.onPlaylistDeleted();
                                }
                            }
                        }).start();

                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
    }

    public static void actionSetPlaylistEnabled(
            final Context context, final long playlistId, final boolean enabled,
            OnPlaylistEnabledChangeListener callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                VideoDatabase.getDbInstance(context).playlistInfoDao().setEnabled(playlistId, enabled);
                if (callback != null) {
                    callback.onPlaylistEnabledChange();
                }
            }
        }).start();
    }
}
