/**
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.quarkiverse.operatorsdk.samples.joke;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group(Joke.GROUP)
@Version(Joke.VERSION)
@JsonInclude(Include.NON_NULL)
public class Joke extends CustomResource<Void, Void> implements Namespaced {

    public static final String GROUP = "samples.javaoperatorsdk.io";
    public static final String VERSION = "v1alpha1";
    public static final String NAME = "jokes." + GROUP;
    private String joke;
    private String category;
    private boolean safe;
    private String lang;
    private int id;

    public Joke() {
    }

    public Joke(int id, String joke, String category, boolean safe, String lang) {
        this.id = id;
        getMetadata().setName("" + id);
        this.joke = joke;
        this.category = category;
        this.safe = safe;
        this.lang = lang;
    }

    public int getId() {
        return id;
    }

    public String getJoke() {
        return joke;
    }

    public void setJoke(String joke) {
        this.joke = joke;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isSafe() {
        return safe;
    }

    public void setSafe(boolean safe) {
        this.safe = safe;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
}
