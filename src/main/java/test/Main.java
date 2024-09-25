package test;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.streams.ReadStream;
import org.example.event.ApplicationType;
import org.zeromq.ZContext;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main
{
    public static Vertx vertx = Vertx.vertx();

    //    public static HashMap<String>
    public static void main(String[] args)
    {
        String a = "file name adffa";
        String[] splited = a.split("\\s+", 2);
        System.out.println(splited[0] + splited[1]);
//
//        Map<ApplicationType, JsonObject> contexts = new HashMap<>();
//
//        contexts.put(ApplicationType.SECONDARY, new JsonObject().put("bad", "akdf").put("alsd", 1212));
//        contexts.put(ApplicationType.PRIMARY, new JsonObject().put("bad", "akdf").put("alsd", 1212));
////        writeContextMapToFileAsync("d.txt",contexts);
//        readContextMapFromFileAsync("d.txt");
//        vertx.fileSystem().open("newHashmap.txt",new OpenOptions().setCreate(true)).onSuccess(file -> {
//        });
//        System.out.println(Instant.now().getEpochSecond());
//        Long time = System.currentTimeMillis();
//        System.out.println(time);
//        try
//        {
//            Thread.sleep(1000);
//        }
//        catch (InterruptedException e)
//        {
//            throw new RuntimeException(e);
//        }
//        System.out.println(System.currentTimeMillis() - time);

//        String str = "Hello I'm your String";
//        String[] splited = str.split("\\s+");
//        System.out.println(splited[0] + splited[1]);
//        HashMap<String,Integer> m = new HashMap<>();
//        m.put("a",10);
//        var a = m.get("b");
//        System.out.println(a + m.get("b"));

//        JsonObject json = new JsonObject().put("ip",1).put("port",2);
//        System.out.println(json);
//        json.put("ip",22);
//        System.out.println(json.getString("aa"));
//        System.out.println(json);
//        vertx.fileSystem().readDir("ping_data",".*\\.txt").onComplete(dirResult -> {
//
//            if (dirResult.succeeded())
//            {
//                dirResult.result().forEach(file -> System.out.println(file));
//            }
//        });


    }

    public static Future<Map<ApplicationType, JsonObject>> readContextMapFromFileAsync(String fileName)
    {
        Promise<Map<ApplicationType, JsonObject>> promise = Promise.promise();

        vertx.executeBlocking(future ->
        {
            vertx.fileSystem().readFile(fileName, result ->
            {
                if (result.succeeded())
                {
                    try
                    {
                        String jsonString = result.result().toString();
                        // Deserialize the file content to a map (Map<String, Object>)
                        Map<String, Object> jsonMap = Json.decodeValue(jsonString, Map.class);

                        // Convert the string keys back to ApplicationType enum and LinkedHashMap to JsonObject
                        Map<ApplicationType, JsonObject> contexts = new HashMap<>();
                        jsonMap.forEach((key, value) ->
                        {
                            // Convert the value (LinkedHashMap) into JsonObject
                            JsonObject jsonObject = new JsonObject((Map<String, Object>) value);
                            contexts.put(ApplicationType.valueOf(key), jsonObject);
                        });

                        future.complete(contexts);
                    }
                    catch (Exception e)
                    {
                        future.fail(e);
                    }
                }
                else
                {
                    future.fail(result.cause());
                }
            });
        }, false, result ->
        {
            if (result.succeeded())
            {
                System.out.println("Map successfully read from file.");
                System.out.println(result.result());
                promise.complete((Map<ApplicationType, JsonObject>) result.result());
            }
            else
            {
                System.err.println("Failed to read map from file: " + result.cause().getMessage());
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }


    public static Future<Void> writeContextMapToFileAsync(String fileName, Map<ApplicationType, JsonObject> contexts)
    {
        Promise<Void> promise = Promise.promise();

        vertx.executeBlocking(future ->
        {
            try
            {
                // Convert the map to a JSON-serializable structure
                Map<String, JsonObject> jsonMap = new HashMap<>();

                contexts.forEach((key, value) ->
                {
                    jsonMap.put(key.name(), value);
                });

                // Convert to JSON string
                String jsonString = Json.encodePrettily(jsonMap);

                // Write to file asynchronously
                vertx.fileSystem().writeFile(fileName, Buffer.buffer(jsonString), result ->
                {
                    if (result.succeeded())
                    {
                        future.complete();
                    }
                    else
                    {
                        future.fail(result.cause());
                    }
                });
            }
            catch (Exception e)
            {
                future.fail(e);
            }
        }, false, result ->
        {
            if (result.succeeded())
            {
                System.out.println("Map successfully written to file.");
                promise.complete();
            }
            else
            {
                System.err.println("Failed to write map to file: " + result.cause().getMessage());
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }
}
