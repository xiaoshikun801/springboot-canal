package com.example.canal.server;

import com.alibaba.otter.canal.common.utils.AddressUtils;
import com.alibaba.otter.canal.deployer.CanalConstants;
import com.alibaba.otter.canal.deployer.CanalController;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

@Component
public class CustomCanalServerStart implements ApplicationRunner {
    static final Logger logger = LoggerFactory.getLogger(CustomCanalServerStart.class);
    @Override
    public void run(ApplicationArguments args){
        String ip = AddressUtils.getHostIp();
        Properties properties = getCanalProperties();
        properties.put(CanalConstants.CANAL_IP, ip);
        String port = properties.get("canal.port")+"";
        System.setProperty("canal.instance.mysql.slaveId", getUnique(ip, port));
        printToConsole("配置文件信息：" + properties);
        final CanalController controller = new CanalController(properties);
        try {
            controller.start();
        } catch (Throwable e1) {
            printToConsole("启动服务失败." + e1);
        }
        printToConsole("## the canal server is running now ......");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                printToConsole("## stop the canal server");
                controller.stop();
            } catch (Throwable e) {
                printToConsole("##something goes wrong when stopping canal Server:" + e);
            } finally {
                printToConsole("## canal server is down.");
            }
        }));
        printToConsole("canal Service started.");
    }


    private static Properties getCanalProperties() {
        Properties properties = new Properties();
        String conf = System.getProperty("canal.conf", "classpath:canal.properties");
        if (conf.startsWith("classpath:")) {
            conf = StringUtils.substringAfter(conf, "classpath:");
            try {
                properties.load(CanalServerApplication.class.getClassLoader().getResourceAsStream(conf));
            } catch (IOException e) {
                printToConsole("读取配置文件错误：" + conf + "\n" + e);
            }
        } else {
            try {
                properties.load(new FileInputStream(conf));
            } catch (FileNotFoundException e) {
                printToConsole("没有找到配置文件：" + conf);
            } catch (IOException e) {
                printToConsole("读取配置文件错误：" + conf + "\n" + e);
            }
        }
        return properties;
    }

    private static void printToConsole(String s) {
        if (null != s && s.length() > 0) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            logger.info("time:{},{}", time, s);
        } else {
            logger.info(s);
        }
    }

    private static String getUnique(String ip, String strPort) {
        final int prime = 31;
        long result = 1;
        result = prime * result + ((ip == null) ? 0 : ip.hashCode());
        result = prime * result + ((strPort == null) ? 0 : strPort.hashCode());
        printToConsole("计算后的slaveId:" + result);
        return String.valueOf(result);
    }
}
