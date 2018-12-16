import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// java -classpath ".:/Users/gjtorikian/Downloads/sqlite-jdbc-3.23.1.jar" CreateDB

public class CreateDB {
    public static void main(String[] args) throws ClassNotFoundException {
        // load the sqlite-JDBC driver using the current class loader
        Class.forName("org.sqlite.JDBC");

        Connection connection = null;
        try {
            // create a database connection
            connection = DriverManager.getConnection("jdbc:sqlite:src/main/resources/ridesharing.db");
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.

            // Customers: have ID, name and number
            statement.executeUpdate("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, number TEXT)");

            // Drivers: have ID, name and number
            statement.executeUpdate("CREATE TABLE drivers (id INTEGER PRIMARY KEY, name TEXT, number TEXT)");

            // Proxy Numbers: have ID and number
            statement.executeUpdate("CREATE TABLE proxy_numbers (id INTEGER PRIMARY KEY, number TEXT)");

            // Rides: have ID, start, destination and date; are connected to a customer, a driver, and a proxy number
            statement.executeUpdate("CREATE TABLE rides (id INTEGER PRIMARY KEY, start TEXT, destination TEXT, datetime TEXT, customer_id INTEGER, driver_id INTEGER, number_id INTEGER, FOREIGN KEY (customer_id) REFERENCES customers(id), FOREIGN KEY (driver_id) REFERENCES drivers(id))");

            // Insert some data

            // Create a sample customer for testing
            // -> enter your name and number here!
            statement.executeUpdate("INSERT INTO customers (name, number) VALUES ('Caitlyn Carless', '+14153596403')");

            // Create a sample driver for testing
            // -> enter your name and number here!
            statement.executeUpdate("INSERT INTO drivers (name, number) VALUES ('David Driver', '+14153596403')");

            // Create a proxy number
            // -> provide a number purchased from MessageBird here
            // -> copy the line if you have more numbers

            statement.executeUpdate("INSERT INTO proxy_numbers (number) VALUES ('+19094177737')");
        } catch (SQLException e) {
            // if the error message is "out of memory",
            // it probably means no database file is found
            System.err.println(e.getMessage());
        } finally {
            try {
                if (connection != null)
                    connection.close();
            } catch (SQLException e) {
                // connection close failed.
                System.err.println(e);
            }
        }
    }
}