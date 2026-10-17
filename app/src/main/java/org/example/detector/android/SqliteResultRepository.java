package org.example.detector.android;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.IOException;
import java.util.*;
import org.example.detector.core.*;

/** Android 本地记录库，以 UUID 主键防止重复覆盖，payload 使用核心模块统一编码。 */
final class SqliteResultRepository extends SQLiteOpenHelper implements ResultRepository {
  SqliteResultRepository(Context context) {
    super(context, "detection-results.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE results (id TEXT PRIMARY KEY, measured_at INTEGER NOT NULL, payload BLOB NOT"
            + " NULL)");
    db.execSQL("CREATE INDEX results_time ON results(measured_at DESC)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int from, int to) {
    throw new SQLiteException("没有可用的数据迁移路径");
  }

  @Override
  public void save(DetectionResult result) throws IOException {
    ContentValues values = new ContentValues();
    values.put("id", result.id);
    values.put("measured_at", result.epochMillis);
    values.put("payload", ResultCodec.encode(result));
    try {
      getWritableDatabase().insertOrThrow("results", null, values);
    } catch (SQLiteException ex) {
      throw new IOException("无法保存记录", ex);
    }
  }

  @Override
  public List<DetectionResult> all() throws IOException {
    List<DetectionResult> rows = new ArrayList<>();
    try (Cursor cursor =
        getReadableDatabase()
            .query(
                "results", new String[] {"payload"}, null, null, null, null, "measured_at DESC")) {
      while (cursor.moveToNext()) rows.add(ResultCodec.decode(cursor.getBlob(0)));
    } catch (SQLiteException ex) {
      throw new IOException("无法读取记录", ex);
    }
    return rows;
  }
}
