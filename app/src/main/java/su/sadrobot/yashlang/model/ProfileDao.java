package su.sadrobot.yashlang.model;

/*
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

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;
import java.util.Set;


@Dao
public abstract class ProfileDao {

    @Insert
    public abstract long insert(final Profile profile);

    @Update
    public abstract void update(final Profile profile);

    @Delete
    public abstract void delete(final Profile profile);

    @Query("SELECT * FROM profile WHERE _id = :id LIMIT 1")
    public abstract Profile getById(final long id);

    @Query("SELECT * FROM profile")
    public abstract List<Profile> getAll();

    @Query("SELECT * FROM profile")
    public abstract DataSource.Factory<Integer, Profile> getAllDs();

    @Query("SELECT * FROM profile_nfc_tags")
    public abstract List<ProfileNfcTags> getAllNfcTags();

    @Query("SELECT playlist_id FROM profile_playlists WHERE profile_id = :profileId")
    public abstract List<Long> getProfilePlaylistsIds(final long profileId);

    @Query("SELECT * FROM playlist_info WHERE playlist_info._id IN (SELECT playlist_id FROM profile_playlists WHERE profile_id = :profileId)")
    public abstract List<PlaylistInfo> getProfilePlaylists(final long profileId);

    @Query("SELECT profile_id FROM profile_playlists WHERE playlist_id = :playlistId")
    public abstract List<Long> getProfileIdsForPlaylist(final long playlistId);

    @Query("INSERT INTO profile_playlists (profile_id, playlist_id) VALUES (:profileId, :playlistId)")
    public abstract void addPlaylistToProfile(final long profileId, final long playlistId);

    @Query("DELETE FROM profile_playlists WHERE profile_id = :profileId AND playlist_id =:playlistId")
    public abstract void removePlaylistFromProfile(final long profileId, final long playlistId);

    @Query("SELECT profile_id FROM profile_nfc_tags WHERE nfc_tag_id = :nfcTagId")
    public abstract long getByNfcTagId(final String nfcTagId);

    @Query("SELECT * FROM profile_nfc_tags WHERE profile_id = :profileId")
    public abstract List<ProfileNfcTags> getProfileNfcTags(final long profileId);

    @Insert
    public abstract List<Long> insertNfcTags(final List<ProfileNfcTags> nfcTags);

    @Query("DELETE FROM profile_playlists WHERE profile_id = :profileId")
    public abstract void clearProfile(final long profileId);

    @Query("DELETE FROM profile_nfc_tags WHERE profile_id = :profileId")
    public abstract void clearProfileNfcTags(final long profileId);

    @Query("DELETE FROM profile_nfc_tags WHERE nfc_tag_id = :nfcTagId")
    public abstract void removeNfcTagById(final String nfcTagId);

    @Transaction
    public void setPlaylists(final long profileId, final Set<Long> playlistIds) {
        clearProfile(profileId);
        for (final long plId : playlistIds) {
            addPlaylistToProfile(profileId, plId);
        }
    }

    @Transaction
    public void setNfcTags(final long profileId, final List<ProfileNfcTags> nfcTags) {
        clearProfileNfcTags(profileId);
        // нужно специально дополнительно удалить все записи, связанные с каждой из меток,
        // т.к. они могли быть привязаны к другому профилю
        for (final ProfileNfcTags nfcTag : nfcTags) {
            removeNfcTagById(nfcTag.getNfcTagId());
        }
        // по-хорошему, все id профилей в списке nfcTags, должны быть равны profileId,
        // но проверять мы это здесь не будем
        insertNfcTags(nfcTags);
    }

    @Transaction
    public long insert(final Profile profile, final Set<Long> playlistIds) {
        final long profileId = insert(profile);
        for (final long plId : playlistIds) {
            addPlaylistToProfile(profileId, plId);
        }
        return profileId;
    }

    @Transaction
    public void update(final Profile profile, final Set<Long> playlistIds) {
        update(profile);
        setPlaylists(profile.getId(), playlistIds);
    }
}
