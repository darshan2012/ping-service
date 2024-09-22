package org.example;

import event.ServerVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static Vertx vertx = Vertx.vertx(new VertxOptions());

    public static WorkerExecutor pingExecutor = vertx.createSharedWorkerExecutor("ping-executor", 10, 2,
            TimeUnit.MINUTES);

    public static void main(String[] args)
    {
        try
        {
            var interval = 60000L;

            var baseDir = "ping_logs";

            var pingScheduler = new PingScheduler(interval, 10, baseDir);

            var inputReader = new BufferedReader(new InputStreamReader(System.in));

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

            while (true)
            {
                try
                {

                    System.out.print("Enter valid IP: ");

                    String ip = inputReader.readLine();

                    if (!Util.isValidIp(ip))
                    {
                        logger.warn("Invalid IP: {}", ip);

                        continue;
                    }

                    if (isHostAlive(ip))
                    {
                        logger.info("Host [{}] is up.", ip);

                        System.out.print("Do you want to provision? [Y/N]: ");

                        String provision = inputReader.readLine();

                        if (provision.equalsIgnoreCase("y"))
                        {

                            logger.info("Provisioning started for IP: {}", ip);

                            pingScheduler.ping(ip);
                        }
                    }
                    else
                    {
                        logger.info("Host [{}] is down.", ip);
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
            logger.error("Critical error in the main loop: ", exception);
        }
    }

    private static boolean isHostAlive(String ip)
    {
        logger.info("Checking if host [{}] is up...", ip);
        try
        {
            String pingOutput = Util.executeCommand("fping", "-c", "5", "-q", ip);

            if (pingOutput.isEmpty())
            {
                logger.warn("No response from host [{}].", ip);

                return false;
            }

            var packetLossMatcher = Util.PING_OUTPUT_PATTERN.matcher(pingOutput);

            if (packetLossMatcher.find())
            {
                int packetLossPercent = Integer.parseInt(packetLossMatcher.group(3));

                logger.debug("Packet loss for IP [{}] is {}%.", ip, packetLossPercent);

                return packetLossPercent < 50;
            }
            else
            {
                logger.warn("No packet loss information for IP [{}].", ip);

                return false;
            }
        }
        catch (Exception exception)
        {
            logger.error("Error while checking host [{}]: {}", ip, exception.getMessage());

            return false;
        }
    }
}
