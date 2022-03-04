package org.sqlite.core;

import jdk.incubator.foreign.*;
import jpassport.PassportFactory;
import jpassport.Utils;
import org.sqlite.*;
import org.sqlite.core.panama.*;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class PanamaDBImpl extends DB {

    private final List<ProgressFunction> progressList = new ArrayList<>();
    private final PanamaCallbacks callbackCreator = new PanamaCallbacks();
    private final Map<String, PanamaFunction> callbackFunctions = new HashMap<>();
    private PanamaBusyHandler m_currentHandler = null;

    private static boolean isLoaded;
    private static boolean loadSucceeded;

    private static final int SQLITE_UTF8 = 1;
    private static final int SQLITE_UTF16LE = 2;
    private static final int SQLITE_UTF16BE = 3;
    private static final int SQLITE_UTF16   = 4;    /* Use native byte order */
    private static final int SQLITE_ANY     = 5;    /* sqlite3_create_function only */
    private static final int SQLITE_UTF16_ALIGNED  = 8;    /* sqlite3_create_collation only */

    private static final int SQLITE_TRANSIENT = -1;

    private static final int SQLITE_OPEN_READONLY = 0x00000001;
    private static final int SQLITE_OPEN_READWRITE = 0x00000002;
    private static final int SQLITE_OPEN_CREATE =    0x00000004;
    private static final int SQLITE_OPEN_URI=        0x00000040;

    static {
        if ("The Android Project".equals(System.getProperty("java.vm.vendor"))) {
//            System.loadLibrary("sqlitejdbc");
            isLoaded = true;
            loadSucceeded = true;
        } else {
            // continue with non Android execution path
            isLoaded = false;
            loadSucceeded = false;
        }
    }

    public PanamaDBImpl(String url, String fileName, SQLiteConfig config) throws SQLException {
        super(url, fileName, config);
    }

    static PanamaDB m_panama = null;
    DBHandle m_dbHandle = new DBHandle(0);

    /**
     * Loads the SQLite interface backend.
     *
     * @return True if the SQLite JDBC driver is successfully loaded; false otherwise.
     */
    public static boolean load() throws Exception {
        if (isLoaded) return loadSucceeded == true;

        try {
//            System.setProperty("jpassport.build.home", "C:\\code\\github\\sqlite-jdbc\\src\\main\\write");
            var p = SQLiteJDBCLoader.extractedLibraryName;
            m_panama = PassportFactory.link(p.toString(), PanamaDB.class);
            loadSucceeded = true;
        } catch (Throwable th) {
            th.printStackTrace();
            loadSucceeded = false;
        }

        isLoaded = true;
        return loadSucceeded;
    }

    // WRAPPER FUNCTIONS ////////////////////////////////////////////

    /**
     * @see org.sqlite.core.DB#_open(java.lang.String, int)
     */
    @Override
    protected synchronized void _open(String file, int openFlags) throws SQLException {
        _open_utf8(file, openFlags);
    }

    synchronized void _open_utf8(String file, int openFlags) throws SQLException {
        if (m_dbHandle.isValid()) {
            m_panama.sqlite3_close(m_dbHandle.handle());
            throwex("DB already open");
        }

        var dbHandle = DBHandle.createOpenHandle();
        int ret = m_panama.sqlite3_open_v2(file, dbHandle, openFlags, null);
        m_dbHandle = dbHandle[0];
        ResultCode.checkReturnCode(ret);
    }

    /**
     * @see org.sqlite.core.DB#_close()
     */
    @Override
    protected synchronized void _close() throws SQLException {
        if (m_dbHandle.isValid()) {
            int ret = m_panama.sqlite3_close(m_dbHandle.handle());
            ResultCode.checkReturnCode(ret);
            m_dbHandle = new DBHandle(0);
        }
    }

    /**
     * @see org.sqlite.core.DB#_exec(java.lang.String)
     */
    @Override
    public synchronized int _exec(String sql) throws SQLException {
        return _exec_utf8(sql);
    }

    synchronized int _exec_utf8(String sqlUtf8) throws SQLException {
        m_dbHandle.checkValid();
        int status = m_panama.sqlite3_exec(m_dbHandle.handle(), sqlUtf8, 0, 0, null);
        ResultCode.checkReturnCode(status);
        return status;
    }

    /**
     * @see org.sqlite.core.DB#shared_cache(boolean)
     */
    @Override
    public synchronized int shared_cache(boolean enable) {
        return m_panama.sqlite3_enable_shared_cache(enable ? 1 : 0);
    }

    /**
     * @see org.sqlite.core.DB#enable_load_extension(boolean)
     */
    @Override
    public synchronized int enable_load_extension(boolean enable) throws SQLException {
        m_dbHandle.checkValid();
        return m_panama.sqlite3_enable_load_extension(m_dbHandle.handle(), enable ? 1 : 0);
    }

    /**
     * @see org.sqlite.core.DB#interrupt()
     */
    @Override
    public void interrupt() throws SQLException {
        m_dbHandle.checkValid();
        m_panama.sqlite3_interrupt(m_dbHandle.handle());
    }

    /**
     * @see org.sqlite.core.DB#busy_timeout(int)
     */
    @Override
    public synchronized void busy_timeout(int ms) throws SQLException {
        m_dbHandle.checkValid();
        m_panama.sqlite3_busy_timeout(m_dbHandle.handle(), ms);
    }


    /**
     * @see org.sqlite.core.DB#busy_handler(BusyHandler)
     */
    @Override
    public synchronized void busy_handler(BusyHandler busyHandler)
    {
        if (busyHandler == null) {
            m_panama.sqlite3_busy_handler(m_dbHandle.handle(), MemoryAddress.NULL, MemoryAddress.NULL);
            m_currentHandler = null;
        } else {
            if (m_currentHandler != null)
                throw new IllegalArgumentException("A busy handler already exists");

            m_currentHandler = new PanamaBusyHandler(busyHandler);
            var addr = callbackCreator.createCallback(m_currentHandler, "callback");
            m_panama.sqlite3_busy_handler(m_dbHandle.handle(), addr, MemoryAddress.NULL);
        }
    }

    /**
     * @see org.sqlite.core.DB#prepare(java.lang.String)
     */
    @Override
    protected synchronized long prepare(String sql) throws SQLException {
        return prepare_utf8(sql);
    }

    synchronized long prepare_utf8(String sqlUtf8) throws SQLException {
        m_dbHandle.checkValid();
        var stmtPtr = PrepStmt.createPrepareHandle();
        int byteCount = sqlUtf8.getBytes(StandardCharsets.UTF_8).length;
        int status = m_panama.sqlite3_prepare_v2(m_dbHandle.handle(), sqlUtf8, byteCount, stmtPtr, 0);
        ResultCode.checkReturnCode(status);
        return stmtPtr[0].handle();
    }

    /**
     * @see org.sqlite.core.DB#errmsg()
     */
    @Override
    synchronized String errmsg() {
        return errmsg_utf8();
    }

    synchronized String errmsg_utf8() {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_errmsg(m_dbHandle.handle());
        return "No connection!";
    }

    /**
     * @see org.sqlite.core.DB#libversion()
     */
    @Override
    public synchronized String libversion() {
        return libversion_utf8();
    }

    String libversion_utf8() {
        return m_panama.sqlite3_libversion();
    }

    /**
     * @see org.sqlite.core.DB#changes()
     */
    @Override
    public synchronized int changes() {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_changes(m_dbHandle.handle());
        return ResultCode.SQLITE_MISUSE.code();
    }

    /**
     * @see org.sqlite.core.DB#total_changes()
     */
    @Override
    public synchronized int total_changes() {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_total_changes(m_dbHandle.handle());
        return ResultCode.SQLITE_MISUSE.code();
    }

    /**
     * @see org.sqlite.core.DB#finalize(long)
     */
    @Override
    protected synchronized int finalize(long stmt) {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_finalize(stmt);
        return ResultCode.SQLITE_MISUSE.code();
    }

    /**
     * @see org.sqlite.core.DB#step(long)
     */
    @Override
    public synchronized int step(long stmt) {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_step(stmt);
        return ResultCode.SQLITE_MISUSE.code();
    }

    /**
     * @see org.sqlite.core.DB#reset(long)
     */
    @Override
    public synchronized int reset(long stmt) {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_reset(stmt);
        return ResultCode.SQLITE_MISUSE.code();
    }

    /**
     * @see org.sqlite.core.DB#clear_bindings(long)
     */
    @Override
    public synchronized int clear_bindings(long stmt) {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_clear_bindings(stmt);
        return ResultCode.SQLITE_MISUSE.code();
    }

    /**
     * @see org.sqlite.core.DB#bind_parameter_count(long)
     */
    @Override
    synchronized int bind_parameter_count(long stmt) {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_bind_parameter_count(stmt);
        return ResultCode.SQLITE_MISUSE.code();
    }

    /**
     * @see org.sqlite.core.DB#column_count(long)
     */
    @Override
    public synchronized int column_count(long stmt) {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_column_count(stmt);
        return ResultCode.SQLITE_MISUSE.code();

    }

    /**
     * @see org.sqlite.core.DB#column_type(long, int)
     */
    @Override
    public synchronized int column_type(long stmt, int col)
    {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_column_type(stmt, col);
        return ResultCode.SQLITE_MISUSE.code();
    }

    /** @see org.sqlite.core.DB#column_decltype(long, int) */
    @Override
    public synchronized String column_decltype(long stmt, int col) {
        return column_decltype_utf8(stmt, col);
    }

    synchronized String column_decltype_utf8(long stmt, int col)
    {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_column_decltype(stmt, col);
        return null;
    }

    /** @see org.sqlite.core.DB#column_table_name(long, int) */
    @Override
    public synchronized String column_table_name(long stmt, int col) {
        return column_table_name_utf8(stmt, col);
    }

    synchronized String column_table_name_utf8(long stmt, int col)
    {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_column_table_name(stmt, col);
        return null;
    }

    /** @see org.sqlite.core.DB#column_name(long, int) */
    @Override
    public synchronized String column_name(long stmt, int col) {
        return column_name_utf8(stmt, col);
    }

    synchronized String column_name_utf8(long stmt, int col)
    {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_column_name(stmt, col);
        return null;
    }

    /** @see org.sqlite.core.DB#column_text(long, int) */
    @Override
    public synchronized String column_text(long stmt, int col) {
        return column_text_utf8(stmt, col);
    }

    synchronized String column_text_utf8(long stmt, int col)
    {
        if (m_dbHandle.isValid())
            return m_panama.sqlite3_column_text(stmt, col);
        return null;
    }

    /** @see org.sqlite.core.DB#column_blob(long, int) */
    @Override
    public synchronized byte[] column_blob(long stmt, int col)
    {
        if (m_dbHandle.isValid())
        {
            var type = m_panama.sqlite3_column_type(stmt, col);
            var addr =  m_panama.sqlite3_column_blob(stmt, col);
            if (addr == MemoryAddress.NULL)
                return type == SQLITE_NULL ? null : new byte[0];

            int length = m_panama.sqlite3_column_bytes(stmt, col);
            return Utils.toArrByte(addr, length);
        }
        return null;
    }

    /** @see org.sqlite.core.DB#column_double(long, int) */
    @Override
    public synchronized double column_double(long stmt, int col)
    {
        return m_panama.sqlite3_column_double(stmt, col);
    }

    /** @see org.sqlite.core.DB#column_long(long, int) */
    @Override
    public synchronized long column_long(long stmt, int col)
    {
        return m_panama.sqlite3_column_int64(stmt, col);
    }

    /** @see org.sqlite.core.DB#column_int(long, int) */
    @Override
    public synchronized int column_int(long stmt, int col)
    {
        return m_panama.sqlite3_column_int(stmt, col);
    }

    /** @see org.sqlite.core.DB#bind_null(long, int) */
    @Override
    synchronized int bind_null(long stmt, int pos)
    {
        return m_panama.sqlite3_bind_null(stmt, pos);
    }

    /** @see org.sqlite.core.DB#bind_int(long, int, int) */
    @Override
    synchronized int bind_int(long stmt, int pos, int v)
    {
        return m_panama.sqlite3_bind_int(stmt, pos, v);
    }

    /** @see org.sqlite.core.DB#bind_long(long, int, long) */
    @Override
    synchronized int bind_long(long stmt, int pos, long v)
    {
        return m_panama.sqlite3_bind_int64(stmt, pos, v);
    }

    /** @see org.sqlite.core.DB#bind_double(long, int, double) */
    @Override
    synchronized int bind_double(long stmt, int pos, double v)
    {
        return m_panama.sqlite3_bind_double(stmt, pos, v);
    }

    /** @see org.sqlite.core.DB#bind_text(long, int, java.lang.String) */
    @Override
    synchronized int bind_text(long stmt, int pos, String v) {
        return bind_text_utf8(stmt, pos, v);
    }

    synchronized int bind_text_utf8(long stmt, int pos, String vUtf8)
    {
        int len = vUtf8.getBytes(StandardCharsets.UTF_8).length;
        return m_panama.sqlite3_bind_text(stmt, pos, vUtf8, len, SQLITE_TRANSIENT);
    }

    /** @see org.sqlite.core.DB#bind_blob(long, int, byte[]) */
    @Override
    synchronized int bind_blob(long stmt, int pos, byte[] v)
    {
        int len = 0;
        if (v != null && v.length == 0)
            v = null;
        else if (v != null)
            len = v.length;

        return m_panama.sqlite3_bind_blob(stmt, pos, v, len, SQLITE_TRANSIENT);
    }

    /** @see org.sqlite.core.DB#result_null(long) */
    @Override
    public synchronized void result_null(long context)
    {
        m_panama.sqlite3_result_null(toref(context));
    }


    /** @see org.sqlite.core.DB#result_text(long, java.lang.String) */
    @Override
    public synchronized void result_text(long context, String val) {

        if (context == 0)
            return;
        if (val == null)
        {
            m_panama.sqlite3_result_null(toref(context));
            return;
        }

        m_panama.sqlite3_result_text(toref(context), val, val.getBytes(StandardCharsets.UTF_8).length, SQLITE_TRANSIENT);
    }

    /** @see org.sqlite.core.DB#result_blob(long, byte[]) */
    @Override
    public synchronized void result_blob(long context, byte[] val)
    {
        m_panama.sqlite3_result_blob(toref(context), val, val.length, SQLITE_TRANSIENT);
    }

    /** @see org.sqlite.core.DB#result_double(long, double) */
    @Override
    public synchronized void result_double(long context, double val)
    {
        m_panama.sqlite3_result_double(toref(context), val);
    }

    /** @see org.sqlite.core.DB#result_long(long, long) */
    @Override
    public synchronized void result_long(long context, long val)
    {
        m_panama.sqlite3_result_int64(toref(context), val);
    }

    /** @see org.sqlite.core.DB#result_int(long, int) */
    @Override
    public synchronized void result_int(long context, int val)
    {
        m_panama.sqlite3_result_int(toref(context), val);
    }

    /** @see org.sqlite.core.DB#result_error(long, java.lang.String) */
    @Override
    public synchronized void result_error(long context, String err) {

        m_panama.sqlite3_result_error(toref(context), err, err.getBytes(StandardCharsets.UTF_8).length);
    }

    /** @see org.sqlite.core.DB#value_text(org.sqlite.Function, int) */
    @Override
    public synchronized String value_text(Function f, int arg) {
        return m_panama.sqlite3_value_text(tovalue(f, arg));
    }

    /** @see org.sqlite.core.DB#value_blob(org.sqlite.Function, int) */
    @Override
    public synchronized byte[] value_blob(Function f, int arg)
    {
        var value = tovalue(f, arg);
        var mem = m_panama.sqlite3_value_blob(value);
        if (mem == MemoryAddress.NULL)
            return null;

        int len = m_panama.sqlite3_value_bytes(value);
        return Utils.toArrByte(mem, len);
    }

    /** @see org.sqlite.core.DB#value_double(org.sqlite.Function, int) */
    @Override
    public synchronized double value_double(Function f, int arg)
    {
        return m_panama.sqlite3_value_double(tovalue(f, arg));
    }

    /** @see org.sqlite.core.DB#value_long(org.sqlite.Function, int) */
    @Override
    public synchronized long value_long(Function f, int arg)
    {
        return m_panama.sqlite3_value_int64(tovalue(f, arg));
    }

    /** @see org.sqlite.core.DB#value_int(org.sqlite.Function, int) */
    @Override
    public synchronized int value_int(Function f, int arg)
    {
        return m_panama.sqlite3_value_int(tovalue(f, arg));
    }

    /** @see org.sqlite.core.DB#value_type(org.sqlite.Function, int) */
    @Override
    public synchronized int value_type(Function f, int arg)
    {
        return m_panama.sqlite3_value_type(tovalue(f, arg));
    }

    /** @see org.sqlite.core.DB#create_function(java.lang.String, org.sqlite.Function, int, int) */
    @Override
    public synchronized int create_function(String name, Function func, int nArgs, int flags) {
        return create_function_utf8(name, func, nArgs, flags);
    }

    synchronized int create_function_utf8(String name, Function func, int nArgs, int flags)
    {
        var pFunc = new PanamaFunction(this, func);
        callbackFunctions.put(name, pFunc);

        if (func instanceof Function.Aggregate)
        {

            return m_panama.sqlite3_create_window_function(m_dbHandle.handle(), name, nArgs, SQLITE_UTF16 | flags,
                    0,
                    pFunc.getxStepCall(),
                    pFunc.getxFinalCall(),
                    func instanceof Function.Window ? pFunc.getxValueCall() : MemoryAddress.NULL,
                    func instanceof Function.Window ? pFunc.getxInverseCall() : MemoryAddress.NULL,
                    MemoryAddress.NULL);
        }

        return m_panama.sqlite3_create_function(m_dbHandle.handle(), name, nArgs, SQLITE_UTF16 | flags,
                0,
                pFunc.getxFuncCall(),
                MemoryAddress.NULL,
                MemoryAddress.NULL);
    }

    /** @see org.sqlite.core.DB#destroy_function(java.lang.String, int) */
    @Override
    public synchronized int destroy_function(String name, int nArgs) {
        callbackFunctions.remove(name);
        return m_panama.sqlite3_create_function(m_dbHandle.handle(), name, nArgs, SQLITE_UTF16,
                0,
                MemoryAddress.NULL,
                MemoryAddress.NULL,
                MemoryAddress.NULL);
    }


    HashMap<String, CollateCallback> activeCollates = new HashMap<>();

    /** @see org.sqlite.core.DB#create_collation(String, Collation) */
    @Override
    public synchronized int create_collation(String name, Collation coll) {
        var callback = new CollateCallback(coll);
        activeCollates.put(name, callback);

        return m_panama.sqlite3_create_collation(
                m_dbHandle.handle(),
                name,            // collation name
                SQLITE_UTF8,          // preferred chars
                MemoryAddress.NULL,
                callbackCreator.createCallback(callback, "callback"));
    }


    /** @see org.sqlite.core.DB#destroy_collation(String) */
    @Override
    public synchronized int destroy_collation(String name) {

        activeCollates.remove(name);

        return m_panama.sqlite3_create_collation(
                m_dbHandle.handle(),
                name,            // collation name
                SQLITE_UTF8,          // preferred chars
                MemoryAddress.NULL,
                MemoryAddress.NULL);
     }


    /** @see org.sqlite.core.DB#free_functions() */
    @Override
    synchronized  void free_functions()
    {
        callbackFunctions.clear();
    }

    @Override
    public synchronized  int limit(int id, int value) throws SQLException
    {
        return m_panama.sqlite3_limit(m_dbHandle.handle(), id, value);
    }


    /**
     * @see org.sqlite.core.DB#backup(java.lang.String, java.lang.String,
     *     org.sqlite.core.DB.ProgressObserver)
     */
    @Override
    public int backup(String dbName, String destFileName, ProgressObserver observer)
            throws SQLException {

        int flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE;
        if (m_panama.sqlite3_strnicmp(destFileName, "file:", 5) == 0) {
            flags |= SQLITE_OPEN_URI;
        }

        var pFile = DBHandle.createOpenHandle();
        int rc = m_panama.sqlite3_open_v2(destFileName, pFile, flags, null);
        long handle = pFile[0].handle();

        if( rc==SQLITE_OK ){

            /* Open the sqlite3_backup object used to accomplish the transfer */
            var pBackup = m_panama.sqlite3_backup_init(handle, "main", m_dbHandle.handle(), dbName);
            if( pBackup != 0 ){
                while((rc = m_panama.sqlite3_backup_step(pBackup,100))==SQLITE_OK ){}

                /* Release resources allocated by backup_init(). */
                m_panama.sqlite3_backup_finish(pBackup);
            }
            rc = m_panama.sqlite3_errcode(handle);
        }

        /* Close the database connection opened on database file zFilename
         ** and return the result of this function. */
        m_panama.sqlite3_close(handle);

        return rc;
    }

    /**
     * @see org.sqlite.core.DB#restore(java.lang.String, java.lang.String,
     *     org.sqlite.core.DB.ProgressObserver)
     */
    @Override
    public synchronized int restore(String dbName, String sourceFileName, ProgressObserver observer)
            throws SQLException {

        /* Open the database file identified by dFileName. */
        int flags = SQLITE_OPEN_READONLY;
        if (m_panama.sqlite3_strnicmp(sourceFileName, "file:", 5) == 0) {
            flags |= SQLITE_OPEN_URI;
        }

        var pFile = DBHandle.createOpenHandle();
        int rc = m_panama.sqlite3_open_v2(sourceFileName, pFile, flags, null);
        long handle = pFile[0].handle();

        if( rc==SQLITE_OK ){
            int nTimeout = 0;

            /* Open the sqlite3_backup object used to accomplish the transfer */
            var pBackup = m_panama.sqlite3_backup_init(m_dbHandle.handle(), dbName, handle, "main");
            if( pBackup != 0 ){
                while( (rc = m_panama.sqlite3_backup_step(pBackup,100))==SQLITE_OK
                        || rc==SQLITE_BUSY  ){
                    if( rc==SQLITE_BUSY ){
                        if( nTimeout++ >= 3 ) break;
                        m_panama.sqlite3_sleep(100);
                    }
                }
                /* Release resources allocated by backup_init(). */
                m_panama.sqlite3_backup_finish(pBackup);
            }
            rc = m_panama.sqlite3_errcode(handle);
        }

        /* Close the database connection opened on database file zFilename
         ** and return the result of this function. */
        m_panama.sqlite3_close(handle);
        return rc;
    }

    // COMPOUND FUNCTIONS (for optimisation) /////////////////////////

    /**
     * Provides metadata for table columns.
     *
     * @return For each column returns: <br>
     *     res[col][0] = true if column constrained NOT NULL<br>
     *     res[col][1] = true if column is part of the primary key<br>
     *     res[col][2] = true if column is auto-increment.
     * @see org.sqlite.core.DB#column_metadata(long)
     */
    @Override
    synchronized boolean[][] column_metadata(long stmt)
    {
        int colCount = m_panama.sqlite3_column_count(stmt);
        boolean[][] ret = new boolean[colCount][3];


        for (int n = 0; n < colCount; ++n)
        {
            var zColumnName = m_panama.sqlite3_column_name(stmt, n);
            var zTableName  = m_panama.sqlite3_column_table_name(stmt, n);

            int[] notNull = {0};
            int[] primaryKey = {0};
            int[] autoIncrement = {0};

            if (zColumnName != null && zTableName != null) {
                m_panama.sqlite3_table_column_metadata(m_dbHandle.handle(), null,
                        zTableName, zColumnName, MemoryAddress.NULL, MemoryAddress.NULL,
                        notNull, primaryKey, autoIncrement);
            }

            ret[n][0] = notNull[0] != 0;
            ret[n][1] = primaryKey[0] != 0;
            ret[n][2] = autoIncrement[0] != 0;
        }

        return ret;
    }


        @Override
    synchronized void set_commit_listener(boolean enabled)
    {
        if (enabled)
        {
            m_panama.sqlite3_commit_hook(m_dbHandle.handle(), callbackCreator.createCallback(this, "commit_hook"), MemoryAddress.NULL);
            m_panama.sqlite3_rollback_hook(m_dbHandle.handle(), callbackCreator.createCallback(this, "rollback_hook"), MemoryAddress.NULL);
        }
        else
        {
            m_panama.sqlite3_commit_hook(m_dbHandle.handle(), MemoryAddress.NULL, MemoryAddress.NULL);
            m_panama.sqlite3_rollback_hook(m_dbHandle.handle(), MemoryAddress.NULL, MemoryAddress.NULL);
        }
    }

    @Override
    synchronized void set_update_listener(boolean enabled)
    {
        if (enabled)
            m_panama.sqlite3_update_hook(m_dbHandle.handle(), callbackCreator.createCallback(this, "update_hook"), MemoryAddress.NULL);
        else
            m_panama.sqlite3_update_hook(m_dbHandle.handle(), MemoryAddress.NULL, MemoryAddress.NULL);
    }

    /**
     * Throws an SQLException
     *
     * @param msg Message for the SQLException.
     * @throws SQLException
     */
    static void throwex(String msg) throws SQLException {
        throw new SQLException(msg);
    }


    public synchronized void register_progress_handler(int vmCalls, ProgressHandler progressHandler)
    {
        var progress = new ProgressFunction(progressHandler);
        progressList.add(progress);

        m_panama.sqlite3_progress_handler(m_dbHandle.handle(), vmCalls,
                callbackCreator.createCallback(progress, "progress"), MemoryAddress.NULL);
    }

    public synchronized void clear_progress_handler()
    {
        m_panama.sqlite3_progress_handler(m_dbHandle.handle(), 0, MemoryAddress.NULL, MemoryAddress.NULL);
        progressList.clear();
    }

    public synchronized void setError(long context, String msg)
    {
        m_panama.sqlite3_result_error(toref(context), msg, msg.getBytes(StandardCharsets.UTF_8).length);
    }

    private static MemoryAddress toref(long context)
    {
        return MemoryAddress.ofLong(context);
    }

    private static MemoryAddress tovalue(Function f, int arg)
    {
        if (arg < 0)
            throw new IllegalArgumentException("Arg must bet >= 0, not "+ arg);

        try {
            int args = f.args();
            long value = f.value();
            var addr = toref(value);

            if (arg >= args)
                throw new IllegalArgumentException("arg out of range");

            return toref(Utils.toArrLong(addr, args)[arg]);
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }

        return MemoryAddress.NULL;
    }

    public int commit_hook(MemoryAddress context)
    {
        onCommit(true);
        return 0;
    }

    public void rollback_hook(MemoryAddress context)
    {
        onCommit(false);
    }

    public void update_hook(MemoryAddress context, int type, MemoryAddress database, MemoryAddress table, long row) {

        onUpdate( type, Utils.readString(database), Utils.readString(table), row);
    }


    public static class CollateCallback
    {
        Collation collation;

        CollateCallback(Collation callback)
        {
            collation = callback;
        }

        public int callback(MemoryAddress context, int len1, MemoryAddress str1, int len2, MemoryAddress str2 )
        {
            String s1 = new String(Utils.toArrByte(str1, len1));
            String s2 = new String(Utils.toArrByte(str2, len2));

            return collation.xCompare(s1, s2);
        }
    }

    public static class PanamaBusyHandler
    {
        private final BusyHandler wrapped;

        PanamaBusyHandler(BusyHandler bh)
        {
            wrapped = bh;
        }

        public int callback(MemoryAddress addr, int nbPrevInvok) throws SQLException {
                return wrapped.callback(nbPrevInvok);
        }
    }

    public static class ProgressFunction {
        private final ProgressHandler progress;

        ProgressFunction(ProgressHandler handler)
        {
            progress = handler;
        }

        public int progress(MemoryAddress address)
        {
            try {
                return progress.progress();
            }
            catch (SQLException ex)
            {
                return -1;
            }
        }
    }

    public PanamaCallbacks getCallbackCreator()
    {
        return callbackCreator;
    }
}
