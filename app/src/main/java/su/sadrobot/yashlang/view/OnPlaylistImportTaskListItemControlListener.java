package su.sadrobot.yashlang.view;

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

import android.view.View;

import su.sadrobot.yashlang.controller.PlaylistImportTask;

public interface OnPlaylistImportTaskListItemControlListener {
    void onTaskItemCancelClick(final View view, final int position, final PlaylistImportTask importTask);
    void onTaskItemRetryClick(final View view, final int position, final PlaylistImportTask importTask);
    void onTaskItemDismissClick(final View view, final int position, final PlaylistImportTask importTask);
}
