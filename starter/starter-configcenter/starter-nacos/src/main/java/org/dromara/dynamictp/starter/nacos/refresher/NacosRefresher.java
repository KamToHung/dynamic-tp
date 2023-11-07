/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.dynamictp.starter.nacos.refresher;

import cn.hutool.core.io.FileUtil;
import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dromara.dynamictp.common.em.ConfigFileTypeEnum;
import org.dromara.dynamictp.common.properties.DtpProperties;
import org.dromara.dynamictp.common.util.NacosUtil;
import org.dromara.dynamictp.core.refresher.AbstractRefresher;
import org.dromara.dynamictp.core.support.ThreadPoolCreator;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * NacosRefresher related
 *
 * @author yanhom
 * @since 1.0.0
 **/
@Slf4j
public class NacosRefresher extends AbstractRefresher implements InitializingBean, DisposableBean, Listener {

    private static final ThreadPoolExecutor EXECUTOR = ThreadPoolCreator.createCommonFast("nacos-listener");

    private ConfigFileTypeEnum configFileType;

    @NacosInjected
    private ConfigService configService;

    @Override
    public void afterPropertiesSet() {

        DtpProperties.Nacos nacos = dtpProperties.getNacos();
        ConfigFileTypeEnum deduceType = getConfigFileType(nacos.getDataId());
        configFileType = NacosUtil.getConfigType(dtpProperties, deduceType);
        String dataId = NacosUtil.deduceDataId(nacos, environment, configFileType);
        String group = NacosUtil.getGroup(nacos, "DEFAULT_GROUP");

        try {
            configService.addListener(dataId, group, this);
            log.info("DynamicTp refresher, add listener success, dataId: {}, group: {}", dataId, group);
        } catch (NacosException e) {
            log.error("DynamicTp refresher, add listener error, dataId: {}, group: {}", dataId, group, e);
        }
    }

    /**
     * 根据dataId后缀识别配置类型
     * @param dataId dataId
     * @return ConfigFileTypeEnum
     */
    private static ConfigFileTypeEnum getConfigFileType(String dataId) {
        String suffix = FileUtil.getSuffix(dataId);
        if (StringUtils.isBlank(suffix)) {
            return ConfigFileTypeEnum.PROPERTIES;
        }
        return ConfigFileTypeEnum.of(suffix);
    }

    @Override
    public Executor getExecutor() {
        return EXECUTOR;
    }

    @Override
    public void receiveConfigInfo(String content) {
        refresh(content, configFileType);
    }

    @Override
    public void destroy() {
        EXECUTOR.shutdown();
    }

}
