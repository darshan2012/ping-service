package org.example;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

public class FileWriterService {

    private  FileSystem fileSystem = null;

    private String baseDir = "ping";

    public FileWriterService(Vertx vertx, String baseDir) {

        this.fileSystem = vertx.fileSystem();

        this.baseDir = baseDir;

    }

    public void writeToFile(String ip, String data) {

        String ipDirPath = baseDir + "/" + ip;

        ensureDirectoryExists(ipDirPath, res -> {

            if (res.succeeded()) {

                writePingDataToFile(ip, data);

            } else {

                System.err.println("Failed to ensure directory exists: " + res.cause().getMessage());

            }
        });
    }

    private void ensureDirectoryExists(String ipDirPath, Handler<AsyncResult<Void>> resultHandler) {

        fileSystem.exists(ipDirPath, existsResult -> {

            if (existsResult.succeeded()) {

                if (!existsResult.result()) {

                    fileSystem.mkdirs(ipDirPath, mkdirResult -> {

                        if (mkdirResult.succeeded()) {

                            resultHandler.handle(Future.succeededFuture());

                        } else {

                            resultHandler.handle(Future.failedFuture(new FileSystemException("Failed to create directory")));

                        }
                    });
                } else {

                    resultHandler.handle(Future.succeededFuture());

                }
            } else {

                resultHandler.handle(Future.failedFuture(existsResult.cause()));

            }
        });
    }

    private void writePingDataToFile(String ip, String data) {

        String filename = generateFileName(ip);

        fileSystem.writeFile(filename, Buffer.buffer(data), writeRes -> {

            if (writeRes.succeeded()) {

                System.out.println("host[" + ip + "] : Ping data for  successfully written to " + filename);

            } else {

                System.err.println("host["+ ip +"] : Failed to write data to file: " + writeRes.cause().getMessage());

            }
        });
    }

    private String generateFileName(String ip) {

        String timestamp = LocalDateTime.now().toString();

        return baseDir + "/" + ip + "/" + timestamp + ".txt";

    }
}
