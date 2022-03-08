module sqlite.jdbc {
    requires java.sql;
    requires jpassport;
    requires jdk.incubator.foreign;

    exports org.sqlite.core.panama;
    exports org.sqlite;
    opens org.sqlite.util;

    provides java.sql.Driver with org.sqlite.JDBC;
}