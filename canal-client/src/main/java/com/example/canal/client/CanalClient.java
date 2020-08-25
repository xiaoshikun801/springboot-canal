package com.example.canal.client;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CanalClient extends AbstractCanalClient implements InitializingBean {
    @Value(value = "${canal.zkServers}")
    private String zkServers;
    @Value(value = "${canal.userName}")
    private String userName;
    @Value(value = "${canal.password}")
    private String password;
    @Value(value = "${canal.destination}")
    private String destination;

    @Override
    public void afterPropertiesSet() {
        CanalConnector connector = CanalConnectors.newClusterConnector(zkServers, destination, userName, password);
        setConnector(connector);
        start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("## stop the canal client");
                CanalClient.this.stop();
            } catch (Throwable e) {
                logger.warn("##something goes wrong when stopping canal:", e);
            } finally {
                logger.info("## canal client is down.");
            }
        }));
    }

}
