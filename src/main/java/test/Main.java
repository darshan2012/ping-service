package test;

import io.vertx.core.Vertx;
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

        vertx.fileSystem().readDir("ping_data",".*\\.txt").onComplete(dirResult -> {

            if (dirResult.succeeded())
            {
                dirResult.result().forEach(file -> System.out.println(file));
            }
        });


    }
}
