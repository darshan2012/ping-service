package test;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.Json;
import org.example.event.ApplicationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MapToFileExample {

    private static final Logger logger = LoggerFactory.getLogger(MapToFileExample.class);
    private static final Vertx vertx = Vertx.vertx();

    public static void main(String[] args) {
        // Example ConcurrentHashMap
        ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>> map = new ConcurrentHashMap<>();
        HashMap<ApplicationType, Boolean> statusMap = new HashMap<>();
        statusMap.put(ApplicationType.PRIMARY, true);
        statusMap.put(ApplicationType.SECONDARY, false);
        statusMap.put(ApplicationType.FAILOVER, true);
        map.put("exampleKey", statusMap);

        // Convert ConcurrentHashMap to a JSON-compatible structure (using strings as keys)
        Map<String, Map<String, Boolean>> jsonCompatibleMap = convertMap(map);

        // Convert the JSON-compatible map to a JSON string
        String jsonString = Json.encodePrettily(jsonCompatibleMap);

        // File name
        String fileName = "map_data.json";

        // Write JSON string to file
        writeMapToBinaryFile("data/map.bin",map).onComplete(r -> {
            if (r.succeeded())
            {
                System.out.println("success map ");
            }
            else
            {
                System.out.println("failure map");
            }
        });

        readMapFromBinaryFile("data/map.bin");
    }

    public static Future<ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>>> readMapFromBinaryFile(String fileName) {
        Promise<ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>>> promise = Promise.promise();

        vertx.executeBlocking(future -> {
            try (FileInputStream fis = new FileInputStream(fileName);
                 ObjectInputStream ois = new ObjectInputStream(fis)) {

                ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>> map = (ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>>) ois.readObject();
                future.complete(map);
            } catch (IOException | ClassNotFoundException e) {
                future.fail(e);
            }
        }, result -> {
            if (result.succeeded()) {
                logger.info("Map successfully read from file.");
                System.out.println(result.result());
                promise.complete((ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>>) result.result());
            } else {
                logger.error("Failed to read map from file.", result.cause());
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }


    public static Future<Void> writeMapToBinaryFile(String fileName, ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>> map) {
        Promise<Void> promise = Promise.promise();

        vertx.executeBlocking(future -> {
            try (FileOutputStream fos = new FileOutputStream(fileName);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {

                oos.writeObject(map); // Serialize the map
                future.complete();
            } catch (IOException e) {
                future.fail(e);
            }
        }, result -> {
            if (result.succeeded()) {
                logger.info("Map successfully written to file.");
                promise.complete();
            } else {
                logger.error("Failed to write map to file.", result.cause());
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }


    private static Map<String, Map<String, Boolean>> convertMap(ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        e -> e.getKey().name(), // Convert ApplicationType enum to String
                                        Map.Entry::getValue
                                ))
                ));
    }

    private static void writeFile(String data, String fileName) {
        OpenOptions options = new OpenOptions().setCreate(true).setWrite(true);

        vertx.fileSystem().open(fileName, options, result -> {
            if (result.succeeded()) {
                var asyncFile = result.result();
                asyncFile.write(io.vertx.core.buffer.Buffer.buffer(data), writeResult -> {
                    if (writeResult.succeeded()) {
                        logger.info("Map data written to file successfully.");
                        asyncFile.close();
                    } else {
                        logger.error("Failed to write map data to file: ", writeResult.cause());
                    }
                });
            } else {
                logger.error("Failed to open file: ", result.cause());
            }
        });
    }
}
