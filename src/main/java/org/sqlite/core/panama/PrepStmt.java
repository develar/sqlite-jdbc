package org.sqlite.core.panama;

public record PrepStmt(long handle) {
    static public PrepStmt[] createPrepareHandle()
    {
        return new PrepStmt[] {new PrepStmt(0)};
    }
}
