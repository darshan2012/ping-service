package org.example;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;


import java.time.LocalDateTime;
import java.util.Base64;


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

        var ipDirPath = baseDir + "/" + ip;

//        fileSystem.open( ip + "/" + LocalDateTime.now() + ".txt", new OpenOptions().setCreate(true))
//                .onComplete(result ->
//                {
//                    if (result.succeeded())
//                    {
//                        try
//                        {
//                            var asyncFile = result.result();
//                            asyncFile.write(Buffer.buffer(data));
//                        } catch (Exception exception)
//                        {
//                            System.out.println(exception.getMessage());
//                        }
//                    } else
//                    {
//                        System.err.println("Cannot open file " + result.cause());
//                    }
//                });
        ensureDirectoryExists(ipDirPath, res ->
        {

            if (res.succeeded())
            {

                writePingDataToFile(ip, data);

            } else
            {

                System.err.println("Failed to ensure directory exists: " + res.cause().getMessage());

            }
        });
    }

    private void ensureDirectoryExists(String ipDirPath, Handler<AsyncResult<Void>> resultHandler)
    {

        fileSystem.exists(ipDirPath, existsResult ->
        {

            if (existsResult.succeeded())
            {

                if (!existsResult.result())
                {

                    fileSystem.mkdirs(ipDirPath, mkdirResult ->
                    {

                        if (mkdirResult.succeeded())
                        {

                            resultHandler.handle(Future.succeededFuture());

                        } else
                        {

                            resultHandler.handle(
                                    Future.failedFuture(new FileSystemException("Failed to create directory")));

                        }
                    });
                } else
                {

                    resultHandler.handle(Future.succeededFuture());

                }
            } else
            {

                resultHandler.handle(Future.failedFuture(existsResult.cause()));

            }
        });
    }

    private void writePingDataToFile(String ip, String data)
    {

        var filename = baseDir + "/" + ip + "/" + LocalDateTime.now() + ".txt";

        fileSystem.writeFile(filename, Buffer.buffer(Base64.getEncoder().encodeToString(data.getBytes())), writeRes ->
        {

            if (writeRes.succeeded())
            {

                System.out.println("host[" + ip + "] : Ping data for  successfully written to " + filename);

            } else
            {

                System.err.println("host[" + ip + "] : Failed to write data to file: " + writeRes.cause().getMessage());

            }
        });
    }
}
