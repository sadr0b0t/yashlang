package su.sadrobot.yashlang.model;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoItem.java is part of YaShlang.
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

import android.graphics.Bitmap;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.Date;

import su.sadrobot.yashlang.util.TimestampConverter;

/**
 * Поля по большей части из StremInfoItem+InfoItem (то, что Newpipe загружает для
 * элементов списка), расширенную информацию (например, описание description) пока
 * не сохраняем - во-первых, из-за экономии места, во-вторых, т.к. Newpipe их
 * сразу не тянет, в-третьих, пока незачем.
 */
@Entity(tableName = "video_item",
        foreignKeys = @ForeignKey(entity = PlaylistInfo.class,
        parentColumns = "_id",
        childColumns = "playlist_id",
        onDelete = ForeignKey.CASCADE),
        indices=@Index(value="playlist_id"))
public class VideoItem {

    public static int FAKE_TIMESTAMP_BLOCK_SIZE = 10000;

    // Id должен быть только long, иначе из метода @Insert не получится получить id вновь созданной записи
    // https://developer.android.com/training/data-storage/room/accessing-data#convenience-insert
    // If the @Insert method receives only 1 parameter, it can return a long, which is the new rowId
    // for the inserted item. If the parameter is an array or a collection,
    // it should return long[] or List<Long> instead.

    @PrimaryKey(autoGenerate = true)
    private long _id;

    @ColumnInfo(name = "playlist_id")
    private long playlistId;

    @ColumnInfo(name = "yt_id")
    private String ytId;

    @ColumnInfo(name = "name")
    private String name;

    //@ColumnInfo(name = "descr")
    //private String descr;

    @ColumnInfo(name = "uploader")
    private String uploader;


    // При загрузке плейлиста содержит бесполезную поебень
    // вроде "1 год назад", "3 месяца назад и т.п".
    // Если грузить со страницы видео, получается чуть лучше:
    // приходит полная дата вида "12 авг. 2008 г.", но ее все равно крайне проблематично парсить
    // с учетом сокращений и локализаций.
    //@ColumnInfo(name = "upload_date")
    //private String uploadDate;

    /**
     * Количество просмотров локально
     */
    @ColumnInfo(name = "view_count")
    private long viewCount;

    /**
     * Количество просмотров на внешнем сервисе (Youtube)
     */
    @ColumnInfo(name = "view_count_ext")
    private long viewCountExt;

    @ColumnInfo(name = "duration")
    private long duration;

    @ColumnInfo(name = "thumb_url")
    private String thumbUrl;

    @ColumnInfo(name = "enabled")
    private boolean enabled;

    @ColumnInfo(name = "blacklisted")
    private boolean blacklisted;

    @ColumnInfo(name = "starred")
    private boolean starred;

    // для сортировки
    @ColumnInfo(name = "starred_date")
    @TypeConverters({TimestampConverter.class})
    private Date starredDate;

    /**
     * Время последнего просмотра.
     */
    // Варианты храниния времени в SQLite:
    // - long (миллисекунды от начала времен)
    // - строка в формате "yyyy-MM-dd HH:mm:ss"
    // вариант с миллисекундами кажется проще и надежнее, но
    // для работы со строкой есть всроенные в SQL функции
    // datetime, strftime и т.п., строка в таком виде автоматически сортируется
    // лексикографическим порядком (т.е. сортировка по авфавиту дает автоматическую
    // сортировку по дате), плюс строку можно сразу читать, а не переводить в дату.
    // Короче, останавливаемся на варианте со строкой, плюс неплохо интегрируется с ROOM.
    // https://androidkt.com/datetime-datatype-sqlite-using-room/
    // https://github.com/Thumar/RoomPersistenceLibrary
    @ColumnInfo(name = "last_viewed_date")
    @TypeConverters({TimestampConverter.class})
    private Date lastViewedDate;

    @ColumnInfo(name = "paused_at")
    private long pausedAt;

    // Короче, здесь история такая...
    // 1) Ютюб возвращает плейлист постранично, на первой странице самые новые ролики
    // 2) Таким образом, при первой загрузке плейлиста мы добавлям ролики в базу по порядку
    // от самых новых к самым старым
    // 3) Сортировка списка сразу после добавления (по id по возрастанию) совпадает с тем, как плейлист
    // представлен на сайте: у самых старых видео будет самый большой id и они окажутся внизу,
    // а у самого нового видео будет id=0 и оно окажется наверху - это хорошо
    // 4) Но если мы хотим добавить в плейлист новые видео, которые появлись позднее
    // первоначальной закачки, прозойдет небольшая неприятность: мы сможем найти и добавить новые видео,
    // но их id с автоматическим счетчиком начнут расти от последнего добавленного в базу элемента,
    // т.е. от наиболее старых видео в плейлисте и выше
    // 5) Это значит, что при сортировке по умолчанию по id новые видео окажутся в самом низу списка
    // 6) Это не большая проблема (выдача рекомендаций все равно происходит случайно, при поиске
    // сортируется по алфавиту), но при просмотре плейлиста в режиме настроек все равно не очень
    // хорошо, когда только что добавленные видео улетают в неизвестном направлении и вообще
    // плейлист перестает совпадать с тем, что показано на сайте Ютюба
    // 7) Для решения этого вопроса можно рассмотреть разные решения:
    // 7.1) Использовать ютюбовский таймстапм видео: не представляется возможным (см комент к исключенному
    // из базы полю uploadData), тем более для сортировки внутри дня нужны еще минуты или секунды
    // 7.2) Сначала загружать все видео плейлиста в большой массив, потом добавлять в обратном порядке:
    // вариант рабочий, но архитектурно не очень красивый (памяти на весь плейлист в пару-тройку
    // тысяч элементов максимум хватит, но постраничная загрузка и добавление в базу просто выглядит
    // хорошо и ради такой сортировки от нее не хочется отказываться)
    // 7.3) Выбранное решение: добавить поле fake_timestamp, которое будет содержать метки роликов
    // для сортировки в нужном порядке внутри каждого плейлиста
    // 8) Смысл такой: при добавлении первого блока берем значение fake_timestamp, например,
    // 10000 для первого элемента на первой странице  и уменьшаем его для всех последующих
    // 8.1) Таким образом у самого первого (самого нового) элемента значение fake_timestamp
    // будет больше, чем у всех остальных; чем старее элемент, т.е. чем ниже он в списке, тем
    // меньше будет значение fake_timestamp. Самое маленькое значение будет у самого
    // старого элемента. Значит по fake_timestamp можно будет сортировать элементы, как по дате.
    // Значение 10000 для первого блока - достаточно большое (у канала CNN база роликов всего
    // 3000 тысячи, у RT столько же). Но даже если найдется канал с большим количеством роликов,
    // значение fake_timestamp просто уйдет в минус и порядок сортировки все равно сохранится
    // 8.2) Дальше, когда мы хотим добавить новый блок, берем максимальное значение fake_timestamp
    // и увеличиваем его на те же 10000, далее для всех элементов из нового списка уменьшаем
    // значение на 1 сверху вниз. Таким образом, значение fake_timestamp будет уменьшаться от
    // самых новых элементов к самым старым для всего списка.
    // 8.3) Таким образом, отсортированные значения fake_timestamp для списка будут примерно такие:
    // 2038...10000 19816...20000 29983...30000 и т.п.
    // (максимальное значение long достаточно большое, чтобы считать операции добавления
    // с шагом по 10тыс, не заботясь о переполнении)
    // 8.4) По размеру блока: крайне мало вероятно, что со времени добавления всего списка
    // на канал будет добавлено более 10 тыс новых видео. Даже если это произойдет, значения
    // fake_timestamp просто пересекутся где-то в середине списка и в этой области, вероятно, немного
    // нарушится сортировка, что в принципе все равно не критично и пользователь скорее всего
    // никогда его не увидит.
    // 8.5) По большому счету, весь этого огород мы городим только для того, чтобы у локальной
    // копии плейлиста и у оригинала на ютюбе всегда одинаково выглядела макушка списка.

    @ColumnInfo(name = "fake_timestamp")
    private long fakeTimestamp;


    // возможно, не очень красиво хранить здесь поля, не сохраняемые в базу,
    // но более удобного способа отправлять объекты в адаптер списка прямиком из
    // базы данных и при этом приклеплять к ним картинки, загружаемые по требованию
    // из интернета, найти не представляется возможным
    @Ignore
    private Bitmap thumbBitmap;

    // кэш - объект плейлист, для тех случаев, когда не хочется постоянно обращаться
    // в базу в фоновом потоке
    @Ignore
    private PlaylistInfo playlistInfo;

    // кэш - адрес потока видео, загруженный для этого ролика в последний раз
    @Ignore
    private String videoStreamUrl;

    public VideoItem(long playlistId, String ytId, String name, String uploader,
                     long viewCount, long viewCountExt, long duration, String thumbUrl,
                     boolean enabled, boolean blacklisted) {
        this.playlistId = playlistId;
        this.ytId = ytId;
        this.name = name;
        this.uploader = uploader;
        this.viewCount = viewCount;
        this.viewCountExt = viewCountExt;
        this.duration = duration;
        this.thumbUrl = thumbUrl;
        this.enabled = enabled;
        this.blacklisted = blacklisted;
    }


    @Ignore
    public VideoItem(long playlistId, String ytId, String name, String uploader,
                     long viewCount, long viewCountExt, long duration, String thumbUrl) {
        this.playlistId = playlistId;
        this.ytId = ytId;
        this.name = name;
        this.uploader = uploader;
        this.viewCount = viewCount;
        this.viewCountExt = viewCountExt;
        this.duration = duration;
        this.thumbUrl = thumbUrl;
        this.enabled = true;
        this.blacklisted = false;
        this.fakeTimestamp = 0;
    }

    @Ignore
    public VideoItem(long playlistId, String ytId, String name, String uploader,
                     long viewCount, long viewCountExt, long duration, String thumbUrl,
                     boolean enabled, long fakeTimestamp) {
        this.playlistId = playlistId;
        this.ytId = ytId;
        this.name = name;
        this.uploader = uploader;
        this.viewCount = viewCount;
        this.viewCountExt = viewCountExt;
        this.duration = duration;
        this.thumbUrl = thumbUrl;
        this.enabled = enabled;
        this.blacklisted = false;
        this.fakeTimestamp = fakeTimestamp;
    }

    public long getId() {
        return _id;
    }

    public void setId(long _id) {
        this._id = _id;
    }

    public long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(long playlistId) {
        this.playlistId = playlistId;
    }

    public String getYtId() {
        return ytId;
    }

    public void setYtId(String ytId) {
        this.ytId = ytId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getViewCountExt() {
        return viewCountExt;
    }

    public void setViewCountExt(long viewCountExt) {
        this.viewCountExt = viewCountExt;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(boolean blacklisted) {
        this.blacklisted = blacklisted;
    }

    public boolean isStarred() {
        return starred;
    }

    public void setStarred(boolean starred) {
        this.starred = starred;
    }

    public Date getStarredDate() {
        return starredDate;
    }

    public void setStarredDate(Date starredDate) {
        this.starredDate = starredDate;
    }

    public Date getLastViewedDate() {
        return lastViewedDate;
    }

    public void setLastViewedDate(Date lastViewedDate) {
        this.lastViewedDate = lastViewedDate;
    }

    public long getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(long pausedAt) {
        this.pausedAt = pausedAt;
    }

    public long getFakeTimestamp() {
        return fakeTimestamp;
    }

    public void setFakeTimestamp(long fakeTimestamp) {
        this.fakeTimestamp = fakeTimestamp;
    }

    public Bitmap getThumbBitmap() {
        return thumbBitmap;
    }

    public void setThumbBitmap(Bitmap thumbBitmap) {
        this.thumbBitmap = thumbBitmap;
    }

    public PlaylistInfo getPlaylistInfo() {
        return playlistInfo;
    }

    public void setPlaylistInfo(PlaylistInfo playlistInfo) {
        this.playlistInfo = playlistInfo;
    }

    public String getVideoStreamUrl() {
        return videoStreamUrl;
    }

    public void setVideoStreamUrl(String videoStreamUrl) {
        this.videoStreamUrl = videoStreamUrl;
    }
}
