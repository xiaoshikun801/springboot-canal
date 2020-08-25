package com.example.canal.client;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;


public class AbstractCanalClient {

    static final Logger logger = LoggerFactory.getLogger(AbstractCanalClient.class);
    private volatile boolean running = false;
    private Thread.UncaughtExceptionHandler handler = (t, e) -> logger.error("parse events has an error", e);
    private Thread thread = null;
    private CanalConnector connector;

    @Autowired
    ParseDataToElasticsearch parseDataToElasticsearch;

    protected void start() {
        Assert.notNull(connector, "connector is null");
        thread = new Thread(this::process);
        thread.setUncaughtExceptionHandler(handler);
        thread.start();
        running = true;
    }

    protected void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    protected void process() {
        int batchSize = 5 * 1024;
        while (running) {
            try {
                connector.connect();
                connector.subscribe();
                while (running) {
                    Message message = connector.getWithoutAck(batchSize);
                    if (message == null) {
                        continue;
                    }
                    long batchId = message.getId();
                    int size = message.getEntries().size();
                    if (batchId == -1 || size == 0) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                        }
                    } else {
                        parseDataToElasticsearch.parseData(message.getEntries());
                    }
                    connector.ack(batchId);
                }
            } catch (Throwable e) {
                logger.error("process error!", e);
            } finally {
                connector.disconnect();
            }
        }
    }


    public void setConnector(CanalConnector connector) {
        this.connector = connector;
    }

}
