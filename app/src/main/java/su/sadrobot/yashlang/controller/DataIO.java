package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2021.
 *
 * Copyright (C) Anton Moiseev 2021 <github.com/sadr0b0t>
 * DataIO.java is part of YaShlang.
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
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

public class DataIO {
    public static String exportPlaylistsToMarkdownOrYtdlScript(
            final Context context,
            final boolean exportYtdlScript,
            final boolean exportPlaylistList,
            final boolean exportOnlyEnabledPlaylists1,
            final boolean exportProfiles,
            final boolean exportPlaylistItems,
            final boolean exportOnlyEnabledPlaylists2,
            final boolean exportSkipBlocked,
            final boolean exportPlaylistItemsNames,
            final boolean exportPlaylistItemsUrls,
            final boolean exportStarred,
            final boolean exportBlacklist) {
        final StringBuilder stringBuilder = new StringBuilder();

        if (exportYtdlScript) {
            stringBuilder.append("#!/bin/sh").append("\n");
        }

        if (exportPlaylistList && !exportYtdlScript) {
            stringBuilder.append("# Playlists").append("\n");

            final List<PlaylistInfo> playlists = exportOnlyEnabledPlaylists1 ?
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getEnabled() :
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getAll();

            for (final PlaylistInfo plInfo : playlists) {
                stringBuilder.append("[").append(plInfo.getName()).append("]");
                stringBuilder.append("(").append(plInfo.getUrl()).append(")");
                stringBuilder.append("  \n");
            }
            stringBuilder.append("  \n");
        }

        if (exportProfiles && !exportYtdlScript) {
            stringBuilder.append("# Profiles").append("\n");

            final List<Profile> profiles = VideoDatabase.getDbInstance(context).
                    profileDao().getAll();
            for (final Profile profile : profiles) {
                stringBuilder.append("## ").append(profile.getName()).append("\n");

                final List<PlaylistInfo> playlists = VideoDatabase.getDbInstance(context).
                        profileDao().getProfilePlaylists(profile.getId());

                for (final PlaylistInfo plInfo : playlists) {
                    stringBuilder.append("[").append(plInfo.getName()).append("]");
                    stringBuilder.append("(").append(plInfo.getUrl()).append(")");
                    stringBuilder.append("  \n");
                }
            }

            stringBuilder.append("  \n");
        }

        if (exportPlaylistItems) {
            stringBuilder.append("# Playlists with items").append("\n");

            final List<PlaylistInfo> playlists = exportOnlyEnabledPlaylists2 ?
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getEnabled() :
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getAll();

            for (final PlaylistInfo plInfo : playlists) {
                stringBuilder.append("# ");
                stringBuilder.append("[").append(plInfo.getName()).append("]");
                stringBuilder.append("(").append(plInfo.getUrl()).append(")");
                stringBuilder.append("\n");

                final List<VideoItem> videoItems = exportSkipBlocked ?
                        VideoDatabase.getDbInstance(context).
                                videoItemDao().getByPlaylist(plInfo.getId()) :
                        VideoDatabase.getDbInstance(context).
                                videoItemDao().getByPlaylistAll(plInfo.getId());
                for (final VideoItem videoItem : videoItems) {

                    if (exportYtdlScript) {
                        stringBuilder.append("youtube-dl " + videoItem.getItemUrl());
                        stringBuilder.append("\n");
                    } else if (exportPlaylistItemsNames && exportPlaylistItemsUrls) {
                        stringBuilder.append("[").append(videoItem.getName()).append("]");
                        stringBuilder.append("(").append(videoItem.getItemUrl()).append(")");
                        stringBuilder.append("  \n");
                    } else if (exportPlaylistItemsNames) {
                        stringBuilder.append(videoItem.getName());
                        stringBuilder.append("  \n");
                    } else if (exportPlaylistItemsUrls) {
                        stringBuilder.append(videoItem.getItemUrl());
                        stringBuilder.append("  \n");
                    }
                }
                stringBuilder.append("  \n");
            }
        }

        if (exportStarred) {
            stringBuilder.append("# Starred").append("\n");

            final List<VideoItem> videoItems = VideoDatabase.getDbInstance(context).
                    videoItemDao().getStarred();
            for (final VideoItem videoItem : videoItems) {
                if (exportYtdlScript) {
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

        if (exportBlacklist) {
            stringBuilder.append("# Blacklist").append("\n");

            final List<VideoItem> videoItems = VideoDatabase.getDbInstance(context).
                    videoItemDao().getBlacklist();
            for (final VideoItem videoItem : videoItems) {
                if (exportYtdlScript) {
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


    public static JSONObject exportPlaylistsToJSON(
            final Context context,
            final boolean exportPlaylistList,
            final boolean exportOnlyEnabledPlaylists1,
            final boolean exportProfiles,
            final boolean exportPlaylistItems,
            final boolean exportOnlyEnabledPlaylists2,
            final boolean exportSkipBlocked,
            final boolean exportStarred,
            final boolean exportBlacklist) throws JSONException {
        final JSONObject jsonRoot = new JSONObject();

        if (exportPlaylistList) {
            final JSONArray jsonPlaylists = new JSONArray();

            final List<PlaylistInfo> playlists = exportOnlyEnabledPlaylists1 ?
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getEnabled() :
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getAll();

            for (final PlaylistInfo plInfo : playlists) {
                final JSONObject jsonPlInfo = new JSONObject();

                jsonPlInfo.put("name", plInfo.getName());
                jsonPlInfo.put("url", plInfo.getUrl());
                jsonPlInfo.put("type", plInfo.getType());
                jsonPlInfo.put("thumb_url", plInfo.getThumbUrl());

                jsonPlaylists.put(jsonPlInfo);
            }

            jsonRoot.put("playlists", jsonPlaylists);
        }

        if (exportProfiles) {
            final JSONArray jsonProfiles = new JSONArray();

            final List<Profile> profiles = VideoDatabase.getDbInstance(context).
                    profileDao().getAll();
            for (final Profile profile : profiles) {
                final JSONObject jsonProfile = new JSONObject();

                jsonProfile.put("name", profile.getName());

                final JSONArray jsonPlaylists = new JSONArray();
                final List<PlaylistInfo> playlists = VideoDatabase.getDbInstance(context).
                        profileDao().getProfilePlaylists(profile.getId());

                for (final PlaylistInfo plInfo : playlists) {
                    final JSONObject jsonPlInfo = new JSONObject();

                    jsonPlInfo.put("name", plInfo.getName());
                    jsonPlInfo.put("url", plInfo.getUrl());
                    jsonPlInfo.put("type", plInfo.getType());
                    jsonPlInfo.put("thumb_url", plInfo.getThumbUrl());

                    jsonPlaylists.put(jsonPlInfo);
                }

                jsonProfile.put("playlists", jsonPlaylists);
                jsonProfiles.put(jsonProfile);
            }

            jsonRoot.put("profiles", jsonProfiles);
        }

        if (exportPlaylistItems) {
            final JSONArray jsonPlaylistsWithVideos = new JSONArray();

            final List<PlaylistInfo> playlists = exportOnlyEnabledPlaylists2 ?
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getEnabled() :
                    VideoDatabase.getDbInstance(context).playlistInfoDao().getAll();

            for (final PlaylistInfo plInfo : playlists) {
                final JSONObject jsonPlInfo = new JSONObject();

                jsonPlInfo.put("name", plInfo.getName());
                jsonPlInfo.put("url", plInfo.getUrl());
                jsonPlInfo.put("type", plInfo.getType());
                jsonPlInfo.put("thumb_url", plInfo.getThumbUrl());

                final JSONArray jsonVideoItems = new JSONArray();
                final List<VideoItem> videoItems = exportSkipBlocked ?
                        VideoDatabase.getDbInstance(context).videoItemDao().getByPlaylist(plInfo.getId()) :
                        VideoDatabase.getDbInstance(context).videoItemDao().getByPlaylistAll(plInfo.getId());

                for (final VideoItem videoItem : videoItems) {
                    final JSONObject jsonVideoItem = new JSONObject();

                    jsonVideoItem.put("name", videoItem.getName());
                    jsonVideoItem.put("item_url", videoItem.getItemUrl());
                    jsonVideoItem.put("starred", videoItem.isStarred());
                    jsonVideoItem.put("blocked", videoItem.isBlacklisted());
                    jsonVideoItem.put("last_viewed", videoItem.getLastViewedDate());

                    jsonVideoItems.put(jsonVideoItem);
                }

                jsonPlInfo.put("video_items", jsonVideoItems);
                jsonPlaylistsWithVideos.put(jsonPlInfo);
            }

            jsonRoot.put("videos", jsonPlaylistsWithVideos);
        }

        if (exportStarred) {
            final JSONArray jsonStarred = new JSONArray();

            final List<VideoItem> videoItems =
                    VideoDatabase.getDbInstance(context).videoItemDao().getStarred();
            for (final VideoItem videoItem : videoItems) {
                final JSONObject jsonVideoItem = new JSONObject();

                jsonVideoItem.put("name", videoItem.getName());
                jsonVideoItem.put("item_url", videoItem.getItemUrl());
                jsonVideoItem.put("starred", videoItem.isStarred());
                jsonVideoItem.put("blocked", videoItem.isBlacklisted());
                jsonVideoItem.put("last_viewed", videoItem.getLastViewedDate());

                jsonStarred.put(jsonVideoItem);
            }
            jsonRoot.put("starred", jsonStarred);
        }

        if (exportBlacklist) {
            final JSONArray jsonBlacklist = new JSONArray();

            final List<VideoItem> videoItems =
                    VideoDatabase.getDbInstance(context).videoItemDao().getStarred();
            for (final VideoItem videoItem : videoItems) {
                final JSONObject jsonVideoItem = new JSONObject();

                jsonVideoItem.put("name", videoItem.getName());
                jsonVideoItem.put("item_url", videoItem.getItemUrl());
                jsonVideoItem.put("starred", videoItem.isStarred());
                jsonVideoItem.put("blocked", videoItem.isBlacklisted());
                jsonVideoItem.put("last_viewed", videoItem.getLastViewedDate());

                jsonBlacklist.put(jsonVideoItem);
            }
            jsonRoot.put("blacklist", jsonBlacklist);
        }

        return jsonRoot;
    }


    public static List<PlaylistInfo> loadPlaylistsFromJSON(final String jsonString) throws JSONException {
        final List<PlaylistInfo> playlistList = new ArrayList<>();

        final JSONObject jsonRoot = new JSONObject(jsonString);
        final JSONArray jsonPlaylists = jsonRoot.getJSONArray("playlists");
        for (int i = 0; i < jsonPlaylists.length(); i++) {
            final JSONObject jsonPlaylist = jsonPlaylists.getJSONObject(i);

            final PlaylistInfo plInfo = new PlaylistInfo(
                    jsonPlaylist.getString("name"),
                    jsonPlaylist.getString("url"),
                    jsonPlaylist.getString("thumb_url"),
                    jsonPlaylist.getString("type"));
            playlistList.add(plInfo);
        }

        return playlistList;
    }

    public static File saveToFile(final Context context, final String fileExt, final String contentStr) throws IOException {
        // https://www.zoftino.com/saving-files-to-internal-storage-&-external-storage-in-android
        // https://stackoverflow.com/questions/51565897/saving-files-in-android-for-beginners-internal-external-storage

        // пользователь не сможет жмякать кнопку больше раза в секунду,
        // поэтому идентификатор с секундной точностью можно считать уникальным
        // (а даже если жмякнет, ничего страшного - перезапишет только что сохраненный файл)
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

        final String fileName = "yashlang-video-db-%s.%ext".replace("%ext", fileExt).replace("%s", timestamp);

        final File file = new File(context.getExternalFilesDir(null), fileName);
        final FileWriter fw = new FileWriter(file);

        try {
            file.createNewFile();
            fw.write(contentStr);
            fw.flush();
        } finally {
            fw.close();
        }
        return file;
    }

    /**
     * @param uri
     * @return resource content as String
     */
    public static String loadFromUri(final Context context, final Uri uri) throws IOException {
        final StringBuilder fileContent = new StringBuilder();

        BufferedReader bufferedReader = null;
        try {
            final InputStream in = context.getContentResolver().openInputStream(uri);
            bufferedReader = new BufferedReader(new InputStreamReader(in));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                fileContent.append(line);
            }
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
        return fileContent.toString();

    }
}
