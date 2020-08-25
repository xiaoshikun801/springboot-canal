package com.example.canal.client;

import com.alibaba.otter.canal.protocol.CanalEntry;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ParseDataToElasticsearch extends AbstractParseData {
    static final Logger logger = LoggerFactory.getLogger(ParseDataToElasticsearch.class);
    @Autowired
    RestHighLevelClient restHighLevelClient;

    @Value("${es.single.index}")
    private String singleIndex;

    private String joinIndex = "goods-search";
    private String parentTable = "dat_goods";
    private String childTable = "mall_goods_release";
    private String joinField = "goods_id";

    @Value("${es.shard.num}")
    private Integer shard;

    @Override
    public void parseData(List<CanalEntry.Entry> entryList) {
        List<List<Map<String, Object>>> doEntrys = new ArrayList<>();
        for (final CanalEntry.Entry entry : entryList) {
            List<Map<String, Object>> doEntry = super.doEntry(entry, entryList.size());
            if (Objects.nonNull(doEntry)) {
                if (singleIndex.equals(entry.getHeader().getTableName())) {
                    doEntrys.add(doEntry.stream().peek(map -> map.put("index", singleIndex)).collect(Collectors.toList()));
                } else if (entry.getHeader().getTableName().equals(parentTable)) {
                    doEntrys.add(doEntry.stream().peek(map -> {
                        map.put("index", joinIndex);
                        map.put("tableName", parentTable);
                    }).collect(Collectors.toList()));
                }else if(entry.getHeader().getTableName().equals(childTable)){
                    doEntrys.add(doEntry.stream().peek(map -> {
                        map.put("index", joinIndex);
                        map.put("tableName", childTable);
                        map.put("relations", joinField);
                    }).collect(Collectors.toList()));
                }
                logger.debug("数据库名称:{},表名:{},解析了{}行数据", entry.getHeader().getSchemaName(), entry.getHeader().getTableName(), doEntry.size());
            }
        }
        logger.info("解析了{}行数据", doEntrys.size());
        try {
            BulkResponse responses = restHighLevelClient.bulk(combinationBulk(doEntrys, shard), RequestOptions.DEFAULT);
            RestStatus status = responses.status();
            logger.info("responses status>>>{}", status);
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    public BulkRequest combinationBulk(List<List<Map<String, Object>>> doEntrys, Integer shard) {
        BulkRequest requests = new BulkRequest();
        requests.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        doEntrys.forEach(entry -> {
            for (Map<String, Object> m : entry) {
                String id = (String) m.get("_id");
                String index = m.get("index") + "";
                Object relations = m.get("relations");
                String tableName = m.get("tableName") + "";
                switch (m.get("OP_TYPE") + "") {
                    case "create":
                    case "update":
                        m.remove("OP_TYPE");
                        m.remove("_id");
                        m.remove("index");
                        m.remove("tableName");
                        m.remove("join_field");
                        m.remove("relations");
                        if(singleIndex.equals(index)){
                            requests.add(new IndexRequest(index).id(id).routing(rotatingHash(id, shard) + "").source(m));
                        }else if(joinIndex.equals(index)){
                            Map<String, String> map = new HashMap<>();
                            if (Objects.isNull(relations)) {
                                map.put("name", tableName);
                                m.put("join-field", map);
                                requests.add(new IndexRequest(index).id(id).routing(rotatingHash(id, shard) + "").source(m));
                            } else{
                                map.put("name", tableName);
                                map.put("parent", m.get(relations)+"");
                                m.put("join-field", map);
                                requests.add(new IndexRequest(index).id(id+m.get(relations)).routing(rotatingHash( m.get(relations)+ "", shard) + "").source(m));
                            }
                        }
                        break;
                    case "delete":
                        m.remove("OP_TYPE");
                        requests.add(new DeleteRequest(index).id(id));
                        break;
                }

            }
        });
        return requests;
    }

    public int rotatingHash(String key, int prime) {
        int hash, i;
        for (hash = key.length(), i = 0; i < key.length(); ++i)
            hash = (hash << 4) ^ (hash >> 28) ^ key.charAt(i);
        return (hash % prime);
    }

}
