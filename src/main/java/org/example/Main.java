package org.example;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.event.EventSender;
import org.example.event.FileManager;
import org.example.event.FileService;
import org.example.poll.Poller;
import org.example.poll.Scheduler;
import org.example.server.HTTPServer;
import org.example.cache.FileStatusTracker;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public final static Vertx vertx = Vertx.vertx();

    public final static ZContext zContext = new ZContext();

    public static void main(String[] args)
    {
        try
        {
            //setting up the directories if not exist
            vertx.fileSystem().mkdirsBlocking(Constants.BASE_DIR + "/data");

            ApplicationContextStore.read()
                    .compose(result -> vertx.deployVerticle(new FileService(), new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)))
                    .compose(result -> loadFiles())
                    .compose(result -> vertx.deployVerticle(new EventSender(Constants.IP, Constants.PORT)))
                    .compose(result -> vertx.deployVerticle(new HTTPServer()))
                    .compose(result ->
                    {
                        var applications = ApplicationContextStore.getApplications();

                        if (!applications.isEmpty())
                        {
                            applications.forEach(application -> vertx.deployVerticle(new FileManager(application), deployResult ->
                                    {
                                        if (deployResult.succeeded())
                                        {
                                            logger.info("file manager deployed successfully with deployment ID: {} for app {}", deployResult.result(),application);
                                        }
                                        else
                                        {
                                            logger.error("Failed to deploy file manager for app {}",application, deployResult.cause());
                                        }
                                    })
                            );
                        }

                        return Future.succeededFuture();
                    })
                    .compose(result -> vertx.deployVerticle(new Scheduler()))
                    .compose(result -> vertx.deployVerticle(new Poller()))
                    .compose(deployment ->
                    {
                        UI();

                        return Future.succeededFuture();
                    })
                    .compose(result ->
                    {
                        vertx.setPeriodic(Constants.FILE_STORE_INTERVAL, id ->
                        {
                            try
                            {
                                ApplicationContextStore.write();

                                FileStatusTracker.write();
                            }
                            catch (Exception exception)
                            {
                                logger.error(exception.getMessage(), exception);
                            }
                        });
                        return Future.succeededFuture();
                    })
                    .onFailure(error -> logger.error("Error in application startup: ", error));
        }
        catch (Exception exception)
        {
            logger.error("Critical error in the main loop: ", exception);
        }
    }

    private static Future<Void> loadFiles()
    {
        Promise<Void> promise = Promise.promise();

        try
        {
            vertx.executeBlocking(() ->
            {
                try
                {
                    if (!vertx.fileSystem().existsBlocking(Constants.FILE_STATUS_PATH))
                    {
                        logger.warn("File does not exist: {}", Constants.FILE_STATUS_PATH);

                        return Future.succeededFuture();
                    }

                    var buffer = Main.vertx.fileSystem().readFileBlocking(Constants.FILE_STATUS_PATH);

                    var lines = new JsonObject(buffer);

                    if (buffer.toString().equals("{}"))
                    {
                        return Future.succeededFuture();
                    }

                    lines.forEach(entry ->
                    {
                        try
                        {
                            Set<Constants.ApplicationType> appTypes = new HashSet<>();

                            ((JsonArray) entry.getValue()).forEach(
                                    app -> appTypes.add(Constants.ApplicationType.valueOf((String) app))
                            );

                            if(!appTypes.isEmpty())
                            {
                                FileStatusTracker.addFile(entry.getKey(), appTypes);

                                vertx.eventBus()
                                        .send(Constants.EVENT_OPEN_FILE, new JsonObject().put("file.name", entry.getKey()));
                            }
                        }
                        catch (Exception exception)
                        {
                            logger.error("Error while setting up file {}", entry.getKey());
                        }
                    });

                    return Future.succeededFuture();
                }
                catch (Exception exception)
                {
                    logger.error(exception.getMessage(), exception);

                    return Future.failedFuture(exception);
                }
            }).onComplete(result ->
            {
                if (result.succeeded())
                {
                    logger.info("{} file read successfully", Constants.FILE_STATUS_PATH);

                    promise.complete();
                }
                else
                {
                    logger.error("Error while reading file {}", Constants.FILE_STATUS_PATH, result.cause());

                    promise.fail(result.cause());
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("Error while opening the file {}", Constants.FILE_STATUS_PATH, exception);

            promise.fail(exception);
        }

        return promise.future();
    }

    private static void UI()
    {
        new Thread(() ->
        {
            try
            {
                logger.info("new thread for ui started");

                var inputReader = new BufferedReader(new InputStreamReader(System.in));

                while (true)
                {
                    try
                    {
                        System.out.print("Enter valid IP: ");

                        var ip = inputReader.readLine();

                        if (!Util.isValidIp(ip))
                        {
                            System.out.println("Invalid IP: " + ip);

                            continue;
                        }
                        System.out.println("Checking if host [ " + ip + "] is up...");

                        if (Util.isHostAlive(ip))
                        {
                            System.out.println("Host [ " + ip + " ] is up.");

                            System.out.print("Do you want to provision? [Y/N]: ");

                            var provision = inputReader.readLine();

                            if (provision.equalsIgnoreCase("y"))
                            {
                                System.out.println("Provisioning started for IP: " + ip);

                                Main.vertx.eventBus()
                                        .send(Constants.OBJECT_PROVISION, new JsonObject().put("ip", ip)
                                                .put("metric", "ping"));
                            }
                        }
                        else
                        {
                            System.out.println("Host [ " + ip + "] is down.");
                        }
                    }
                    catch (Exception exception)
                    {
                        logger.error("Error occurred while processing input: ", exception);
                    }
                }
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(), exception);
            }
        }).start();
    }
}
