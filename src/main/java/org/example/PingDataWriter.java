package org.example;


import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import java.time.LocalDateTime;


public class PingDataWriter
{
    private FileSystem fileSystem;

    private String baseDir = "ping";

    public PingDataWriter(Vertx vertx, String baseDir)
    {
        this.fileSystem = vertx.fileSystem();

        this.baseDir = baseDir;
    }

    public PingDataWriter(Vertx vertx)
    {
        this.fileSystem = vertx.fileSystem();
    }

    public void writeToFile(String ip, String data)
    {
        var filename = baseDir + "/" + ip + "/" + LocalDateTime.now() + ".txt";

        fileSystem.open(filename, new OpenOptions().setCreate(true)).onComplete(result ->
        {
            try
            {
                if (result.succeeded())
                {
                    AsyncFile file = result.result();

                    file.write(Buffer.buffer(data));
                }

            } catch (Exception exception)
            {
                System.out.println(exception.getMessage());
            }
        });
    }
}
