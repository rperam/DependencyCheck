/*
 * This file is part of dependency-check-core.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2014 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.data.nvdcve;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.owasp.dependencycheck.utils.DBUtils;
import org.owasp.dependencycheck.utils.Settings;

/**
 * Loads the configured database driver and returns the database connection. If the embedded H2 database is used
 * obtaining a connection will ensure the database file exists and that the appropriate table structure has been
 * created.
 *
 * @author Jeremy Long <jeremy.long@owasp.org>
 */
public final class ConnectionFactory {

    /**
     * The version of the current DB Schema.
     */
    public static final String DB_SCHEMA_VERSION = "2.9";
    /**
     * Resource location for SQL file used to create the database schema.
     */
    public static final String DB_STRUCTURE_RESOURCE = "data/initialize.sql";

    /**
     * Private constructor for this factory class; no instance is ever needed.
     */
    private ConnectionFactory() {
    }

    /**
     * Constructs a new database connection object per the database configuration. This will load the appropriate
     * database driver, via the DriverManager, if configured.
     *
     * @return a database connection object
     * @throws DatabaseException thrown if there is an exception loading the database connection
     */
    public static Connection getConnection() throws DatabaseException {
        Connection conn = null;
        String connStr = null;
        final String user = Settings.getString(Settings.KEYS.DB_USER, "dcuser");
        //yes, yes - hard-coded password - only if there isn't one in the properties file.
        final String pass = Settings.getString(Settings.KEYS.DB_PASSWORD, "DC-Pass1337!");
        try {
            connStr = getConnectionString();

            Logger.getLogger(CveDB.class.getName()).log(Level.FINE, "Loading database connection");
            Logger.getLogger(CveDB.class.getName()).log(Level.FINE, "Connection String: {0}", connStr);
            Logger.getLogger(CveDB.class.getName()).log(Level.FINE, "Database User: {0}", user);
            boolean createTables = false;
            if (connStr.startsWith("jdbc:h2:file:")) { //H2
                createTables = needToCreateDatabaseStructure();
                Logger.getLogger(CveDB.class.getName()).log(Level.FINE, "Need to create DB Structure: {0}", createTables);
            }
            final String driverName = Settings.getString(Settings.KEYS.DB_DRIVER_NAME, "");
            if (!driverName.isEmpty()) { //likely need to load the correct driver
                Logger.getLogger(CveDB.class.getName()).log(Level.FINE, "Loading driver: {0}", driverName);
                final String driverPath = Settings.getString(Settings.KEYS.DB_DRIVER_PATH, "");
                if (!driverPath.isEmpty()) { //ugh, driver is not on classpath?
                    Logger.getLogger(CveDB.class.getName()).log(Level.FINE, "Loading driver from: {0}", driverPath);
                    DriverLoader.load(driverName, driverPath);
                } else {
                    DriverLoader.load(driverName);
                }
            }

            conn = DriverManager.getConnection(connStr, user, pass);
            if (createTables) {
                try {
                    createTables(conn);
                } catch (DatabaseException ex) {
                    Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
                    throw new DatabaseException("Unable to create the database structure");
                }
            } else {
                try {
                    ensureSchemaVersion(conn);
                } catch (DatabaseException ex) {
                    Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
                    throw new DatabaseException("Database schema does not match this version of dependency-check");
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
            throw new DatabaseException("Unable to load database");
        } catch (DriverLoadException ex) {
            Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
            throw new DatabaseException("Unable to load database driver");
        } catch (SQLException ex) {
            if (ex.getMessage().contains("java.net.UnknownHostException") && connStr.contains("AUTO_SERVER=TRUE;")) {
                final String newConnStr = connStr.replace("AUTO_SERVER=TRUE;", "");
                try {
                    conn = DriverManager.getConnection(newConnStr, user, pass);
                    Settings.setString(Settings.KEYS.DB_CONNECTION_STRING, newConnStr);
                    Logger.getLogger(ConnectionFactory.class.getName()).log(Level.WARNING, "Unable to start the database in server mode; reverting to single user mode");
                } catch (SQLException sqlex) {
                    Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
                    throw new DatabaseException("Unable to connect to the database");
                }
            } else {
                Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
                throw new DatabaseException("Unable to connect to the database");
            }
        }
        return conn;
    }

    /**
     * Returns the configured connection string. If using the embedded H2 database this function will also ensure the
     * data directory exists and if not create it.
     *
     * @return the connection string
     * @throws IOException thrown the data directory cannot be created
     */
    private static String getConnectionString() throws IOException {
        final String connStr = Settings.getString(Settings.KEYS.DB_CONNECTION_STRING, "jdbc:h2:file:%s;AUTO_SERVER=TRUE");
        if (connStr.contains("%s")) {
            final String directory = getDataDirectory().getCanonicalPath();
            final File dataFile = new File(directory, "cve." + DB_SCHEMA_VERSION);
            Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, String.format("File path for H2 file: '%s'", dataFile.toString()));
            return String.format(connStr, dataFile.getAbsolutePath());
        }
        return connStr;
    }

    /**
     * Retrieves the directory that the JAR file exists in so that we can ensure we always use a common data directory
     * for the embedded H2 database. This is public solely for some unit tests; otherwise this should be private.
     *
     * @return the data directory to store data files
     * @throws IOException is thrown if an IOException occurs of course...
     */
    public static File getDataDirectory() throws IOException {
        final File path = Settings.getDataFile(Settings.KEYS.DATA_DIRECTORY);
        if (!path.exists()) {
            if (!path.mkdirs()) {
                throw new IOException("Unable to create NVD CVE Data directory");
            }
        }
        return path;
    }

    /**
     * Determines if the H2 database file exists. If it does not exist then the data structure will need to be created.
     *
     * @return true if the H2 database file does not exist; otherwise false
     * @throws IOException thrown if the data directory does not exist and cannot be created
     */
    private static boolean needToCreateDatabaseStructure() throws IOException {
        final File dir = getDataDirectory();
        final String name = String.format("cve.%s.h2.db", DB_SCHEMA_VERSION);
        final File file = new File(dir, name);
        return !file.exists();
    }

    /**
     * Creates the database structure (tables and indexes) to store the CVE data.
     *
     * @param conn the database connection
     * @throws DatabaseException thrown if there is a Database Exception
     */
    private static void createTables(Connection conn) throws DatabaseException {
        Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, "Creating database structure");
        InputStream is;
        InputStreamReader reader;
        BufferedReader in = null;
        try {
            is = ConnectionFactory.class.getClassLoader().getResourceAsStream(DB_STRUCTURE_RESOURCE);
            reader = new InputStreamReader(is, "UTF-8");
            in = new BufferedReader(reader);
            final StringBuilder sb = new StringBuilder(2110);
            String tmp;
            while ((tmp = in.readLine()) != null) {
                sb.append(tmp);
            }
            Statement statement = null;
            try {
                statement = conn.createStatement();
                statement.execute(sb.toString());
            } catch (SQLException ex) {
                Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
                throw new DatabaseException("Unable to create database statement", ex);
            } finally {
                DBUtils.closeStatement(statement);
            }
        } catch (IOException ex) {
            throw new DatabaseException("Unable to create database schema", ex);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                    Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINEST, null, ex);
                }
            }
        }
    }

    /**
     * Uses the provided connection to check the specified schema version within the database.
     *
     * @param conn the database connection object
     * @throws DatabaseException thrown if the schema version is not compatible with this version of dependency-check
     */
    private static void ensureSchemaVersion(Connection conn) throws DatabaseException {
        ResultSet rs = null;
        CallableStatement cs = null;
        try {
            cs = conn.prepareCall("SELECT value FROM properties WHERE id = 'version'");
            rs = cs.executeQuery();
            if (rs.next()) {
                final boolean isWrongSchema = !DB_SCHEMA_VERSION.equals(rs.getString(1));
                if (isWrongSchema) {
                    throw new DatabaseException("Incorrect database schema; unable to continue");
                }
            } else {
                throw new DatabaseException("Database schema is missing");
            }
        } catch (SQLException ex) {
            Logger.getLogger(ConnectionFactory.class.getName()).log(Level.FINE, null, ex);
            throw new DatabaseException("Unable to check the database schema version");
        } finally {
            DBUtils.closeResultSet(rs);
            DBUtils.closeStatement(cs);
        }
    }
}
