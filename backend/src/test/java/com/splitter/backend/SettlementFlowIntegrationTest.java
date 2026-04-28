package com.splitter.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
public class SettlementFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void settlementShouldNotReduceBalanceUntilConfirmed() throws Exception {

        RestTemplate client = new RestTemplate();

        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });

        String base = "http://localhost:" + port;

        String user1Username = "settlement_user1_" + System.currentTimeMillis();
        String user2Username = "settlement_user2_" + System.currentTimeMillis();
        String outsiderUsername = "settlement_outsider_" + System.currentTimeMillis();

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        // ---------- SIGN UP USERS ----------
        HttpEntity<String> signupUser1Req = new HttpEntity<>(
                mapper.writeValueAsString(Map.of(
                        "username", user1Username,
                        "password", "TestPass1!"
                )),
                jsonHeaders
        );

        HttpEntity<String> signupUser2Req = new HttpEntity<>(
                mapper.writeValueAsString(Map.of(
                        "username", user2Username,
                        "password", "TestPass1!"
                )),
                jsonHeaders
        );

        HttpEntity<String> signupOutsiderReq = new HttpEntity<>(
                mapper.writeValueAsString(Map.of(
                        "username", outsiderUsername,
                        "password", "TestPass1!"
                )),
                jsonHeaders
        );

        assertThat(client.postForEntity(base + "/api/auth/signup", signupUser1Req, String.class)
                .getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(client.postForEntity(base + "/api/auth/signup", signupUser2Req, String.class)
                .getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(client.postForEntity(base + "/api/auth/signup", signupOutsiderReq, String.class)
                .getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- SIGN IN USERS ----------
        String tokenUser1 = extractToken(client.postForEntity(base + "/api/auth/signin", signupUser1Req, String.class));
        String tokenUser2 = extractToken(client.postForEntity(base + "/api/auth/signin", signupUser2Req, String.class));
        String tokenOutsider = extractToken(client.postForEntity(base + "/api/auth/signin", signupOutsiderReq, String.class));

        HttpHeaders authHeadersUser1 = new HttpHeaders();
        authHeadersUser1.set("Authorization", "Bearer " + tokenUser1);
        authHeadersUser1.setContentType(MediaType.APPLICATION_JSON);

        HttpHeaders authHeadersUser2 = new HttpHeaders();
        authHeadersUser2.set("Authorization", "Bearer " + tokenUser2);
        authHeadersUser2.setContentType(MediaType.APPLICATION_JSON);

        HttpHeaders authHeadersOutsider = new HttpHeaders();
        authHeadersOutsider.set("Authorization", "Bearer " + tokenOutsider);
        authHeadersOutsider.setContentType(MediaType.APPLICATION_JSON);

        // ---------- CREATE GROUP ----------
        ResponseEntity<String> groupResp = client.postForEntity(
                base + "/api/groups?name=SettlementTestGroup",
                new HttpEntity<>("", authHeadersUser1),
                String.class
        );

        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> groupMap =
                mapper.readValue(groupResp.getBody(), new TypeReference<>() {});

        UUID groupId = UUID.fromString(groupMap.get("id").toString());

        User user1 = userRepository.findByUsername(user1Username)
                .orElseThrow(() -> new RuntimeException("User 1 not found"));

        User user2 = userRepository.findByUsername(user2Username)
                .orElseThrow(() -> new RuntimeException("User 2 not found"));

        // ---------- ADD USER 2 TO GROUP DIRECTLY ----------
        groupMemberRepository.save(new GroupMember(groupId, user2.getId(), GroupRole.MEMBER));

        // ---------- CREATE EXPENSE ----------
        // user1 pays 10.00 for both => user2 owes user1 5.00
        Map<String, Object> expenseRequest = Map.of(
                "groupId", groupId,
                "description", "Dinner",
                "amount", "10.00",
                "paidByUserId", user1.getId(),
                "participants", List.of(user1.getId(), user2.getId())
        );

        ResponseEntity<String> expenseResp = client.postForEntity(
                base + "/api/expenses",
                new HttpEntity<>(mapper.writeValueAsString(expenseRequest), authHeadersUser1),
                String.class
        );

        assertThat(expenseResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- CHECK INITIAL BALANCE ----------
        Map<String, Object> initialBalanceMap = getBalanceMap(client, base, groupId, authHeadersUser1);

        List<Map<String, Object>> initialBalances =
                (List<Map<String, Object>>) initialBalanceMap.get("balances");

        assertThat(initialBalances).hasSize(1);
        assertThat(initialBalances.get(0).get("fromUserId").toString()).isEqualTo(user2.getId().toString());
        assertThat(initialBalances.get(0).get("toUserId").toString()).isEqualTo(user1.getId().toString());
        assertAmountEquals(initialBalances.get(0).get("amount"), "5.00");

        // ---------- CREATE PENDING SETTLEMENT ----------
        Map<String, Object> settlementRequest = Map.of(
                "groupId", groupId,
                "toUserId", user1.getId(),
                "amount", "3.00",
                "note", "Sent through cash"
        );

        ResponseEntity<String> createSettlementResp = client.postForEntity(
                base + "/api/settlements",
                new HttpEntity<>(mapper.writeValueAsString(settlementRequest), authHeadersUser2),
                String.class
        );

        assertThat(createSettlementResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> settlementMap =
                mapper.readValue(createSettlementResp.getBody(), new TypeReference<>() {});

        UUID settlementId = UUID.fromString(settlementMap.get("id").toString());

        assertThat(settlementMap.get("status").toString()).isEqualTo("PENDING");
        assertThat(settlementMap.get("fromUserId").toString()).isEqualTo(user2.getId().toString());
        assertThat(settlementMap.get("toUserId").toString()).isEqualTo(user1.getId().toString());
        assertAmountEquals(settlementMap.get("amount"), "3.00");

        // ---------- PENDING INCOMING FOR USER 1 ----------
        ResponseEntity<String> incomingResp = client.exchange(
                base + "/api/settlements/pending/incoming",
                HttpMethod.GET,
                new HttpEntity<>(authHeadersUser1),
                String.class
        );

        assertThat(incomingResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> incomingSettlements =
                mapper.readValue(incomingResp.getBody(), new TypeReference<>() {});

        assertThat(incomingSettlements).hasSize(1);
        assertThat(incomingSettlements.get(0).get("status").toString()).isEqualTo("PENDING");

        // ---------- PENDING OUTGOING FOR USER 2 ----------
        ResponseEntity<String> outgoingResp = client.exchange(
                base + "/api/settlements/pending/outgoing",
                HttpMethod.GET,
                new HttpEntity<>(authHeadersUser2),
                String.class
        );

        assertThat(outgoingResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> outgoingSettlements =
                mapper.readValue(outgoingResp.getBody(), new TypeReference<>() {});

        assertThat(outgoingSettlements).hasSize(1);
        assertThat(outgoingSettlements.get(0).get("status").toString()).isEqualTo("PENDING");

        // ---------- PENDING SETTLEMENT SHOULD NOT CHANGE BALANCE ----------
        Map<String, Object> pendingBalanceMap = getBalanceMap(client, base, groupId, authHeadersUser1);

        List<Map<String, Object>> pendingBalances =
                (List<Map<String, Object>>) pendingBalanceMap.get("balances");

        assertThat(pendingBalances).hasSize(1);
        assertAmountEquals(pendingBalances.get(0).get("amount"), "5.00");

        // ---------- OUTSIDER CANNOT CONFIRM ----------
        ResponseEntity<String> outsiderConfirmResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/confirm",
                new HttpEntity<>("", authHeadersOutsider),
                String.class
        );

        assertThat(outsiderConfirmResp.getStatusCode().value()).isEqualTo(403);

        // ---------- CONFIRM SETTLEMENT AS RECIPIENT ----------
        ResponseEntity<String> confirmResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/confirm",
                new HttpEntity<>("", authHeadersUser1),
                String.class
        );

        assertThat(confirmResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> confirmedSettlementMap =
                mapper.readValue(confirmResp.getBody(), new TypeReference<>() {});

        assertThat(confirmedSettlementMap.get("status").toString()).isEqualTo("CONFIRMED");

        // ---------- CONFIRMED SETTLEMENT SHOULD REDUCE BALANCE ----------
        Map<String, Object> confirmedBalanceMap = getBalanceMap(client, base, groupId, authHeadersUser1);

        List<Map<String, Object>> confirmedBalances =
                (List<Map<String, Object>>) confirmedBalanceMap.get("balances");

        assertThat(confirmedBalances).hasSize(1);
        assertThat(confirmedBalances.get(0).get("fromUserId").toString()).isEqualTo(user2.getId().toString());
        assertThat(confirmedBalances.get(0).get("toUserId").toString()).isEqualTo(user1.getId().toString());
        assertAmountEquals(confirmedBalances.get(0).get("amount"), "2.00");

        // ---------- CREATE SECOND SETTLEMENT TO TEST REJECTION ----------
        Map<String, Object> secondSettlementRequest = Map.of(
                "groupId", groupId,
                "toUserId", user1.getId(),
                "amount", "1.00",
                "note", "Second settlement claim"
        );

        ResponseEntity<String> secondSettlementResp = client.postForEntity(
                base + "/api/settlements",
                new HttpEntity<>(mapper.writeValueAsString(secondSettlementRequest), authHeadersUser2),
                String.class
        );

        assertThat(secondSettlementResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> secondSettlementMap =
                mapper.readValue(secondSettlementResp.getBody(), new TypeReference<>() {});

        UUID secondSettlementId = UUID.fromString(secondSettlementMap.get("id").toString());

        // ---------- REJECT SECOND SETTLEMENT ----------
        Map<String, Object> rejectRequest = Map.of(
                "reason", "I did not receive this payment"
        );

        ResponseEntity<String> rejectResp = client.postForEntity(
                base + "/api/settlements/" + secondSettlementId + "/reject",
                new HttpEntity<>(mapper.writeValueAsString(rejectRequest), authHeadersUser1),
                String.class
        );

        assertThat(rejectResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> rejectedSettlementMap =
                mapper.readValue(rejectResp.getBody(), new TypeReference<>() {});

        assertThat(rejectedSettlementMap.get("status").toString()).isEqualTo("REJECTED");
        assertThat(rejectedSettlementMap.get("rejectionReason").toString()).isEqualTo("I did not receive this payment");

        // ---------- REJECTED SETTLEMENT SHOULD NOT CHANGE BALANCE ----------
        Map<String, Object> rejectedBalanceMap = getBalanceMap(client, base, groupId, authHeadersUser1);

        List<Map<String, Object>> rejectedBalances =
                (List<Map<String, Object>>) rejectedBalanceMap.get("balances");

        assertThat(rejectedBalances).hasSize(1);
        assertAmountEquals(rejectedBalances.get(0).get("amount"), "2.00");

        // ---------- GROUP SETTLEMENT HISTORY ----------
        // Only CONFIRMED settlements should appear in group-visible history
        ResponseEntity<String> historyResp = client.exchange(
                base + "/api/settlements/groups/" + groupId,
                HttpMethod.GET,
                new HttpEntity<>(authHeadersUser1),
                String.class
        );

        assertThat(historyResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> history =
                mapper.readValue(historyResp.getBody(), new TypeReference<>() {});

        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("status").toString()).isEqualTo("CONFIRMED");
        assertAmountEquals(history.get(0).get("amount"), "3.00");
    }

    private String extractToken(ResponseEntity<String> signinResp) throws Exception {
        assertThat(signinResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> signinMap =
                mapper.readValue(signinResp.getBody(), new TypeReference<>() {});

        return signinMap.get("token").toString();
    }

    private Map<String, Object> getBalanceMap(
            RestTemplate client,
            String base,
            UUID groupId,
            HttpHeaders authHeaders
    ) throws Exception {
        ResponseEntity<String> balanceResp = client.exchange(
                base + "/api/groups/" + groupId + "/balances",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                String.class
        );

        assertThat(balanceResp.getStatusCode().is2xxSuccessful()).isTrue();

        return mapper.readValue(balanceResp.getBody(), new TypeReference<>() {});
    }

        private void assertAmountEquals(Object actualAmount, String expectedAmount) {
                assertThat(new BigDecimal(actualAmount.toString()))
                                .isEqualByComparingTo(new BigDecimal(expectedAmount));
        }
}
