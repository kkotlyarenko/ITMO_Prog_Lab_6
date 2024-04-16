package main.socket;

import main.collection.CollectionManager;
import main.command.Command;
import main.console.ConsoleWorker;
import main.console.Request;
import main.console.Response;
import main.models.Coordinates;
import main.models.LocationFrom;
import main.models.LocationTo;
import main.models.Route;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Client implements Runnable, Serializable {
    private final ConsoleWorker consoleWorker;

    private Socket server;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;

    private int reconnectCount = 0;

    private List<Command> serverCommands = Collections.emptyList();

    public Client(ConsoleWorker consoleWorker) {
        this.consoleWorker = consoleWorker;
    }

    @Override
    public void run() {
        consoleWorker.print("Commmaands for managing connection:\n'reconnect' - reconnect to server\n'exit' - exit\n");

        consoleWorker.print("Try to load collection...");
        connect();

        String input;

        while ((input = consoleWorker.get("[%tl:%tM:%tS] ~ ")) != null) {
            try {
                handle(input);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handle(String input) throws Throwable {
        // check system commands
        switch (input) {
            case "reconnect": // reconnect to server
                connect();
                break;
            case "exit": // exit
                shutdownGracefully();
                System.exit(0);
                break;
            default: // send request and get response
                Request request = parseString(input);
                if (request == null) return;

                Response response = sendAndGet(request);

                if (response == null) {
                    consoleWorker.print("Server returned null response");
                    return;
                }

                serverCommands = Optional.ofNullable(response.getCommands()).orElse(Collections.emptyList());

                if (response.getText() != null) consoleWorker.print(response.getText());
                if (response.getRoutes() != null) {
                    response.getRoutes().forEach(route -> consoleWorker.print(route.toString()));
                }

                break;
        }
    }

    private Request parseString(String input) throws Throwable {
        Request request = new Request();

        while (input.contains("element")) {
            input = input.replaceFirst("element", "");
            Route inputRoute = inputRoute();

            request.getCollection().add(inputRoute);
        }

        String[] parts = input.trim().split(" ", 2);
        request.setCommand(parts[0]);

        int requiredElements = serverCommands.stream()
                .filter(command -> command.getName().equalsIgnoreCase(request.getCommand()))
                .map(Command::getElementsRequired).findAny().orElse(0);

        while (requiredElements-- > 0) {
            try {
                request.getCollection().add(inputRoute());
            } catch (Throwable t) {
                consoleWorker.print("Exiting command: " + t.getMessage());
                return null;
            }
        }

        if (parts.length == 2) request.setText(parts[1].trim());

        return request;
    }

    private Response sendAndGet(Request request) {
        sendRequest(request);
        try {
            return (Response) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            consoleWorker.error("Error receiving answer: " + e.getMessage());
            return null;
        }
    }

    private void sendRequest(Request request) {
        try {
            objectOutputStream.writeObject(request);
        } catch (IOException e) {
            consoleWorker.error("Error sending request: " + e.getMessage());
            consoleWorker.error("Use 'reconnect' to reconnect to server");
        }
    }

    private void shutdownGracefully() {
        try {
            if (objectInputStream != null) objectInputStream.close();
            if (objectOutputStream != null) objectOutputStream.close();
            if (server != null) server.close();

            objectInputStream = null;
            objectOutputStream = null;
            server = null;

            consoleWorker.print("\nConnection closed");
        } catch (IOException e) {
            consoleWorker.error("Error closing threads:" + e.getMessage());
        }
    }

    private void connect() {
        try {
            shutdownGracefully();

            server = new Socket("localhost", 8080);
            objectOutputStream = new ObjectOutputStream(server.getOutputStream());
            objectInputStream = new ObjectInputStream(server.getInputStream());

            Response connectResponse = sendAndGet(new Request("CONNECT"));

            Optional.ofNullable(connectResponse).ifPresent((presentResponse) ->
                    serverCommands = Optional.ofNullable(presentResponse.getCommands()).orElse(Collections.emptyList()));

            consoleWorker.print(String.format("Successfully connected to server (%s:%d)", "localhost", 8080));
        } catch (IOException e) {
            consoleWorker.error("Error connectiong to server. Reconnect in " + 10 + " seconds | Tries: " + reconnectCount + "/" + 5);
            shutdownGracefully();

            try {
                TimeUnit.SECONDS.sleep(10);

                if (reconnectCount++ > 5) {
                    System.exit(1);
                }

                connect();
            } catch (InterruptedException ex) {
                consoleWorker.error("Error waiting connection");
                return;
            }
        }
    }

    private Route inputRoute() throws Throwable {
        consoleWorker.print("New Route:");
        consoleWorker.skip();
        consoleWorker.print("Enter main information:");

        Route route = new Route();

        while(!input("name", route::setName, str->str));

        consoleWorker.skip();
        consoleWorker.print("Enter coordinates:");
        Coordinates coordinates = new Coordinates();
        while(!input("x", coordinates::setX, Long::parseLong));
        while(!input("y", coordinates::setY, Double::parseDouble));
        route.setCoordinates(coordinates);

        consoleWorker.skip();
        consoleWorker.print("Enter location from:");
        LocationFrom locationFrom = new LocationFrom();
        while(!input("x", locationFrom::setX, Long::parseLong));
        while(!input("y", locationFrom::setY, Integer::parseInt));
        while(!input("name", locationFrom::setName, str->str));
        route.setFrom(locationFrom);

        consoleWorker.skip();
        consoleWorker.print("Enter location to:");
        LocationTo locationTo = new LocationTo();
        while(!input("x", locationTo::setX, Long::parseLong));
        while(!input("y", locationTo::setY, Long::parseLong));
        while(!input("z", locationTo::setZ, Double::parseDouble));
        route.setTo(locationTo);

        while(!input("distance", route::setDistance, Long::parseLong));

        route.setId(CollectionManager.getInstance().getFreeId());

        return route;
    }

    private <K> boolean input(String fieldName, Consumer<K> setter, Function<String, K> parser, String line) throws Throwable {
        try {
            if (line.equals("return")) throw new Throwable("stop using return");

            setter.accept(parser.apply(line));
            return true;
        } catch (Exception ex) {
            consoleWorker.error(ex.getMessage());
            return false;
        }
    }

    private <K> boolean input(String fieldName, Consumer<K> setter, Function<String, K> parser) throws Throwable {
        try {
            String line = consoleWorker.get(" - " + fieldName);
            if (line.equals("return")) throw new Throwable("stop using return");

            setter.accept(parser.apply(line));
            return true;
        } catch (Exception ex) {
            consoleWorker.error(ex.getMessage());
            return false;
        }
    }
}
