package org.example;

import io.vertx.core.*;
import org.example.server.HTTPServer;
import org.example.cache.FileStatusTracker;
import org.example.poller.PingScheduler;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public final static Vertx vertx = Vertx.vertx();

    public static void main(String[] args)
    {
        try
        {
            ApplicationContextStore.read()
                    .compose(result -> FileStatusTracker.read())
                    .compose(result ->
                            vertx.deployVerticle(new HTTPServer())
                                    .compose(deployment ->
                                    {
                                        vertx.setPeriodic(Constants.FILE_STORE_INTERVAL, id ->
                                        {
                                            ApplicationContextStore.write();

                                            FileStatusTracker.write();
                                        });
                                        logger.info("HTTPServer deployed successfully.");

                                        return Future.succeededFuture();
                                    })
                    )
                    .compose(result -> vertx.deployVerticle(new PingScheduler()).compose(deployment ->
                            {
                                logger.info("PingScheduler deployed successfully.");

                                UI();

                                return Future.succeededFuture();
                            })
                    )
                    .onFailure(error -> logger.error("Error in application startup: ", error));
        }
        catch (Exception exception)
        {
            logger.error("Critical error in the main loop: ", exception);
        }
    }

    private static void UI()
    {
        new Thread(() ->
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

                            Main.vertx.eventBus().send("object.provision", ip);
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
        }).start();
    }
}
