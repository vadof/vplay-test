package com.vcasino.tests.model;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public Double getDouble(String key) {
        Object v = fields.get(key);
        if (v != null) {
            return (double) v;
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

    public boolean contains(String key) {
        return fields.get(key) != null;
    }

    public Boolean getBoolean(String key) {
        Object v = fields.get(key);
        if (v != null) {
            return (boolean) v;
        }
        return null;
    }

    public String getJson(String key) {
        Object v = fields.get(key);
        if (v != null) {
            return new Gson().toJson(v);
        }
        return null;
    }

    public <T> T get(String key, Class<T> clazz) {
        Object v = fields.get(key);
        if (v != null) {
            Gson gson = new Gson();
            return gson.fromJson(gson.toJson(v), clazz);
        }
        return null;
    }

    public <T> List<T> getList(String key, Class<T> clazz) {
        Object v = fields.get(key);
        if (v instanceof List<?>) {
            Gson gson = new Gson();
            List<?> rawList = (List<?>) v;

            List<T> result = new ArrayList<>();
            for (Object item : rawList) {
                T obj = gson.fromJson(gson.toJson(item), clazz);
                result.add(obj);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
