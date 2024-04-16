package main.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import main.Main;
import main.collection.CollectionManager;
import main.command.handler.CommandHandler;
import main.console.Request;
import main.console.Response;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public class Server implements Runnable, Serializable {
    // log
    Logger log = LoggerFactory.getLogger(this.getClass());

    // chat handler
    private final CommandHandler handler;

    //console worker
    BufferedInputStream consoleInputStream = new BufferedInputStream(System.in);
    BufferedReader consoleReader = new BufferedReader(new InputStreamReader(consoleInputStream));

    public Server(CommandHandler handler) {
        this.handler = handler;
        init();
    }

    /**
     * Initialize server
     */
    private void init() {
        // try to load collection
        CollectionManager.getInstance().getCollection();

        // check file permissions
        if (!Files.isWritable(Main.ROOT_FILE))
            log.warn("file {} is not writable", Main.ROOT_FILE.getFileName());
        if (!Files.isReadable(Main.ROOT_FILE))
            log.warn("file {} is not readable", Main.ROOT_FILE.getFileName());
    }

    /**
     * Run server
     */
    @Override
    public void run() {
        try (ServerSocketChannel server = (ServerSocketChannel) ServerSocketChannel.open().bind(new InetSocketAddress(8080)).configureBlocking(false)) {
            log.info("server started on port {}", 8080);

            while (server.isOpen()) {
                if (consoleReader.ready()) {
                    String command = consoleReader.readLine();
                    if (command.equalsIgnoreCase("exit")) {
                        CollectionManager.getInstance().save(Main.ROOT_FILE);
                        log.info("server stopped");
                        System.exit(0);
                    }
                    if (command.equalsIgnoreCase("save")) {
                        CollectionManager.getInstance().save(Main.ROOT_FILE);
                        log.info("collection saved");
                    }
                }

                try (SocketChannel client = server.accept()) {
                    if (client == null) {
                        TimeUnit.MILLISECONDS.sleep(1000);
                        continue;
                    }

                    ObjectInputStream objectInputStream = new ObjectInputStream(client.socket().getInputStream());
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(client.socket().getOutputStream());


                    Request request = (Request) objectInputStream.readObject();
                    log.info("request from client({}): {}", client.socket().getInetAddress(), request.toString());

                    if (request.getCommand().equalsIgnoreCase("CONNECT")) {
                        log.info("connect request from client({})", client.socket().getInetAddress());
                        Response connectResponse = new Response("CONNECTED");
                        connectResponse.setCommands(handler.getCommands());

                        objectOutputStream.writeObject(connectResponse);
                        continue;
                    }

                    Response response = handleClient(request);
                    log.info("response for client({}): {}", client.socket().getInetAddress(), response.toString());

                    objectOutputStream.writeObject(response);

                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private Response handleClient(Request request) {
        return handler.handle(request);
    }

}
