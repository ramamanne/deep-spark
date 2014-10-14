/*
 * Copyright 2014, Stratio.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.stratio.deep.commons.config;

import java.io.Serializable;

import org.apache.hadoop.conf.Configuration;

/**
 * Created by rcrespo on 13/10/14.
 */
public class HadoopConfig<T> extends DeepJobConfig<T> implements Serializable {

    protected transient Configuration configHadoop;


    public HadoopConfig(Class<T> t) {
        super(t);
    }

    public Configuration getHadoopConfiguration() {
        return configHadoop;
    }

    public void setHadoopConfiguration(Configuration configHadoop) {
        this.configHadoop = configHadoop;
    }
}
