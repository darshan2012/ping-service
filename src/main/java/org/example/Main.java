package org.example;

import org.example.event.FileStatusTracker;
import org.example.event.ServerVerticle;
import io.vertx.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public final static Vertx vertx = Vertx.vertx();

    public static void main(String[] args)
    {
        try
        {
            vertx.fileSystem().readDir("ping_data", ".*\\.txt$").onComplete(dirResult ->
            {
                try
                {
                    if (dirResult.succeeded())
                    {
                        List<String> files = dirResult.result();

                        if (files == null || files.isEmpty())
                        {
                            logger.warn("Directory is currently empty");

                            return;
                        }

                        Collections.sort(files);

                        String FILE_NAME_REGEX = ".*?(ping_data/.*\\.txt)$";
                        Pattern FILE_NAME_PATTERN = Pattern.compile(FILE_NAME_REGEX);

                        System.out.println(files);
                        for (String file : files)
                        {

                            var matcher = FILE_NAME_PATTERN.matcher(file);
                            if (matcher.find())
                            {
                                var extractedPath = matcher.group(1);
                                FileStatusTracker.addFile(extractedPath);
                                System.out.println(extractedPath);
                            }
                            else
                            {
                                logger.error("No match found for: " + file);
                            }
                        }
                        logger.info("Files added to FileStatusTracker");
                    }
                    else
                    {
                        logger.error("Failed to read directory: " + dirResult.cause().getMessage());
                    }
                }
                catch (Exception exception)
                {
                    logger.error(exception.getMessage(), exception);
                }
            });


            vertx.deployVerticle(new PingScheduler())
                    .compose(deployment ->
                    {
                        logger.info("PingScheduler deployed successfully.");
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

                                            Main.vertx.eventBus().send("ping.address", ip);
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
                        return Future.succeededFuture();
                    });


            vertx.deployVerticle(new ServerVerticle(), deployment ->
            {
                if (deployment.succeeded())
                {
                    logger.info("SeverVerticle deployed successfully.");
                }
                else
                {
                    logger.error("Failed to deploy SeverVerticle", deployment.cause());
                }
            });

        }
        catch (Exception exception)
        {
            logger.error("Critical error in the main loop: ", exception);
        }
    }


}
