package org.sqlite.core.panama;


import java.sql.SQLException;

public record DBHandle(long handle) {
    static public DBHandle[] createOpenHandle()
    {
        return new DBHandle[] {new DBHandle(0)};
    }

    public boolean isValid()
    {
        return handle != 0;
    }

    public void checkValid() throws SQLException
    {
        if (!isValid())
            throw new SQLException("Database is closed");
    }
}
