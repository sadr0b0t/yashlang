package su.sadrobot.yashlang.model;

/*
 * Copyright (C) Anton Moiseev 2026 <github.com/sadr0b0t>
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

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "profile_nfc_tags",
        foreignKeys = {
                @ForeignKey(entity = Profile.class,
                        parentColumns = "_id",
                        childColumns = "profile_id",
                        onDelete = ForeignKey.CASCADE)},
        indices = {
                @Index(value = "profile_id"),
                @Index(value = "nfc_tag_id", unique = true)
        })
public class ProfileNfcTags {

    @PrimaryKey(autoGenerate = true)
    private long _id;

    @ColumnInfo(name = "profile_id")
    private long profileId;

    @ColumnInfo(name = "nfc_tag_id")
    private String nfcTagId;

    @ColumnInfo(name = "nfc_tag_label")
    private String nfcTagLabel;

    public ProfileNfcTags(final long profileId, final String nfcTagId, final String nfcTagLabel) {
        this.profileId = profileId;
        this.nfcTagId = nfcTagId;
        this.nfcTagLabel = nfcTagLabel;
    }

    public long getId() {
        return _id;
    }

    public void setId(long _id) {
        this._id = _id;
    }

    public long getProfileId() {
        return profileId;
    }

    public void setProfileId(long profileId) {
        this.profileId = profileId;
    }

    public String getNfcTagId() {
        return nfcTagId;
    }

    public void setNfcTagId(String nfcTagId) {
        this.nfcTagId = nfcTagId;
    }

    public String getNfcTagLabel() {
        return nfcTagLabel;
    }

    public void setNfcTagLabel(String nfcTagLabel) {
        this.nfcTagLabel = nfcTagLabel;
    }
}
