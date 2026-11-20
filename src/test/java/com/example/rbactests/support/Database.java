package com.example.rbactests.support;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Direct, READ-ONLY access to the backend's SQLite file, used only to
 * verify that writes made through the real API actually persisted.
 * Every write in this suite still goes through POST/PUT/DELETE -- this
 * class never writes anything itself.
 *
 * The path can be overridden with -Drbac.dbPath=... (see README) for
 * whatever layout your checkout actually uses; the default assumes this
 * project and rbac-healthcare-system are cloned as sibling folders.
 */
public final class Database {

    private static final String DEFAULT_PATH = "../rbac-healthcare-system/backend/database/rbac.db";

    private Database() {
    }

    public static Connection connect() throws SQLException {
        String path = System.getProperty("rbac.dbPath", DEFAULT_PATH);
        return DriverManager.getConnection("jdbc:sqlite:" + path);
    }

    /** True only if the configured SQLite file actually exists on disk. */
    public static boolean isAvailable() {
        String path = System.getProperty("rbac.dbPath", DEFAULT_PATH);
        return new File(path).isFile();
    }
}
