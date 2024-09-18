package org.example;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FpingService implements PingService {

    private static final int DEFAULT_NO_OF_PACKETS=10;

    private static final long DEFAULT_INTERVAL = 60000;

    private FileWriterService fileWriterService;

    private long interval = DEFAULT_INTERVAL;

    private int noOfPackets = DEFAULT_NO_OF_PACKETS;

    public FpingService(FileWriterService fileWriterService, long interval, int noOfPackets) {

        this.fileWriterService = fileWriterService;

        this.interval = interval;

        this.noOfPackets = noOfPackets;

    }

    @Override
    public void ping(String ip, Vertx vertx, WorkerExecutor workerExecutor) {

        vertx.setPeriodic(interval, id -> {

            workerExecutor.<String>executeBlocking(future -> {

                try {

                    String output = executeFpingCommand(ip);
//                    System.out.println("hrere" + output);
                    if (output.contains("unreachable")) {

                        future.fail("Host[" + ip + "] is not reachable");

                    } else {

                        future.complete(output);

                    }

                } catch (Exception exception) {

                    future.fail(exception.getMessage());

                }

            }, res -> {

                if (res.succeeded()) {

                    processPingOutput(ip, res.result());

                } else {

                    System.err.println("Error during provisioning: " + res.cause().getMessage());

                }

            });
        });
    }

    private String executeFpingCommand(String ip)  {

        try
        {

            ProcessBuilder builder = new ProcessBuilder("fping", "-c", String.valueOf(noOfPackets), "-q", ip);

            Process process = builder.start();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();

            String str;

            while ((str = stdInput.readLine()) != null) {

                output.append(str).append("\n");

            }

            stdInput.close();

            return output.toString();

        } catch (Exception exception)
        {

            throw new RuntimeException(exception.getMessage());

        }

    }

    private void processPingOutput(String ip, String output) {

        String pingOutputRegex = ".* : xmt/rcv/%loss = (\\d+)/(\\d+)/(\\d+)%, min/avg/max = ([0-9.]+)/([0-9.]+)/([0-9.]+)";

        Matcher packetMatcher = Pattern.compile(pingOutputRegex).matcher(output);

        if (packetMatcher.find()) {

            StringBuilder data = new StringBuilder();

            data.append("Packets transmitted: ").append(packetMatcher.group(1)).append("\n")
                    .append("Packets received: ").append(packetMatcher.group(2)).append("\n")
                    .append("Packet loss: ").append(packetMatcher.group(3)).append("%\n")
                    .append("Minimum latency: ").append(packetMatcher.group(4)).append(" ms\n")
                    .append("Average latency: ").append(packetMatcher.group(5)).append(" ms\n")
                    .append("Maximum latency: ").append(packetMatcher.group(6)).append(" ms\n");

            fileWriterService.writeToFile(ip, data.toString());

        } else {

            System.err.println("No match found for ping output. Host may be down.");

        }
    }
}
