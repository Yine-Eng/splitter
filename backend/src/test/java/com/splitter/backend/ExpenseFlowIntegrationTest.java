package com.splitter.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ExpenseFlowIntegrationTest {

    @LocalServerPort
    private int port;

        @Autowired
        private UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fullExpenseFlow() throws Exception {

        RestTemplate client = new RestTemplate();

        // Prevent RestTemplate from throwing exceptions on 4xx/5xx
        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });

        String base = "http://localhost:" + port;

        System.out.println("SERVER STARTED ON: " + base);

        // ---------- SIGNUP ----------
        String username = "expensetest_" + System.currentTimeMillis();

        Map<String,String> signup = Map.of(
                "username", username,
                "password","TestPass1!"
        );

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> signupReq =
                new HttpEntity<>(mapper.writeValueAsString(signup), jsonHeaders);

        ResponseEntity<String> signupResp =
                client.postForEntity(base + "/api/auth/signup", signupReq, String.class);

        System.out.println("Signup response: " + signupResp.getBody());

        assertThat(signupResp.getStatusCode().is2xxSuccessful()).isTrue();


        // ---------- SIGNIN ----------
        ResponseEntity<String> signinResp =
                client.postForEntity(base + "/api/auth/signin", signupReq, String.class);

        Map<String,Object> signin =
                mapper.readValue(signinResp.getBody(), new TypeReference<>() {});

        String token = signin.get("token").toString();

        System.out.println("JWT TOKEN: " + token);

        assertThat(token).isNotNull();

        User signedInUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Signed in user was not found"));


        // ---------- CREATE GROUP ----------
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization","Bearer " + token);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> groupResp =
                client.postForEntity(
                        base + "/api/groups?name=TestTrip",
                        new HttpEntity<>("",authHeaders),
                        String.class
                );

        System.out.println("Group created: " + groupResp.getBody());

        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String,Object> group =
                mapper.readValue(groupResp.getBody(), new TypeReference<>() {});

        String groupId = group.get("id").toString();

        assertThat(group.get("name")).isEqualTo("TestTrip");


        // ---------- CREATE EXPENSE ----------
        Map<String,Object> expenseRequest = Map.of(
                "groupId", UUID.fromString(groupId),
                "description","Dinner",
                "amount",120,
                "paidByUserId",1,
                "participants", List.of(signedInUser.getId())
        );

        HttpEntity<String> expenseReq =
                new HttpEntity<>(mapper.writeValueAsString(expenseRequest),authHeaders);

        ResponseEntity<String> expenseResp =
                client.postForEntity(
                        base + "/api/expenses",
                        expenseReq,
                        String.class
                );

        System.out.println("Expense response: " + expenseResp.getBody());

        assertThat(expenseResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String,Object> expenseResponseBody =
                mapper.readValue(expenseResp.getBody(), new TypeReference<>() {});

        assertThat(Long.valueOf(expenseResponseBody.get("paidByUserId").toString()))
                .isEqualTo(signedInUser.getId());
    }
}