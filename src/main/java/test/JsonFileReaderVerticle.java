package test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonFileReaderVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(JsonFileReaderVerticle.class);

    @Override
    public void start() throws Exception
    {
        String filePath = "ping_data/20240923_1747.txt";  // Update with your file path

        vertx.fileSystem().open(filePath, new OpenOptions().setRead(true)).onComplete(fileResult ->
        {
            if (fileResult.succeeded())
            {
                var asyncFile = fileResult.result();
                final Buffer[] buffer = {Buffer.buffer()};

                asyncFile.handler(fileBuffer ->
                {
                    buffer[0].appendBuffer(fileBuffer);

                    String content = buffer[0].toString();
                    String[] lines = content.split("\n");
                    System.out.println("line length " + lines.length);
                    // Process all complete lines, leave the last incomplete part in buffer
                    for (int i = 0; i < lines.length - 1; i++)
                    {
                        processLine(lines[i]);
                    }

                    // Keep the last (potentially incomplete) line in the buffer
                    buffer[0] = Buffer.buffer(lines[lines.length - 1]);
                }).endHandler(v ->
                {
                    // Handle the last line (if any)
                    if (buffer[0].length() > 0)
                    {
                        processLine(buffer[0].toString());
                    }
                    logger.info("Completed reading the file.");
                }).exceptionHandler(error ->
                {
                    logger.error("Error reading file: ", error);
                });
            }
            else
            {
                logger.error("Failed to open file: {}", fileResult.cause().getMessage());
            }
        });
    }

    private void processLine(String line)
    {
        try
        {
            System.out.println("here");
            JsonObject json = new JsonObject(line);
            logger.info("Processed JSON: {}", json.encodePrettily());
            // Perform any processing on the json object here
        }
        catch (Exception e)
        {
            logger.error("Failed to parse line as JSON: {}", line, e);
        }
    }
}
