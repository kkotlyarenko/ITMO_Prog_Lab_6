package main;

import main.command.Command;
import main.command.list.*;
import main.command.handler.CommandHandler;
import main.console.ConsoleWorker;
import main.console.BaseConsoleWorker;
import main.socket.Client;
import main.socket.Server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;

public class Main {
    public static Path ROOT_FILE;

    public static void main(String[] args) throws InterruptedException {
        String fileName = System.getenv("FILE_NAME");

        if (fileName == null || fileName.isEmpty()) {
            System.out.println("Enter the name of the loaded file as an environment variable like FILE_NAME=<filename>");
            System.exit(1);
        }

        ROOT_FILE = Paths.get(fileName);

        if (Files.notExists(ROOT_FILE)) {
            try {
                Files.createFile(ROOT_FILE);
                System.out.printf("File %s was successfully created%n", ROOT_FILE.getFileName());
            } catch (IOException e) {
                System.out.printf("Unable to create file! %s%n", e.getMessage());
                System.exit(1);
            }
        }

        List<Command> commands = List.of(
                new Info(),
                new Show(),
                new Add(),
                new UpdateById(),
                new RemoveById(),
                new Clear(),
//                new Save(),
                new ExecuteScript(),
//                new Exit(),
                new Head(),
                new AddIfMax(),
                new AddIfMin(),
                new RemoveAnyByDistance(),
                new CountLessThanDistance(),
                new PrintFieldAscendingDistance()
        );

        CommandHandler handler = new CommandHandler(commands);

        Server server = new Server(handler);

        new Thread(server).start();

    }
}
