package com.vcasino.tests.model;

import com.google.gson.internal.LinkedTreeMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response {
    private LinkedTreeMap<String, ?> fields;

    public String get(String key) {
        return (String) fields.get(key);
    }

    public Integer getInt(String key) {
        Object v = fields.get(key);
        if (v != null) {
            return (int) v;
        }
        return null;
    }

    public Long getLong(String key) {
        Object v = fields.get(key);
        if (v != null) {
            return (long) v;
        }
        return null;
    }

    public Response getObject(String key) {
        Object v = fields.get(key);
        if (v instanceof LinkedTreeMap<?,?>) {
            return new Response((LinkedTreeMap<String, ?>) v);
        }
        return null;
    }
}
