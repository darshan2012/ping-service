package test;

import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonParser;
import org.zeromq.ZContext;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main
{
//    public static HashMap<String>
    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();
        System.out.println(Instant.now().getEpochSecond());
        Long time = System.currentTimeMillis();
        System.out.println(time);
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        System.out.println(System.currentTimeMillis() - time);

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
}
