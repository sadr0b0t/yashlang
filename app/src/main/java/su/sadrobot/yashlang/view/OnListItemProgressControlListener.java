package su.sadrobot.yashlang.view;

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

import android.view.View;

/**
 * Позволяет повесить слушателя на кнопки "старт", "пауза", "удалить", "скачать заново" для элемента списка.
 *
 * @param <ItemType>
 */
public interface OnListItemProgressControlListener<ItemType> {
    void onItemProgressStartClick(final View view, final int position, final ItemType item);
    void onItemProgressPauseClick(final View view, final int position, final ItemType item);
    void onItemRedownloadClick(final View view, final int position, final ItemType item);
    void onItemDeleteClick(final View view, final int position, final ItemType item);
}
