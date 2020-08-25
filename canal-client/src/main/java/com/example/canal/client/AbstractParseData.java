package com.example.canal.client;

import com.alibaba.otter.canal.protocol.CanalEntry.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class AbstractParseData {
    static final Logger logger = LoggerFactory.getLogger(AbstractParseData.class);

    public abstract void parseData(List<Entry> entryList);

    List<Map<String, Object>> doEntry(Entry entry, int size) {
        List<Map<String, Object>> rowTurnedData = new ArrayList<>(size);
        if (entry.getEntryType() == EntryType.TRANSACTIONBEGIN || entry.getEntryType() == EntryType.TRANSACTIONEND) {
            return null;
        }
        if (entry.getEntryType() == EntryType.ROWDATA) {
            RowChange rowChage;
            try {
                rowChage = RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("parse event has an error , data:" + entry.toString(), e);
            }
            if (rowChage.getIsDdl()) {
                return null;
            }
            printXAInfo(rowChage.getPropsList());
            EventType eventType = rowChage.getEventType();
            for (RowData rowData : rowChage.getRowDatasList()) {
                rowTurnedData.add(doRowChage(eventType, rowData));
            }
        }
        return rowTurnedData;
    }

    private Map<String, Object> doRowChage(EventType eventType, RowData rowData) {
        if (eventType == EventType.UPDATE) {
            Map<String, Object> map = columnTurnedRow(rowData.getAfterColumnsList());
            map.put("OP_TYPE","update");
            return map;
        } else if (eventType == EventType.INSERT) {
            Map<String, Object> map = columnTurnedRow(rowData.getAfterColumnsList());
            map.put("OP_TYPE","create");
            return map;
        } else if (eventType == EventType.DELETE) {
            Map<String, Object> map = columnTurnedRow(rowData.getBeforeColumnsList());
            map.put("DELETE","delete");
            return map;
        }
        return null;
    }

    private Map<String, Object> columnTurnedRow(List<Column> columns) {
        Map<String, Object> rowData = new HashMap<>(200);
        for (Column column : columns) {
            if (column.getIsKey()) {
                rowData.put("_id", column.getValue());
                rowData.put(column.getName(), column.getValue());
            } else {
                rowData.put(column.getName(), column.getValue());
            }
        }
        return rowData;
    }


    private void printXAInfo(List<Pair> pairs) {
        if (pairs == null) {
            return;
        }
        String xaType = null;
        String xaXid = null;
        for (Pair pair : pairs) {
            String key = pair.getKey();
            if (StringUtils.endsWithIgnoreCase(key, "XA_TYPE")) {
                xaType = pair.getValue();
            } else if (StringUtils.endsWithIgnoreCase(key, "XA_XID")) {
                xaXid = pair.getValue();
            }
        }
        if (xaType != null && xaXid != null) {
            logger.info(" ------> " + xaType + " " + xaXid);
        }
    }
}
