package com.vcasino.tests.model;

import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;

@AllArgsConstructor
public class Row {

    private Map<String, Object> row;

    public String get(String column) {
        Object o = row.get(column);
        if (o == null) return null;
        return (String) o;
    }

    public Long getLong(String column) {
        Object o = row.get(column);
        if (o == null) return null;
        return (Long) o;
    }

    public Integer getInt(String column) {
        Object o = row.get(column);
        if (o == null) return null;
        return (Integer) o;
    }

    public Boolean getBoolean(String column) {
        Object o = row.get(column);
        if (o == null) return null;
        return (Boolean) o;
    }

    public BigDecimal getBigDecimal(String column) {
        Object o = row.get(column);
        if (o == null) return null;
        return (BigDecimal) o;
    }

    public Timestamp getTimestamp(String column) {
        Object o = row.get(column);
        if (o == null) return null;
        return (Timestamp) o;
    }
}
