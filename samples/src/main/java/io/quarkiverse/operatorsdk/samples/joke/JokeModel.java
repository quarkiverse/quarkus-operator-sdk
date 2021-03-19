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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JokeModel {
    /*
     * "error": true,
     * "internalError": false,
     * "code": 106,
     * "message": "No matching joke found",
     * "causedBy": [
     * "No jokes were found that match your provided filter(s)."
     * ],
     * "additionalInfo": "The specified ID range is invalid. Got: \"foo\" but max possible ID range is: \"0-303\".",
     * "timestamp": 1615998352457
     */

    public boolean error;
    public boolean internalError;
    public String message;
    public String[] causedBy;
    public String additionalInfo;
    public String category;
    public String joke;
    public Map<String, Boolean> flags;
    public int id;
    public boolean safe;
    public String lang;
}
