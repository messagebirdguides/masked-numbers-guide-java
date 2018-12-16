# Setting Masked Phone Numbers with MessageBird

### ⏱ 30 min build time

## Why build a number masking application?

In this MessageBird Developer Tutorial, you’ll learn how to anonymize and protect your users’ personal information by building a basic masked numbers application powered by the MessageBird API.

Online service platforms, such as ride-sharing, online food delivery and logistics, facilitate the experience between customers and providers by matching both sides of the transaction to ensure everything runs smoothly and the transaction is completed.

Sometimes it’s necessary for customers and providers to talk or message each other directly; a problem arises since, for many reasons, both parties may not feel comfortable sharing their personal phone number. A great solution to this issue is using anonymous proxy phone numbers that mask a user's personal phone number while also protect the platform's personal contact details. The result: a customer doesn't see their provider's phone number but a number that belongs to the platform and forwards their call to the provider, and vice versa for providers.

Along this tutorial, we'll show you how to build a proxy system to mask phone numbers in Java for our fictitious ride-sharing platform, _BirdCar_. The sample application includes a data model for customers, drivers, rides and proxy phone numbers and allows setting up new rides from an admin interface for demonstration purposes.

## Using a Number Pool

Before we dive into building the sample application, let's take a moment to understand the concept of a number pool. The idea is to set up a list of numbers by purchasing one or more [virtual mobile numbers](https://www.messagebird.com/numbers) from MessageBird and adding them to a database. Whenever a ride is created, the BirdCar application will automatically search the pool for a driver that is available and then assign the ride.

For simplicity and to allow testing with a single number, BirdCar assigns only one number to each ride, not one for each party. If the customer calls or texts this number, is connected to the driver; if the driver rings, the call or text is forwarded to the customer. The incoming caller or message sender identification sent from the network is used to determine which party calls and consequently find the other party's number.

Relying on the caller identification has the additional advantage that you don’t have to purchase a new phone number for each transaction; instead, it is possible to assign the same one to multiple transactions as long as different people are involved. The ride can be looked up based on who is calling. It is also possible to recycle numbers even for the same customer or driver, that is, returning them to the pool, although we haven’t implemented this behavior in the sample code. In any case, the number should remain active for some time after a transaction has ended, just in case the driver and customer need to communicate afterwards; for example, if the customer has forgotten an item in the driver’s car. 😱

## Getting Started

BirdCar's sample application uses Java with the [Spark framework](http://sparkjava.com/); it also uses a relational database to store the data model. We bundled [SQLite](https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc) with the application so that you don’t have to set up an RDBMS like MySQL, but if you extend the code for production use, you can still reuse the SQL queries with other databases.

You can download or clone the complete source code from the [MessageBird Developer Tutorials GitHub repository](https://github.com/messagebirdguides/verify-voice-guide-java) to run the application on your computer and follow along with the tutorial. Keep in mind that to run the sample you need to have [Java 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and [Maven](https://maven.apache.org/) installed.

The `pom.xml` file has all the dependencies the project needs. Your IDE should be configured to automatically install them.

## Prerequisites for Receiving Messages and Calls

### Overview

The BirdCar system receives incoming messages and calls and forwards them. From a high-level viewpoint, receiving with MessageBird is relatively simple: an application defines a webhook URL, which you assign to a number purchased in the MessageBird Dashboard using a flow. A [webhook](https://en.wikipedia.org/wiki/Webhook) is a URL on your site that doesn't render a page to users but is like an API endpoint that can be triggered by other servers. Every time someone sends a message or calls that number, MessageBird collects it and forwards it to the webhook URL where you can process it.

### Exposing your Development Server with ngrok

When working with webhooks, an external service like MessageBird needs to access your application, so the URL must be public. During development, though, you're typically working in a local development environment that is not publicly available. There are various tools and services available that allow you to quickly expose your development environment to the Internet by providing a tunnel from a public URL to your local machine. One of the most popular tools is [ngrok](https://ngrok.com/).

You can [download ngrok here for free](https://ngrok.com/download) as a single-file binary for almost every operating system, or optionally sign up for an account to access additional features.

You can start a tunnel by providing a local port number on which your application runs. We will run our Java server on port 4567, so you can launch your tunnel with this command:

```
ngrok http 4567
```

After you've launched the tunnel, ngrok displays your temporary public URL along with some other information. We'll need that URL in a minute.

Another common tool for tunneling your local machine is [localtunnel.me](https://localtunnel.me/), which you can have a look at if you're facing problems with ngrok. It works in virtually the same way but requires you to install [NPM](https://www.npmjs.com/) first.

### Getting an Inbound Number

A requirement for receiving messages is a dedicated inbound number. Virtual mobile numbers look and work in a similar way to regular mobile numbers, however, instead of being attached to a mobile device via a SIM card, they live in the cloud and can process incoming SMS and voice calls. MessageBird offers numbers from different countries for a low monthly fee; [feel free to explore our low-cost programmable and configurable numbers](https://www.messagebird.com/en/numbers).

Purchasing a number is quite easy:

1. Go to the '[Numbers](https://dashboard.messagebird.com/en/numbers)' section in the left-hand side of your Dashboard and click the blue button '[Buy a number](https://dashboard.messagebird.com/en/vmn/buy-number)' in the top-right side of your screen.
2. Pick the country in which you and your customers are located, and make sure both the SMS and Voice capabilities are selected.
3. Choose one number from the selection and the duration for which you want to pay now.
4. Confirm by clicking 'Buy Number' in the bottom-right of your screen.
![Buy a number](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/buy-a-number.png)

Awesome, you’ve set up your first virtual mobile number! 🎉

One is enough for testing, but for real usage of the masked number system, you'll need a larger pool of numbers. Follow the same steps listed above to purchase more.

_Pro-Tip_: Check out our Help Center for more information about [virtual mobile numbers])https://support.messagebird.com/hc/en-us/sections/201958489-Virtual-Numbers and [country restrictions](https://support.messagebird.com/hc/en-us/sections/360000108538-Country-info-Restrictions).

## Connecting the Number to a Webhook for SMS

So you have a number now, but MessageBird has no idea what to do with it. That's why you need to define a _Flow_ next that links your number to your webhook. This is how you do it:

### Step one

Go to [Flow Builder](https://dashboard.messagebird.com/en/flow-builder) and choose the template 'Call HTTP endpoint with SMS' and click 'Try this flow'.

![Call HTTP with SMS](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/call-HTTP-with-SMS.png)

### Step two

This template has two steps. Click on the first step 'SMS' and select the number or numbers you’d like to attach the flow to. Now, click on the second step ‘Forward to URL’ and choose POST as the method; copy the output from the `ngrok` command in the URL and add `/webhook` at the end—this is the name of the route we use to handle incoming messages in our sample application. Click on 'Save' when ready.

![Forward to URL](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/Forward-to-URL.png)

### Step three

**Ready!** Hit 'Publish' on the right top of the screen to activate your flow. Well done, another step closer to building a customer support system for SMS-based communication!

![”Proxy](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/Proxy-for-SMS.png)

**Pro-Tip:** You can edit the name of the flow by clicking on the icon next to button 'Back to Overview' and pressing 'Rename flow'.

![Rename flow](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/rename-flow.png)

### Connecting the Number to a Webhook for Voice

Let’s set up a second flow for the same number to process incoming calls as well:

Step one
Go back to [Flow Builder](https://dashboard.messagebird.com/en/flow-builder) and hit the button ‘Create new flow’ and then ‘Create Custom Flow’.

![create a new flow](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/create-a-new-flow.png)

### Step two

Give your flow a name, choose '_Phone Call_' as the trigger and hit 'Next'.

![Buy a number](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/setup-new-flow.png)

### Step three

Click on the first step 'Phone Call' and select the number or numbers you’d like to attach the flow to.

### Step four

Add a new step by pressing the small '+', choose 'Fetch call flow from URL' and paste the same ngrok base URL into the form, but this time append `/webhook-voice` to it—this is the name of the route we use to handle incoming calls in our sample application. Click on 'Save' when ready.

![Proxy for Voice](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/Proxy-for-Voice.png)

### Step five

Ready! Hit ‘Publish’ on the right top of the screen to activate your flow.

**Pro-Tip:** You can edit the name of the flow by clicking on the icon next to button 'Back to Overview' and pressing 'Rename flow'.

![Rename flow](https://developers.messagebird.com/assets/images/screenshots/maskednumbers-node/rename-flow.png)

Awesome! 🎉

## Configuring the MessageBird SDK

The MessageBird SDK and an API key are not required to receive messages; however, since we want to send and forward messages, we need to add and configure it. The SDK is defined in `pom.xml` and loaded with a statement in `MaskedSMSAndVoice.java`:

``` java
// Create a MessageBirdService
final MessageBirdService messageBirdService = new MessageBirdServiceImpl("YOUR-API-KEY");
// Add the service to the client
final MessageBirdClient messageBirdClient = new MessageBirdClient(messageBirdService);
```

You need to provide a MessageBird API key via an environment variable loaded with [dotenv](https://mvnrepository.com/artifact/io.github.cdimascio/java-dotenv). We've prepared an `env.example` file in the repository, which you should rename to `.env` and add the required information. Here's an example:

```
MESSAGEBIRD_API_KEY=YOUR-API-KEY
```

You can create or retrieve a live API key from the [API access (REST) tab](https://dashboard.messagebird.com/en/developers/access) in the [Developers section](https://dashboard.messagebird.com/en/developers/access) of the MessageBird Dashboard.

## Creating our Data Model and Sample Data

Our BirdCar application uses a relational model; we have the following four entities:

* _Customers_, who have a name and a phone number.
* _Drivers_, who also have a name and a phone number.
* _Proxy_ Numbers, which are the phone numbers in our pool.
* _Rides_, which have a start, destination, and date and time. Every ride references precisely one _Customer_, _Driver_, and _Proxy Number_ through the use of foreign keys; every entity has a database table with an auto-incremented numeric ID as its primary key.

Open the file `CreateDB.rb` in the repository. It contains four CREATE TABLE queries to set up the data model. Below that, you'll find some INSERT INTO queries to add sample customers, drivers, and proxy numbers. Update those queries like this:

* Enter your name and mobile phone number as a customer.
* Enter another working phone number, such as a secondary phone or a friend's number, as a driver.
* Enter the virtual mobile number you purchased in the MessageBird Dashboard. If you have more than one, copy the query code for each.

After updating the file, save it and run the file through your IDE as a Java application.

Keep in mind that this command only works once so if you make changes and want to recreate the database, you must delete the file `ridesharing.db` that the script creates before re-running it.

## The Admin Interface

The `get("/")` route in `MaskedSMSAndVoice.java` and the associated HTML page in `views/admin.handlebars` implement a simple homepage that lists the content from the database and provides a form to add a new ride. For creating a ride, an admin can select a customer and driver from a drop-down, and enter start, destination, date and time; the form submits this information to /createride.

## Creating a Ride

The `post("/createride")` route defined in `MaskedSMSAndVoice.java` handles the following steps when creating a new ride:

### Getting Customer and Driver Information

The form fields contain only IDs for customer and driver, so we’ll make a query for each to find all the information that we need in subsequent steps:

``` java
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

```

## Finding a Number

We need to get a number from the pool that was never been assigned to a ride for the customer or the driver. To check this, let’s write a SQL query with two subqueries:

* Find all numbers for rides from the selected customer (subquery 1)
* Find all numbers for rides from the selected driver (subquery 2)
* Find all numbers that are in neither of those lists and return one of them (main query)

In Java and SQL, this check looks like this:

``` java
// Find a number that has not been used by the driver or the customer
String query = String.format("SELECT * FROM proxy_numbers \n"
        "WHERE id NOT IN (SELECT number_id FROM rides WHERE customer_id = %s) \n" +
                "AND id NOT IN (SELECT number_id FROM rides WHERE driver_id = %s)",
        customerID,
        driverID);
```

It's possible that no row was found; in that case, we alert the admin that the number pool is depleted and they should buy more numbers:

``` java
if (!proxyRow.next()) {
    return "No number available! Please extend your pool.";
}
```

### Storing the Ride

Once a number was found, that is, our query returned a row, it’s time to insert a new ride into the database using the information from the form:

``` java
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
```

### Notifying Customer and Driver

We should now send a message to both the customer and the driver to confirm the ride. This message should originate from the proxy number, so they can quickly reply to this message to reach the other party. For sending messages, the MessageBird SDK provides the `messageBirdClient.sendMessage` function. We need to call the function twice because we're sending two different versions of the message:

``` java
// Notify the customer
final List<BigInteger> customerPhone = new ArrayList<BigInteger>();
customerPhone.add(new BigInteger(proxyNumber));
messageBirdClient.sendMessage(proxyNumber, String.format("%s will pick you up at %s. Reply to this message to contact the driver.", driverName, req.queryParams("datetime")), customerPhone);

// Notify the driver
final List<BigInteger> driverPhone = new ArrayList<BigInteger>();
driverPhone.add(new BigInteger(proxyNumber));
messageBirdClient.sendMessage(proxyNumber, String.format("%s will wait for you at %s. Reply to this message to contact the customer.", customerName, req.queryParams("datetime")), driverPhone);

```

The response, or error, if any, is logged to the console, MessageBird doesn’t read or take any action based on them. In production applications you should definitely check if the messages were sent successfully.

## Receiving and Forwarding Messages

When a customer or driver replies to the message confirming their ride, the response should go to the other party. As we have instructed MessageBird to post to `/webhook`, we need to implement the `post("/webhook")` route.

First, we read the input sent from MessageBird. We're interested in three fields: originator, payload (the message text) and recipient (the virtual number to which the user sent their message), so that we can find the ride based on this information:

``` java
// Handle incoming messages
post("/webhook",
    (req, res) ->
    {
        // Read input sent from MessageBird
        String number = req.queryParams("originator");
        String text = req.queryParams("params");
        String proxy = req.queryParams("recipient");

```

### Looking up Receiver

To find the ride we use an SQL query which joins all four tables. We're interested in all entries in which the proxy number matches the `recipient` field from the webhook, and the `originator` matches _either_ the driver's number or the customer's number:

``` java
// Find a number that has not been used by the driver or the customer
String query = String.format("SELECT c.number AS customer_number, d.number AS driver_number, p.number AS proxy_number FROM rides r JOIN customers c ON r.customer_id = c.id JOIN drivers d ON r.driver_id = d.id JOIN proxy_numbers p ON p.id = r.number_id WHERE proxy_number = %s AND (driver_number = %s OR customer_number = %s",
    proxy,
    number, number);
ResultSet row = stmt.executeQuery(String.format(query));
```

After we've found the ride based on an _or-condition_, we need to check again which party was the actual sender and determine the recipient (the other party) from there:

```java
// Need to find out whether customer or driver sent this and forward to the other side
String recipient = number == row.getString(1) ? row.getString(2) : row.getString(1);

```

### Forwarding Message

We use `messageBirdClient.sendMessage` to forward the message. The proxy number is used as the originator, and we send the original text to the recipient as determined above:

``` java
// Forward the message through the MessageBird API
final List<BigInteger> recipientPhone = new ArrayList<BigInteger>();
recipientPhone.add(new BigInteger(recipient));
messageBirdClient.sendMessage(proxy, text, recipientPhone);
```

## Receiving and Forwarding Voice Calls

When a customer or driver calls the proxy number from which they received the confirmation, the system should transfer the call to the other party. As we have instructed MessageBird to fetch instructions from `/webhook-voice`, we need to implement the `get("/webhook-voice")` route. Keep in mind that unlike the SMS webhook, where we have configured POST, custom call flows are always retrieved with GET.

First, the input sent from MessageBird should be read because we're interested in the source and destination of the call so that we can find the ride based on this information:

``` java
// Handle incoming calls
get("/webhook-voice",
    (req, res) ->
    {
        // Read input sent from MessageBird
        String number = req.queryParams("source");
        String proxy = req.queryParams("destination");
```

As we will return a new call flow encoded in XML format, let’s set the response header accordingly:

``` java
// Answer will always be XML
res.type("application/xml");
```

### Looking up Receiver

This works exactly as described for the SMS webhooks; therefore, the SQL query and surrounding Ruby code are mostly a verbatim copy. If you’re extending the sample to build a production application, it could be a good idea to make a function as an abstraction around it to avoid duplicate code.

### Transferring call

To transfer the call, we return a short snippet of XML to MessageBird and also log the action to the console:

```java
return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> < Transfer destination =\"" + recipient + "\" mask=\"true\" />";
"""
```

The `<Transfer />` element takes two attributes: _destination_ indicates the number to transfer the call to—which we've determined as described above—and _mask_ instructs MessageBird to use the proxy number instead of the original caller ID.

If we don't find a ride, we return a different XML snippet with a `<Say />` element, which is used to read some instructions to the caller:

``` java
# Cannot match numbers
if (!row.next()) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Say language=\"en-GB\" voice=\"female\">Sorry, we cannot identify your transaction. Make sure you call in from the number you registered.</Say>";
}
```

This element takes two attributes, _language_ and _voice_, that define the configuration for speech synthesis. The text itself goes between the opening and closing XML element.

## Testing the Application

Make sure you’ve set up at least one number correctly with two flows to forward both incoming messages and incoming phone calls to an ngrok URL, and that the tunnel is still running. Keep in mind that whenever you start a fresh tunnel, you'll get a new URL, so you also have to update it in the flows accordingly.

To start the application, build and run the application through your IDE.

Open http://localhost:4567/ in your browser and create a ride between the customer and driver you configured in `CreateDB.java`. If everything works out correctly, two phones should receive a message. Reply to the incoming message on one phone and you'll receive this reply on the other phone, but magically coming from the proxy number. Wow!

If you didn't get the messages or the forwarding doesn't work, check the console output from Spark to see if there's any problem with the API—such as an incorrect API key or a typo in one of the numbers—and try again.

You can also test voice call forwarding as well: call the proxy number from one phone and magically see the other phone ring.

Use the flow, code snippets and UI examples from this tutorial as an inspiration to build your own application. Don't forget to download the code from the [MessageBird Developer Tutorials GitHub repository](https://github.com/messagebirdguides/masked-numbers-guide-java).

### Nice work! 🎉

You've just built your own number masking system with MessageBird!

## Start building!

Want to build something similar but not quite sure how to get started? Feel free to let us know at support@messagebird.com; we'd love to help!
