package com.vcasino.tests.model;

import lombok.AllArgsConstructor;

import java.util.Map;

@AllArgsConstructor
public class Row {

    private Map<String, Object> row;

    public String get(String column) {
        return (String) row.get(column);
    }

}
