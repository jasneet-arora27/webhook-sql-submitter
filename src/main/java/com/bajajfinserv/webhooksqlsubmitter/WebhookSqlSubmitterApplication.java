package com.bajajfinserv.webhooksqlsubmitter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class WebhookSqlSubmitterApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookSqlSubmitterApplication.class, args);
    }

    // This will allow us to make API calls (POST, GET, etc.)
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
