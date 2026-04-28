package me.clip.ezblocks.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * MySQL Database.
 * @author Maximvdw
 */
public class MySQL extends Database {
	private static final Logger LOG = Logger.getLogger("EZBlocks");

	private String hostname = "localhost";
	private String portnmbr = "3306";
	private String username = "minecraft";
	private String password = "";
	private String database = "minecraft";

	public MySQL(String prefix, String hostname, String portnmbr,
			String database, String username, String password) {
		super(prefix);
		this.hostname = hostname;
		this.portnmbr = portnmbr;
		this.database = database;
		this.username = username;
		this.password = password;
	}

	protected boolean initialize() {
		// Try modern (8.x) driver first, fall back to legacy
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			return true;
		} catch (ClassNotFoundException ignored) {
			// fall through
		}
		try {
			Class.forName("com.mysql.jdbc.Driver");
			return true;
		} catch (ClassNotFoundException e) {
			LOG.severe("[EZBlocks] No MySQL JDBC driver found on the classpath.");
			return false;
		}
	}

	public Connection open() {
		this.open(true);
		return this.connection;
	}

	public Connection open(boolean showError) {
		if (initialize()) {
			String url = "";
			try {
				url = "jdbc:mysql://" + this.hostname + ":" + this.portnmbr
						+ "/" + this.database
						+ "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8";
				this.connection = DriverManager.getConnection(url,
						this.username, this.password);
				if (checkConnection())
					connected = true;
				return this.connection;
			} catch (SQLException e) {
				if (showError) {
					LOG.severe("[EZBlocks] " + url);
					LOG.severe("[EZBlocks] Could not connect: " + e.getMessage());
				}
			} catch (Exception e) {
				LOG.severe("[EZBlocks] Unexpected error opening MySQL connection: " + e.getMessage());
			}
		}
		return null;
	}

	public void close() {
		try {
			if (connection != null)
				connection.close();
		} catch (Exception e) {
			LOG.severe("[EZBlocks] Failed to close database connection: " + e.getMessage());
		}
	}

	public Connection getConnection() {
		if (this.connection == null)
			return open();
		try {
			if (this.connection.isClosed()) {
				return open();
			}
		} catch (SQLException e) {
			LOG.severe("[EZBlocks] Could not check connection state: " + e.getMessage());
		}
		return this.connection;
	}

	public boolean checkConnection() {
		return connection != null;
	}

	public ResultSet query(String query) {
		Statement statement = null;
		ResultSet result = null;
		try {
			statement = getConnection().createStatement();

			switch (getStatement(query)) {
			case SELECT:
				result = statement.executeQuery(query);
				break;
			default:
				statement.executeUpdate(query);
			}
			return result;
		} catch (SQLException e) {
			LOG.severe("[EZBlocks] Error in SQL query: " + e.getMessage());
		}
		return result;
	}

	public PreparedStatement prepare(String query) {
		try {
			return getConnection().prepareStatement(query);
		} catch (SQLException e) {
			if (!e.toString().contains("not return ResultSet"))
				LOG.severe("[EZBlocks] Error in SQL prepare() query: " + e.getMessage());
		}
		return null;
	}

	public boolean createTable(String query) {
		Statement statement = null;
		try {
			if (query == null || query.isEmpty()) {
				LOG.severe("[EZBlocks] SQL query empty: createTable(" + query + ")");
				return false;
			}
			statement = getConnection().createStatement();
			statement.execute(query);
			return true;
		} catch (SQLException e) {
			LOG.severe("[EZBlocks] " + e.getMessage());
			return false;
		} catch (Exception e) {
			LOG.severe("[EZBlocks] " + e.getMessage());
			return false;
		}
	}

	public boolean checkTable(String table) {
		try {
			Statement statement = getConnection().createStatement();
			ResultSet result = statement.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1");
			return result != null;
		} catch (SQLException e) {
			if (e.getMessage() != null && e.getMessage().toLowerCase().contains("exist")) {
				return false;
			}
			LOG.severe("[EZBlocks] Error in SQL query: " + e.getMessage());
			return false;
		}
	}

	public boolean wipeTable(String table) {
		Statement statement = null;
		String query = null;
		try {
			if (!this.checkTable(table)) {
				LOG.severe("[EZBlocks] Error wiping table: \"" + table + "\" does not exist.");
				return false;
			}
			statement = getConnection().createStatement();
			query = "DELETE FROM `" + table + "`";
			statement.executeUpdate(query);
			return true;
		} catch (SQLException e) {
			if (!e.toString().contains("not return ResultSet"))
				return false;
		}
		return false;
	}

	@Override
	public String getCreateStatement(String table) {
		if (checkTable(table)) {
			try {
				ResultSet result = query("SHOW CREATE TABLE `" + table + "`");
				if (result != null && result.next()) {
					return result.getString(2);
				}
			} catch (Exception ignored) {
			}
		}
		return "";
	}
}
