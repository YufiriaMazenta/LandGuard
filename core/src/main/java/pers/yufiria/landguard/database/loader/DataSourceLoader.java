package pers.yufiria.landguard.database.loader;

import crypticlib.database.connection.ConnectionSource;

import java.sql.SQLException;

public interface DataSourceLoader {

    ConnectionSource load() throws SQLException;

}
