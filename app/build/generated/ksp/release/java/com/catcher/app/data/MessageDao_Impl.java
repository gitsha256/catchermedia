package com.catcher.app.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MessageDao_Impl implements MessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MessageLog> __insertionAdapterOfMessageLog;

  private final EntityDeletionOrUpdateAdapter<MessageLog> __deletionAdapterOfMessageLog;

  private final SharedSQLiteStatement __preparedStmtOfMarkMessageAsDeleted;

  private final SharedSQLiteStatement __preparedStmtOfDeleteThread;

  private final SharedSQLiteStatement __preparedStmtOfNukeTable;

  public MessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMessageLog = new EntityInsertionAdapter<MessageLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `message_logs` (`id`,`packageName`,`senderName`,`messageText`,`timestamp`,`isDeleted`,`mediaPath`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageLog entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getPackageName());
        statement.bindString(3, entity.getSenderName());
        statement.bindString(4, entity.getMessageText());
        statement.bindLong(5, entity.getTimestamp());
        final int _tmp = entity.isDeleted() ? 1 : 0;
        statement.bindLong(6, _tmp);
        if (entity.getMediaPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getMediaPath());
        }
      }
    };
    this.__deletionAdapterOfMessageLog = new EntityDeletionOrUpdateAdapter<MessageLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `message_logs` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MessageLog entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfMarkMessageAsDeleted = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE message_logs SET isDeleted = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteThread = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM message_logs WHERE packageName = ? AND senderName = ?";
        return _query;
      }
    };
    this.__preparedStmtOfNukeTable = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM message_logs";
        return _query;
      }
    };
  }

  @Override
  public Object insertMessage(final MessageLog message,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMessageLog.insert(message);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMessages(final List<MessageLog> messages,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfMessageLog.handleMultiple(messages);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markMessageAsDeleted(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkMessageAsDeleted.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkMessageAsDeleted.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteThread(final String packageName, final String senderName,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteThread.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, packageName);
        _argIndex = 2;
        _stmt.bindString(_argIndex, senderName);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteThread.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object nukeTable(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfNukeTable.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfNukeTable.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object findLastMessageForDeletion(final String packageName, final String senderName,
      final long timeLimit, final Continuation<? super MessageLog> $completion) {
    final String _sql = "SELECT * FROM message_logs WHERE packageName = ? AND senderName = ? AND timestamp >= ? AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packageName);
    _argIndex = 2;
    _statement.bindString(_argIndex, senderName);
    _argIndex = 3;
    _statement.bindLong(_argIndex, timeLimit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageLog>() {
      @Override
      @Nullable
      public MessageLog call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfSenderName = CursorUtil.getColumnIndexOrThrow(_cursor, "senderName");
          final int _cursorIndexOfMessageText = CursorUtil.getColumnIndexOrThrow(_cursor, "messageText");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final MessageLog _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpSenderName;
            _tmpSenderName = _cursor.getString(_cursorIndexOfSenderName);
            final String _tmpMessageText;
            _tmpMessageText = _cursor.getString(_cursorIndexOfMessageText);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            _result = new MessageLog(_tmpId,_tmpPackageName,_tmpSenderName,_tmpMessageText,_tmpTimestamp,_tmpIsDeleted,_tmpMediaPath);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findLatestMessageByPackage(final String packageName,
      final Continuation<? super MessageLog> $completion) {
    final String _sql = "SELECT * FROM message_logs WHERE packageName = ? ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packageName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageLog>() {
      @Override
      @Nullable
      public MessageLog call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfSenderName = CursorUtil.getColumnIndexOrThrow(_cursor, "senderName");
          final int _cursorIndexOfMessageText = CursorUtil.getColumnIndexOrThrow(_cursor, "messageText");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final MessageLog _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpSenderName;
            _tmpSenderName = _cursor.getString(_cursorIndexOfSenderName);
            final String _tmpMessageText;
            _tmpMessageText = _cursor.getString(_cursorIndexOfMessageText);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            _result = new MessageLog(_tmpId,_tmpPackageName,_tmpSenderName,_tmpMessageText,_tmpTimestamp,_tmpIsDeleted,_tmpMediaPath);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findLatestMessage(final String packageName, final String senderName,
      final Continuation<? super MessageLog> $completion) {
    final String _sql = "SELECT * FROM message_logs WHERE packageName = ? AND senderName = ? ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packageName);
    _argIndex = 2;
    _statement.bindString(_argIndex, senderName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MessageLog>() {
      @Override
      @Nullable
      public MessageLog call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfSenderName = CursorUtil.getColumnIndexOrThrow(_cursor, "senderName");
          final int _cursorIndexOfMessageText = CursorUtil.getColumnIndexOrThrow(_cursor, "messageText");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final MessageLog _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpSenderName;
            _tmpSenderName = _cursor.getString(_cursorIndexOfSenderName);
            final String _tmpMessageText;
            _tmpMessageText = _cursor.getString(_cursorIndexOfMessageText);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            _result = new MessageLog(_tmpId,_tmpPackageName,_tmpSenderName,_tmpMessageText,_tmpTimestamp,_tmpIsDeleted,_tmpMediaPath);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<MessageLog>> getAllThreads() {
    final String _sql = "SELECT * FROM message_logs WHERE id IN (SELECT MAX(id) FROM message_logs GROUP BY packageName, senderName) ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"message_logs"}, new Callable<List<MessageLog>>() {
      @Override
      @NonNull
      public List<MessageLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfSenderName = CursorUtil.getColumnIndexOrThrow(_cursor, "senderName");
          final int _cursorIndexOfMessageText = CursorUtil.getColumnIndexOrThrow(_cursor, "messageText");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final List<MessageLog> _result = new ArrayList<MessageLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageLog _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpSenderName;
            _tmpSenderName = _cursor.getString(_cursorIndexOfSenderName);
            final String _tmpMessageText;
            _tmpMessageText = _cursor.getString(_cursorIndexOfMessageText);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            _item = new MessageLog(_tmpId,_tmpPackageName,_tmpSenderName,_tmpMessageText,_tmpTimestamp,_tmpIsDeleted,_tmpMediaPath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<MessageLog>> getMessagesForThread(final String packageName,
      final String senderName) {
    final String _sql = "SELECT * FROM message_logs WHERE packageName = ? AND senderName = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packageName);
    _argIndex = 2;
    _statement.bindString(_argIndex, senderName);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"message_logs"}, new Callable<List<MessageLog>>() {
      @Override
      @NonNull
      public List<MessageLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfSenderName = CursorUtil.getColumnIndexOrThrow(_cursor, "senderName");
          final int _cursorIndexOfMessageText = CursorUtil.getColumnIndexOrThrow(_cursor, "messageText");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfIsDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isDeleted");
          final int _cursorIndexOfMediaPath = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaPath");
          final List<MessageLog> _result = new ArrayList<MessageLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MessageLog _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpSenderName;
            _tmpSenderName = _cursor.getString(_cursorIndexOfSenderName);
            final String _tmpMessageText;
            _tmpMessageText = _cursor.getString(_cursorIndexOfMessageText);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpIsDeleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDeleted);
            _tmpIsDeleted = _tmp != 0;
            final String _tmpMediaPath;
            if (_cursor.isNull(_cursorIndexOfMediaPath)) {
              _tmpMediaPath = null;
            } else {
              _tmpMediaPath = _cursor.getString(_cursorIndexOfMediaPath);
            }
            _item = new MessageLog(_tmpId,_tmpPackageName,_tmpSenderName,_tmpMessageText,_tmpTimestamp,_tmpIsDeleted,_tmpMediaPath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
