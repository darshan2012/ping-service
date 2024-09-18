package org.example;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        WorkerExecutor executor = vertx.createSharedWorkerExecutor("ping-executor", 10, 2, TimeUnit.MINUTES);

        long interval = 60000;

        FileWriterService fileWriterService = new FileWriterService(vertx, "ping_logs");

        PingService pingService = new FpingService(fileWriterService,interval,10);

        while (true) {
            try {

                BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));

                System.out.print("Enter valid IP: ");

                String ip = inputReader.readLine();

                if (!IpValidator.isValidIp(ip)) {

                    System.out.println("Invalid IP[" + ip + "]");

                    continue;
                }

                if (isHostAlive(ip)) {

                    System.out.println("Host[" + ip + "] is up.");

                    System.out.print("Do you want to provision? [Y/N]: ");

                    String provision = inputReader.readLine();

                    if (provision.equalsIgnoreCase("y")) {

                        pingService.ping(ip, vertx, executor);

                    }
                } else {

                    System.out.println("Host[" + ip + "] is down.");

                }
            } catch (Exception exception) {
                System.out.println("Error: " + exception.getMessage());
            }
        }
    }

    private static boolean isHostAlive(String ip)
    {

        String str = null;
        try
        {

            ProcessBuilder builder = new ProcessBuilder("fping", ip);

            Process process = builder.start();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

            str = null;

            str = stdInput.readLine();

        } catch (Exception exception)
        {
            System.out.println("Error: " + exception.getMessage());

        }
        return str.equals(ip + " is alive");
    }
}
