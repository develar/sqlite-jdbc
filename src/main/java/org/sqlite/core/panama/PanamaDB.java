package org.sqlite.core.panama;

import jdk.incubator.foreign.MemoryAddress;
import jpassport.Passport;
import jpassport.annotations.PtrPtrArg;
import jpassport.annotations.RefArg;

public interface PanamaDB extends Passport {
    String sqlite3_libversion();
    String sqlite3_sourceid();
    int sqlite3_libversion_number();

    int sqlite3_compileoption_used(String zOptName);
    String sqlite3_compileoption_get(int N);

    int sqlite3_threadsafe();

    int sqlite3_close(long handle);
    int sqlite3_close_v2(long handle);

    int sqlite3_exec(
            long sqliteHandle,                                  /* An open database */
            String sql,                           /* SQL to be evaluated */
            int pass0,  /* Callback function */
            int pass0_1,                                    /* 1st argument to callback */
            @PtrPtrArg String errMsg);                              /* Error msg written here */


    int sqlite3_open(
            String filename,                    /* Database filename (UTF-8) */
            @RefArg DBHandle[]  sqliteHandle   /* OUT: SQLite db handle */
    );

    int sqlite3_open_v2(
            String filename,                /* Database filename (UTF-8) */
            @RefArg DBHandle[] sqliteHandle,    /* OUT: SQLite db handle */
            int flags,              /* Flags */
            String zVfs        /* Name of VFS module to use */
    );

    int sqlite3_prepare(
            long sqliteHandle,            /* Database handle */
            String zSql,       /* SQL statement, UTF-8 encoded */
            int nByte,              /* Maximum length of zSql in bytes. */
            @RefArg PrepStmt[] prepHandle,  /* OUT: Statement handle */
            @RefArg long[] pzTail     /* OUT: Pointer to unused portion of zSql */
    );

    int sqlite3_prepare_v2(
            long sqliteHandle,              /* Database handle */
            String zSql,                    /* SQL statement, UTF-8 encoded */
            int nByte,                      /* Maximum length of zSql in bytes. */
            @RefArg PrepStmt[] prepHandle,  /* OUT: Statement handle */
            long pzTail         /* OUT: Pointer to unused portion of zSql */
    );

    int sqlite3_prepare_v3(
            long sqliteHandle,      /* Database handle */
            String zSql,            /* SQL statement, UTF-8 encoded */
            int nByte,              /* Maximum length of zSql in bytes. */
            int prepFlags,          /* Zero or more SQLITE_PREPARE_ flags */
            @RefArg PrepStmt[] prepHandle,  /* OUT: Statement handle */
            @RefArg long[] pzTail         /* OUT: Pointer to unused portion of zSql */
    );

    String sqlite3_errmsg(long sqliteHandle);
    int sqlite3_finalize(long stmt);
    int sqlite3_step(long stmt);
    int sqlite3_data_count(long stmt);
    int sqlite3_column_count(long smt);
    String sqlite3_column_name(long stmt, int N);
    String sqlite3_column_decltype(long stmt,int N);
    int sqlite3_reset(long stmt);
    int sqlite3_clear_bindings(long stmt);
    int sqlite3_bind_parameter_count(long stmt);
    int sqlite3_column_type(long stmt, int col);
    String sqlite3_column_table_name(long stmt, int col);

    MemoryAddress sqlite3_column_value(long stmt, int iCol);

    String sqlite3_column_text(long stmt, int col);

    int sqlite3_value_int(MemoryAddress addr);
    long sqlite3_value_int64(MemoryAddress addr);
    String sqlite3_value_text(MemoryAddress addr);
    double sqlite3_value_double(MemoryAddress addr);
    MemoryAddress sqlite3_value_blob(MemoryAddress addr);
    int sqlite3_value_bytes(MemoryAddress addr);
    int sqlite3_value_type(MemoryAddress addr);

    int sqlite3_column_bytes(long stmt, int iCol);

    double sqlite3_column_double(long stmt, int col);
    long sqlite3_column_int64(long stmt, int col);
    int sqlite3_column_int(long stmt, int col);
    int sqlite3_bind_null(long stmt, int col);
    int sqlite3_bind_int(long stmt, int col, int val);
    int sqlite3_bind_int64(long stmt, int col, long val);
    int sqlite3_bind_double(long stmt, int col, double val);
    int sqlite3_bind_text(long stmt, int pos, String bytes, int v_nbytes, int address);
    MemoryAddress sqlite3_column_blob(long stmt, int col);
    int sqlite3_bind_blob(long stmt, int pos, byte[] a, int size, int type);

    void sqlite3_result_null(MemoryAddress context);
    void sqlite3_result_double(MemoryAddress context, double v);
    void sqlite3_result_int64(MemoryAddress context, long v);
    void sqlite3_result_int(MemoryAddress context, int v);
    void sqlite3_result_text(MemoryAddress context, String value_bytes, int value_nbytes, long var);
    void sqlite3_result_blob(MemoryAddress context, @RefArg byte[] bytes, int size, long var);

    int sqlite3_enable_shared_cache(int enable);
    int sqlite3_enable_load_extension(long sqliteHandle, int enable);
    void sqlite3_interrupt(long sqliteHandle);
    void sqlite3_busy_timeout(long sqliteHandle, int ms);
    int sqlite3_changes(long sqliteHandle);
    int sqlite3_total_changes(long sqliteHandle);
    int sqlite3_busy_handler(long sqliteHandle, MemoryAddress methodPtr, MemoryAddress address);


    int sqlite3_create_function(long sqliteHandle, String name, int nArgs, int flags, long UDF,
                                MemoryAddress xFunc, //xFunc(long  context, int, sqlite_value**);
                                MemoryAddress xStep,  //xStep(long  context, int, sqlite_value**);
                                MemoryAddress xFinal); //xFinal(long  context);


    int sqlite3_create_window_function(long sqliteHandle, String name, int nArgs, int flags, long UDF,
                                       MemoryAddress xStep, // xStep(long  context, int, sqlite_value**);
                                       MemoryAddress xFinal, // xFinal(long  context)
                                        MemoryAddress xValue, // xValue(long  context)
                                       MemoryAddress xInverse, //xInverse (long  context, int, sqlite_value**);
                                       MemoryAddress xDestroy); // xDestroy(void*)

}
