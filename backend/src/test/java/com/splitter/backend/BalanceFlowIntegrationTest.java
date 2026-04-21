package com.splitter.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitter.backend.group.model.GroupMember;
import com.splitter.backend.group.model.GroupRole;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class BalanceFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fullBalanceFlow_withBigDecimalAndUnevenSplit() throws Exception {

        RestTemplate client = new RestTemplate();

        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });

        String base = "http://localhost:" + port;

        String user1Username = "balance_user1_" + System.currentTimeMillis();
        String user2Username = "balance_user2_" + System.currentTimeMillis();

        // ---------- SIGN UP USER 1 ----------
        Map<String, String> signupUser1 = Map.of(
                "username", user1Username,
                "password", "TestPass1!"
        );

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> signupUser1Req =
                new HttpEntity<>(mapper.writeValueAsString(signupUser1), jsonHeaders);

        ResponseEntity<String> signupUser1Resp =
                client.postForEntity(base + "/api/auth/signup", signupUser1Req, String.class);

        assertThat(signupUser1Resp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- SIGN UP USER 2 ----------
        Map<String, String> signupUser2 = Map.of(
                "username", user2Username,
                "password", "TestPass1!"
        );

        HttpEntity<String> signupUser2Req =
                new HttpEntity<>(mapper.writeValueAsString(signupUser2), jsonHeaders);

        ResponseEntity<String> signupUser2Resp =
                client.postForEntity(base + "/api/auth/signup", signupUser2Req, String.class);

        assertThat(signupUser2Resp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- SIGN IN USER 1 ----------
        ResponseEntity<String> signinUser1Resp =
                client.postForEntity(base + "/api/auth/signin", signupUser1Req, String.class);

        assertThat(signinUser1Resp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> signinUser1Map =
                mapper.readValue(signinUser1Resp.getBody(), new TypeReference<>() {});

        String tokenUser1 = signinUser1Map.get("token").toString();

        // ---------- SIGN IN USER 2 ----------
        ResponseEntity<String> signinUser2Resp =
                client.postForEntity(base + "/api/auth/signin", signupUser2Req, String.class);

        assertThat(signinUser2Resp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> signinUser2Map =
                mapper.readValue(signinUser2Resp.getBody(), new TypeReference<>() {});

        String tokenUser2 = signinUser2Map.get("token").toString();

        // ---------- CREATE GROUP AS USER 1 ----------
        HttpHeaders authHeadersUser1 = new HttpHeaders();
        authHeadersUser1.set("Authorization", "Bearer " + tokenUser1);
        authHeadersUser1.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> groupResp =
                client.postForEntity(
                        base + "/api/groups?name=BalanceTestGroup",
                        new HttpEntity<>("", authHeadersUser1),
                        String.class
                );

        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> groupMap =
                mapper.readValue(groupResp.getBody(), new TypeReference<>() {});

        UUID groupId = UUID.fromString(groupMap.get("id").toString());

        // ---------- LOOK UP USER IDS ----------
        User user1 = userRepository.findByUsername(user1Username)
                .orElseThrow(() -> new RuntimeException("User 1 not found"));

        User user2 = userRepository.findByUsername(user2Username)
                .orElseThrow(() -> new RuntimeException("User 2 not found"));

        // ---------- ADD USER 2 TO GROUP DIRECTLY ----------
        groupMemberRepository.save(
                new GroupMember(groupId, user2.getId(), GroupRole.MEMBER)
        );

        // ---------- CREATE EXPENSE 1 ----------
        // User 1 pays 10.00 for both users => user2 owes user1 5.00
        Map<String, Object> expense1 = Map.of(
                "groupId", groupId,
                "description", "Dinner",
                "amount", "10.00",
                "paidByUserId", user1.getId(),
                "participants", List.of(user1.getId(), user2.getId())
        );

        HttpEntity<String> expense1Req =
                new HttpEntity<>(mapper.writeValueAsString(expense1), authHeadersUser1);

        ResponseEntity<String> expense1Resp =
                client.postForEntity(base + "/api/expenses", expense1Req, String.class);

        assertThat(expense1Resp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- CREATE EXPENSE 2 ----------
        // User 2 pays 3.33 for both users => user1 owes user2 1.67
        HttpHeaders authHeadersUser2 = new HttpHeaders();
        authHeadersUser2.set("Authorization", "Bearer " + tokenUser2);
        authHeadersUser2.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> expense2 = Map.of(
                "groupId", groupId,
                "description", "Uber",
                "amount", "3.33",
                "paidByUserId", user2.getId(),
                "participants", List.of(user1.getId(), user2.getId())
        );

        HttpEntity<String> expense2Req =
                new HttpEntity<>(mapper.writeValueAsString(expense2), authHeadersUser2);

        ResponseEntity<String> expense2Resp =
                client.postForEntity(base + "/api/expenses", expense2Req, String.class);

        assertThat(expense2Resp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- FETCH BALANCES AS USER 1 ----------
        HttpEntity<String> balanceReq = new HttpEntity<>(authHeadersUser1);

        ResponseEntity<String> balanceResp =
                client.exchange(
                        base + "/api/groups/" + groupId + "/balances",
                        HttpMethod.GET,
                        balanceReq,
                        String.class
                );

        assertThat(balanceResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> balanceMap =
                mapper.readValue(balanceResp.getBody(), new TypeReference<>() {});

        System.out.println("Balance response: " + balanceResp.getBody());

        assertThat(balanceMap.get("groupId").toString()).isEqualTo(groupId.toString());

        List<Map<String, Object>> balances =
                (List<Map<String, Object>>) balanceMap.get("balances");

        assertThat(balances).hasSize(1);

        Map<String, Object> balance = balances.get(0);

        assertThat(Long.valueOf(balance.get("fromUserId").toString())).isEqualTo(user2.getId());
        assertThat(Long.valueOf(balance.get("toUserId").toString())).isEqualTo(user1.getId());
        assertThat(balance.get("amount").toString()).isEqualTo("3.33");

        // ---------- NON-MEMBER SHOULD BE FORBIDDEN ----------
        String outsiderUsername = "outsider_" + System.currentTimeMillis();

        Map<String, String> outsiderSignup = Map.of(
                "username", outsiderUsername,
                "password", "TestPass1!"
        );

        HttpEntity<String> outsiderSignupReq =
                new HttpEntity<>(mapper.writeValueAsString(outsiderSignup), jsonHeaders);

        ResponseEntity<String> outsiderSignupResp =
                client.postForEntity(base + "/api/auth/signup", outsiderSignupReq, String.class);

        assertThat(outsiderSignupResp.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> outsiderSigninResp =
                client.postForEntity(base + "/api/auth/signin", outsiderSignupReq, String.class);

        assertThat(outsiderSigninResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> outsiderSigninMap =
                mapper.readValue(outsiderSigninResp.getBody(), new TypeReference<>() {});

        String outsiderToken = outsiderSigninMap.get("token").toString();

        HttpHeaders outsiderHeaders = new HttpHeaders();
        outsiderHeaders.set("Authorization", "Bearer " + outsiderToken);

        ResponseEntity<String> forbiddenResp =
                client.exchange(
                        base + "/api/groups/" + groupId + "/balances",
                        HttpMethod.GET,
                        new HttpEntity<>(outsiderHeaders),
                        String.class
                );

        assertThat(forbiddenResp.getStatusCode().value()).isEqualTo(403);
    }
}