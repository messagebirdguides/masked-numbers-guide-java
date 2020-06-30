import com.messagebird.MessageBirdClient;
import com.messagebird.MessageBirdService;
import com.messagebird.MessageBirdServiceImpl;
import io.github.cdimascio.dotenv.Dotenv;
import org.sqlite.jdbc4.JDBC4ResultSet;
import spark.ModelAndView;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.math.BigInteger;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

public class MaskedSMSAndVoice {
    private static ArrayList<Object> convertToArray(ResultSet rs) {
        ArrayList<Object> results = new ArrayList<Object>();

        try {
            while (rs.next()) {
                ArrayList<String> row = new ArrayList<String>();
                for (int i = 1; i <= ((JDBC4ResultSet) rs).getColumnCount(); i++) {
                    row.add(rs.getString(i));
                }
                results.add(row.toArray());
                rs.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();

        // Create a MessageBirdService
        final MessageBirdService messageBirdService = new MessageBirdServiceImpl(dotenv.get("MESSAGEBIRD_API_KEY"));
        // Add the service to the client
        final MessageBirdClient messageBirdClient = new MessageBirdClient(messageBirdService);

        final Connection conn;
        final Statement stmt;

        String url = "jdbc:sqlite::resource:ridesharing.db";

        // create a connection to the database
        try {
            conn = DriverManager.getConnection(url);
            stmt = conn.createStatement();

            get("/",
                    (req, res) ->
                    {
                        Map<String, Object> model = new HashMap<>();

                        // Find unassigned proxy numbers
                        ResultSet proxyNumbers = stmt.executeQuery("SELECT number FROM proxy_numbers");
                        model.put("proxyNumbers", convertToArray(proxyNumbers));

                        // Find current rides
                        ResultSet rides = stmt.executeQuery("SELECT c.name AS customer, d.name AS driver, start, destination, datetime, p.number AS number FROM rides r JOIN customers c ON c.id = r.customer_id JOIN drivers d ON d.id = r.driver_id JOIN proxy_numbers p ON p.id = r.number_id");
                        model.put("rides", convertToArray(rides));

                        // Collect customers
                        ResultSet customers = stmt.executeQuery("SELECT * FROM customers");
                        model.put("customers", convertToArray(customers).toArray());

                        // Collect drivers
                        ResultSet drivers = stmt.executeQuery("SELECT * FROM drivers");
                        model.put("drivers", convertToArray(drivers));

                        return new ModelAndView(model, "admin.handlebars");
                    },

                    new HandlebarsTemplateEngine()
            );

            // Create a new ride
            post("/createride",
                    (req, res) ->
                    {
                        // Find customer details
                        ResultSet customer = stmt.executeQuery(String.format("SELECT * FROM customers WHERE id = %s", req.queryParams("customer")));
                        customer.next();
                        String customerID = customer.getString(1);
                        String customerName = customer.getString(2);

                        // Find driver details
                        ResultSet driver = stmt.executeQuery(String.format("SELECT * FROM customers WHERE id = %s", req.queryParams("driver")));
                        driver.next();
                        String driverID = driver.getString(1);
                        String driverName = driver.getString(2);

                        // Find a number that has not been used by the driver or the customer
                        String query = String.format("SELECT * FROM proxy_numbers \n" +
                                "WHERE id NOT IN (SELECT number_id FROM rides WHERE customer_id = %s) \n" +
                                        "AND id NOT IN (SELECT number_id FROM rides WHERE driver_id = %s)",
                                customerID,
                                driverID);
                        ResultSet proxyRow = stmt.executeQuery(String.format(query));

                        if (!proxyRow.next()) {
                            return "No number available! Please extend your pool.";
                        }

                        String proxyID = proxyRow.getString(1);
                        String proxyNumber = proxyRow.getString(2);

                        // Store ride in database
                        String sql = "INSERT INTO rides (start, destination, datetime, customer_id, driver_id, number_id) VALUES (?, ?, ?, ?, ?, ?)";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setString(1, req.queryParams("start"));
                        pstmt.setString(2, req.queryParams("destination"));
                        pstmt.setString(3, req.queryParams("datetime"));
                        pstmt.setString(4, customerID);
                        pstmt.setString(5, driverID);
                        pstmt.setString(6, proxyID);

                        pstmt.executeUpdate();

                        // Notify the customer
                        final List<BigInteger> customerPhone = new ArrayList<BigInteger>();
                        customerPhone.add(new BigInteger(proxyNumber));
                        messageBirdClient.sendMessage(proxyNumber, String.format("%s will pick you up at %s. Reply to this message to contact the driver.", driverName, req.queryParams("datetime")), customerPhone);

                        // Notify the driver
                        final List<BigInteger> driverPhone = new ArrayList<BigInteger>();
                        driverPhone.add(new BigInteger(proxyNumber));
                        messageBirdClient.sendMessage(proxyNumber, String.format("%s will wait for you at %s. Reply to this message to contact the customer.", customerName, req.queryParams("datetime")), driverPhone);

                        res.redirect("/");

                        return null;
                    }
            );

            // Handle incoming messages
            post("/webhook",
                    (req, res) ->
                    {
                        // Read input sent from MessageBird
                        String number = req.queryParams("originator");
                        String text = req.queryParams("params");
                        String proxy = req.queryParams("recipient");

                        // Find a number that has not been used by the driver or the customer
                        String query = String.format("SELECT c.number AS customer_number, d.number AS driver_number, p.number AS proxy_number FROM rides r JOIN customers c ON r.customer_id = c.id JOIN drivers d ON r.driver_id = d.id JOIN proxy_numbers p ON p.id = r.number_id WHERE proxy_number = %s AND (driver_number = %s OR customer_number = %s",
                                proxy,
                                number, number);
                        ResultSet row = stmt.executeQuery(String.format(query));

                        if (!row.next()) {
                            return "Could not find a ride for customer/driver " + number + " that uses proxy " + proxy;
                        }

                        // Need to find out whether customer or driver sent this and forward to the other side
                        String recipient = number == row.getString(1) ? row.getString(2) : row.getString(1);

                        // Forward the message through the MessageBird API
                        final List<BigInteger> recipientPhone = new ArrayList<BigInteger>();
                        recipientPhone.add(new BigInteger(recipient));
                        messageBirdClient.sendMessage(proxy, text, recipientPhone);

                        res.status(200);
                        return "";
                    }
            );


            // Handle incoming calls
            get("/webhook-voice",
                    (req, res) ->
                    {
                        // Read input sent from MessageBird
                        String number = req.queryParams("source");
                        String proxy = req.queryParams("destination");

                        // Answer will always be XML
                        res.type("application/xml");
                        String query = String.format("SELECT c.number AS customer_number, d.number AS driver_number, p.number AS proxy_number \n" +
                                        "FROM rides r JOIN customers c ON r.customer_id = c.id JOIN drivers d ON r.driver_id = d.id JOIN proxy_numbers p ON p.id = r.number_id \n" +
                                        "WHERE proxy_number = %s AND (driver_number = %s OR customer_number = %s)",
                                proxy,
                                number, number);

                        ResultSet row = stmt.executeQuery(String.format(query));

                        // Cannot match numbers
                        if (!row.next()) {
                            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Say language=\"en-GB\" voice=\"female\">Sorry, we cannot identify your transaction. Make sure you call in from the number you registered.</Say>";
                        }

                        // Need to find out whether customer or driver sent this and forward to the other side
                        String recipient = number == row.getString(1) ? row.getString(2) : row.getString(1);
                        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> < Transfer destination =\"" + recipient + "\" mask=\"true\" />";
                    }
            );
        } catch (
                SQLException e) {
            e.printStackTrace();
        }
    }
}