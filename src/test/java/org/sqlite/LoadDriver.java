package org.sqlite;

public class LoadDriver {
    public static void load() throws ClassNotFoundException
    {
        Class.forName("org.sqlite.JDBC");
    }
}
