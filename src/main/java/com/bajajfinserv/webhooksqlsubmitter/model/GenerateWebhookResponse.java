package com.bajajfinserv.webhooksqlsubmitter.model;

import lombok.Data;

@Data
public class GenerateWebhookResponse {
    private String webhook;
    private String accessToken;
}
