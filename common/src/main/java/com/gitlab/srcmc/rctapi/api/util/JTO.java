/*
    * This file is part of Radical Cobblemon Trainers API.
    * Copyright (c) 2025, HDainester, All rights reserved.
    *
    * Radical Cobblemon Trainers API is free software: you can redistribute it and/or modify
    * it under the terms of the GNU Lesser General Public License as published by
    * the Free Software Foundation, either version 3 of the License, or
    * (at your option) any later version.
    *
    * Radical Cobblemon Trainers API is distributed in the hope that it will be useful, but
    * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
    * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
    * more details.
    *
    * You should have received a copy of the GNU Lesser General Public License along
    * with Radical Cobblemon Trainers API. If not, see <http://www.gnu.org/licenses/lgpl>.
    */
package com.gitlab.srcmc.rctapi.api.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.gitlab.srcmc.rctapi.ModCommon;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Utility class to parse generic json objects on demand. An instance of JTO can be
 * parsed directly from json (e.g. with {@link Gson}). A JTO json object is
 * expected to have following format:
 * <pre>
 *{
 *  "type": "some-type",
 *  "data": { ... }
 *}
 * </pre>
 * Where {@code type} might name any registered parser (see {@link JTO#registerParser(String, Function)})
 * and {@code data} is a json object that will be supplied (as {@link JsonObject}) to the json parser
 * ({@code data} may be omitted in which case {@code null} will be supplied).
 */
public class JTO<T> implements Serializable {
    private static final long serialVersionUID = 0L;
    private static final Map<TypeToken<?>, Map<String, Parser<?>>> PARSERS = new HashMap<>();
    private static final Gson GSON = new Gson();

    private String type;
    private JsonObject data;
    private transient T target;
    private transient Supplier<T> func;

    protected JTO() {
        this.data = new JsonObject();
        this.type = "";
        this.init();
    }

    private void init() {
        this.func = () -> {
            var tt = TypeToken.of(Wrapper.<T>clazz());
            var parsers = JTO.PARSERS.getOrDefault(tt, Map.of());

            @SuppressWarnings("unchecked")
            var parser = (Parser<T>)parsers.get(this.type);

            if(parser != null) {
                this.target = parser.func.apply(this.data);
                this.func = () -> this.target;
            } else {
                ModCommon.LOG.error(String.format("No JTO parser registered for type '%s'", this.type));
            }

            return this.target;
        };
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeObject(this.type);
        oos.writeObject(this.data.toString());
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        this.type = (String)ois.readObject();
        this.data = JsonParser.parseString((String)ois.readObject()).getAsJsonObject();
        this.init();
    }

    /**
     * Parses this JTO into an object of the target type and returns it. If the object
     * has been parsed before the same reference is returned. Returns null if no
     * appropriate parser could be found (see {@link JTO#registerParser(String,
     * Function)}).
     *
     * @return Target object.
     */
    public T get() {
        return this.func.get();
    }

    /**
     * Registers a json parser for the provided type name and generic type. The type
     * name is case insensitive.
     *
     * @param <T> Generic type to register a parser for.
     * @param type Type name of the parser.
     * @param func Parser function.
     * @throws IllegalArgumentException If a parser with the given type name was
     * already registered for the given generic type.
     */
    public static <T> void registerParser(String type, Function<JsonObject, T> func) {
        type = type.toLowerCase();

        var tt = TypeToken.of(Wrapper.<T>clazz());
        var parsers = JTO.PARSERS.computeIfAbsent(tt, key -> new HashMap<>());

        if(parsers.containsKey(type)) {
            throw new IllegalArgumentException("A parser for the type '" + type + "' is already registered");
        }

        parsers.put(type, new Parser<>(func));
    }

    /**
     * Registers a json parser for the provided type name and generic model type in
     * addition to a converter, from the model type to the given generic target type.
     *
     * @param <Model> Generic model type targeted by the parser.
     * @param <T> Generic type to register a parser for.
     * @param type Type name of the parser.
     * @param converter Converter function to create an instance of the target type from a model.
     * @param defaultModel Supplier for a default model instance.
     * @param clazz Class instance of the model type.
     * @throws IllegalArgumentException If a parser with the given type name was
     * already registered for the given generic target type.
     */
    public static <Model, T> void registerParser(String type, Function<Model, T> converter, Supplier<Model> defaultModel, Class<Model> clazz) {
        type = type.toLowerCase();

        JTO.registerParser(type, jso -> {
            var model = jso != null
                    ? GSON.fromJson(jso, clazz)
                    : defaultModel.get();

            return converter.apply(model);
        });
    }

    /**
     * Creates a default JTO instance for the given generic type.
     *
     * @param <T> Generic type provided by this JTO.
     * @param supplier Supplier to provide an instance of the target type.
     * @return New JTO instance.
     */
    public static <T> JTO<T> of(Supplier<T> supplier) {
        var jto = new JTO<T>();
        jto.func = supplier;
        return jto;
    }

    private static class Parser<T> {
        public final Function<JsonObject, T> func;

        Parser(Function<JsonObject, T> func) {
            this.func = func;
        }
    }

    private static class Wrapper<T> {
        static <T> Class<?> clazz() {
            var dummy = new Wrapper<>();
            return dummy.getClass();
        }
    }
}
