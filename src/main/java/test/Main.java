package test;

import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonParser;
import org.zeromq.ZContext;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main
{
//    public static HashMap<String>
    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

       vertx.deployVerticle(new JsonFileReaderVerticle());


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
