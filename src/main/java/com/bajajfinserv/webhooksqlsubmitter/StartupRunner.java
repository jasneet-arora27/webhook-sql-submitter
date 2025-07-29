package com.bajajfinserv.webhooksqlsubmitter;

import com.bajajfinserv.webhooksqlsubmitter.model.GenerateWebhookRequest;
import com.bajajfinserv.webhooksqlsubmitter.model.GenerateWebhookResponse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class StartupRunner implements CommandLineRunner {

    private static final String GENERATE_URL =
            "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    // ---- Update these three values if needed ----
    private static final String CANDIDATE_NAME  = "Jasneet Arora";
    private static final String CANDIDATE_REGNO = "REG12347";
    private static final String CANDIDATE_EMAIL = "jasneetdpsc@gmail.com";
    // --------------------------------------------

    private final RestTemplate restTemplate;

    public StartupRunner(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // 1) Generate webhook + token
            System.out.println("\n=== Generating webhook & token ===");

            GenerateWebhookRequest req = new GenerateWebhookRequest();
            req.setName(CANDIDATE_NAME);
            req.setRegNo(CANDIDATE_REGNO);
            req.setEmail(CANDIDATE_EMAIL);

            ResponseEntity<GenerateWebhookResponse> genResp =
                    restTemplate.postForEntity(GENERATE_URL, req, GenerateWebhookResponse.class);

            if (!genResp.getStatusCode().is2xxSuccessful() || genResp.getBody() == null) {
                throw new IllegalStateException("Failed to generate webhook/token. Status = " + genResp.getStatusCode());
            }

            String webhook = genResp.getBody().getWebhook();
            String accessToken = genResp.getBody().getAccessToken();

            System.out.println("Webhook URL : " + webhook);
            System.out.println("Access Token: " + mask(accessToken));

            // 2) Build final SQL based on regNo last two digits
            String finalQuery = buildSqlForRegNo(CANDIDATE_REGNO);

            // 3) Submit finalQuery with header Authorization = <token> (NO 'Bearer ')
            System.out.println("\n=== Submitting final query ===");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", accessToken); // Important: NO "Bearer " prefix

            Map<String, String> payload = Map.of("finalQuery", finalQuery);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

            System.out.println("POST " + webhook);
            System.out.println("Headers: [Content-Type: application/json, Authorization: <JWT>]");
            System.out.println("Body   : " + payload);

            ResponseEntity<String> submitResp =
                    restTemplate.exchange(webhook, HttpMethod.POST, entity, String.class);

            System.out.println("\n=== Submission Result ===");
            System.out.println(submitResp.getStatusCode());
            System.out.println("Body: " + submitResp.getBody());

            System.out.println("\n=== Flow completed successfully ===");

        } catch (HttpClientErrorException e) {
            System.err.println("\n=== Error during startup flow ===");
            System.err.println(e.toString());
        } catch (Exception e) {
            System.err.println("\n=== Error during startup flow ===");
            e.printStackTrace();
        }

        // Exit only when running the JAR with -Dexit.after.run=true
        if (Boolean.getBoolean("exit.after.run")) {
            System.exit(0);
        }
    }

    private static String buildSqlForRegNo(String regNo) {
        int lastTwo = Integer.parseInt(regNo.replaceAll("\\D", "")) % 100;
        boolean isOdd = (lastTwo % 2 == 1);

        final String QUERY1 =
            "SELECT p.AMOUNT AS SALARY, " +
            "       CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME, " +
            "       TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE, " +
            "       d.DEPARTMENT_NAME " +
            "FROM   PAYMENTS p " +
            "JOIN   EMPLOYEE   e ON p.EMP_ID = e.EMP_ID " +
            "JOIN   DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID " +
            "WHERE  DAY(p.PAYMENT_TIME) <> 1 " +
            "ORDER  BY p.AMOUNT DESC " +
            "LIMIT  1;";

        final String QUERY2 =
            "SELECT e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME, " +
            "       COUNT(e2.EMP_ID) AS YOUNGER_EMPLOYEES_COUNT " +
            "FROM   EMPLOYEE e1 " +
            "JOIN   DEPARTMENT d ON e1.DEPARTMENT = d.DEPARTMENT_ID " +
            "LEFT JOIN EMPLOYEE e2 " +
            "       ON e1.DEPARTMENT = e2.DEPARTMENT " +
            "      AND e2.DOB > e1.DOB " +
            "GROUP  BY e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME " +
            "ORDER  BY e1.EMP_ID DESC;";

        return isOdd ? QUERY1 : QUERY2;
    }

    private static String mask(String token) {
        if (token == null || token.length() < 14) return "<JWT>";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 6);
        // Only for logging; real header uses the full token
    }
}