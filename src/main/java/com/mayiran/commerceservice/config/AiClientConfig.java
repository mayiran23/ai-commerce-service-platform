package com.mayiran.commerceservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 调用 Python AI 服务的 HTTP 客户端配置。
 *
 *   为什么单独配成 Bean：
 *     1. RestClient 内部带连接池，全局建一次复用即可；
 *     2. 超时只能在这里配 —— 最容易被忽略、又最容易出事的地方。
 *   超时怎么定：
 *     - 连接超时 3 秒：Python 就在本机，连不上说明它压根没启动，等久了没意义；
 *     - 读取超时 20 秒：AI 那边要跑两轮大模型 + 一次查库（实测约 3.7 秒），留足余量。
 *       但不能不设 —— 不设的话 Python 卡死时 Java 线程会一直挂着，请求一多线程池就被拖垮。
 */
@Configuration
public class AiClientConfig {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Bean
    public RestClient aiRestClient(){
        SimpleClientHttpRequestFactory factory =new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(factory)
                .build();
    }
}
