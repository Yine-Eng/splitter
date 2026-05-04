package com.splitter.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class GroupEventIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void groupEventFlowShouldRespectVisibilityAndAccessRules() throws Exception {
        RestTemplate client = new RestTemplate();

        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });

        String base = "http://localhost:" + port;

        String adminUsername = "event_admin_" + System.currentTimeMillis() + "@example.com";
        String memberUsername = "event_member_" + System.currentTimeMillis() + "@example.com";
        String outsiderUsername = "event_outsider_" + System.currentTimeMillis() + "@example.com";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        String adminToken = signupAndSignin(client, base, adminUsername, jsonHeaders);
        String memberToken = signupAndSignin(client, base, memberUsername, jsonHeaders);
        String outsiderToken = signupAndSignin(client, base, outsiderUsername, jsonHeaders);

        HttpHeaders adminHeaders = authHeaders(adminToken);
        HttpHeaders memberHeaders = authHeaders(memberToken);
        HttpHeaders outsiderHeaders = authHeaders(outsiderToken);

        User admin = userRepository.findByUsername(adminUsername).orElseThrow();
        User member = userRepository.findByUsername(memberUsername).orElseThrow();

        // ---------- CREATE GROUP ----------
        ResponseEntity<String> groupResp = client.postForEntity(
                base + "/api/groups?name=EventTestGroup",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> groupMap = mapper.readValue(groupResp.getBody(), new TypeReference<>() {
        });

        UUID groupId = UUID.fromString(groupMap.get("id").toString());

        // ---------- ADD MEMBER CREATES GROUP EVENT ----------
        Map<String, Object> addMemberRequest = Map.of(
                "username", memberUsername);

        ResponseEntity<String> addMemberResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members",
                new HttpEntity<>(mapper.writeValueAsString(addMemberRequest), adminHeaders),
                String.class);

        assertThat(addMemberResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- CREATE EXPENSE CREATES GROUP EVENT ----------
        Map<String, Object> expenseRequest = Map.of(
                "groupId", groupId,
                "description", "Pizza",
                "amount", "20.00",
                "paidByUserId", admin.getId(),
                "participants", List.of(admin.getId(), member.getId()));

        ResponseEntity<String> expenseResp = client.postForEntity(
                base + "/api/expenses",
                new HttpEntity<>(mapper.writeValueAsString(expenseRequest), adminHeaders),
                String.class);

        assertThat(expenseResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- MEMBER CREATES PENDING SETTLEMENT ----------
        Map<String, Object> settlementRequest = Map.of(
                "groupId", groupId,
                "toUserId", admin.getId(),
                "amount", "5.00",
                "note", "Sent partial payment");

        ResponseEntity<String> settlementResp = client.postForEntity(
                base + "/api/settlements",
                new HttpEntity<>(mapper.writeValueAsString(settlementRequest), memberHeaders),
                String.class);

        assertThat(settlementResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> settlementMap = mapper.readValue(settlementResp.getBody(), new TypeReference<>() {
        });

        UUID settlementId = UUID.fromString(settlementMap.get("id").toString());

        // ---------- MEMBER CAN SEE GROUP EVENTS AND PRIVATE SETTLEMENT_CREATED
        // ----------
        List<Map<String, Object>> memberEvents = getEvents(client, base, groupId, memberHeaders);

        assertThat(eventTypes(memberEvents))
                .contains("MEMBER_ADDED", "EXPENSE_CREATED", "SETTLEMENT_CREATED");

        Map<String, Object> privateSettlementCreated = memberEvents.stream()
                .filter(e -> e.get("type").toString().equals("SETTLEMENT_CREATED"))
                .findFirst()
                .orElseThrow();

        assertThat(privateSettlementCreated.get("visibility").toString()).isEqualTo("PRIVATE");
        assertThat(privateSettlementCreated.get("actorUserId").toString()).isEqualTo(member.getId().toString());
        assertThat(privateSettlementCreated.get("targetUserId").toString()).isEqualTo(admin.getId().toString());

        // ---------- ADMIN CAN ALSO SEE PRIVATE SETTLEMENT_CREATED BECAUSE ADMIN IS
        // TARGET ----------
        List<Map<String, Object>> adminEventsBeforeConfirm = getEvents(client, base, groupId, adminHeaders);

        assertThat(eventTypes(adminEventsBeforeConfirm))
                .contains("SETTLEMENT_CREATED");

        // ---------- OUTSIDER CANNOT SEE GROUP EVENTS ----------
        ResponseEntity<String> outsiderEventsResp = client.exchange(
                base + "/api/groups/" + groupId + "/events",
                HttpMethod.GET,
                new HttpEntity<>(outsiderHeaders),
                String.class);

        assertThat(outsiderEventsResp.getStatusCode().value()).isEqualTo(403);

        // ---------- CONFIRM SETTLEMENT CREATES GROUP EVENT ----------
        ResponseEntity<String> confirmResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/confirm",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(confirmResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> memberEventsAfterConfirm = getEvents(client, base, groupId, memberHeaders);

        assertThat(eventTypes(memberEventsAfterConfirm))
                .contains("SETTLEMENT_CONFIRMED");

        Map<String, Object> confirmedEvent = memberEventsAfterConfirm.stream()
                .filter(e -> e.get("type").toString().equals("SETTLEMENT_CONFIRMED"))
                .findFirst()
                .orElseThrow();

        assertThat(confirmedEvent.get("visibility").toString()).isEqualTo("GROUP");
        assertAmountEquals(confirmedEvent.get("amount"), "5.00");

        // ---------- CREATE AND REJECT ANOTHER SETTLEMENT ----------
        Map<String, Object> secondSettlementRequest = Map.of(
                "groupId", groupId,
                "toUserId", admin.getId(),
                "amount", "2.00",
                "note", "Another claim");

        ResponseEntity<String> secondSettlementResp = client.postForEntity(
                base + "/api/settlements",
                new HttpEntity<>(mapper.writeValueAsString(secondSettlementRequest), memberHeaders),
                String.class);

        assertThat(secondSettlementResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> secondSettlementMap = mapper.readValue(secondSettlementResp.getBody(),
                new TypeReference<>() {
                });

        UUID secondSettlementId = UUID.fromString(secondSettlementMap.get("id").toString());

        Map<String, Object> rejectRequest = Map.of(
                "reason", "Did not receive it");

        ResponseEntity<String> rejectResp = client.postForEntity(
                base + "/api/settlements/" + secondSettlementId + "/reject",
                new HttpEntity<>(mapper.writeValueAsString(rejectRequest), adminHeaders),
                String.class);

        assertThat(rejectResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> memberEventsAfterReject = getEvents(client, base, groupId, memberHeaders);

        assertThat(eventTypes(memberEventsAfterReject))
                .contains("SETTLEMENT_REJECTED");

        Map<String, Object> rejectedEvent = memberEventsAfterReject.stream()
                .filter(e -> e.get("type").toString().equals("SETTLEMENT_REJECTED"))
                .findFirst()
                .orElseThrow();

        assertThat(rejectedEvent.get("visibility").toString()).isEqualTo("PRIVATE");
        assertThat(rejectedEvent.get("actorUserId").toString()).isEqualTo(admin.getId().toString());
        assertThat(rejectedEvent.get("targetUserId").toString()).isEqualTo(member.getId().toString());
    }

    private String signupAndSignin(
            RestTemplate client,
            String base,
            String username,
            HttpHeaders jsonHeaders) throws Exception {
        Map<String, String> signup = Map.of(
                "username", username,
                "password", "TestPass1!");

        HttpEntity<String> signupReq = new HttpEntity<>(mapper.writeValueAsString(signup), jsonHeaders);

        ResponseEntity<String> signupResp = client.postForEntity(base + "/api/auth/signup", signupReq, String.class);

        assertThat(signupResp.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> signinResp = client.postForEntity(base + "/api/auth/signin", signupReq, String.class);

        assertThat(signinResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> signinMap = mapper.readValue(signinResp.getBody(), new TypeReference<>() {
        });

        return signinMap.get("token").toString();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private List<Map<String, Object>> getEvents(
            RestTemplate client,
            String base,
            UUID groupId,
            HttpHeaders headers) throws Exception {
        ResponseEntity<String> eventsResp = client.exchange(
                base + "/api/groups/" + groupId + "/events",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(eventsResp.getStatusCode().is2xxSuccessful()).isTrue();

        return mapper.readValue(eventsResp.getBody(), new TypeReference<>() {
        });
    }

    private List<String> eventTypes(List<Map<String, Object>> events) {
        return events.stream()
                .map(event -> event.get("type").toString())
                .toList();
    }

    private void assertAmountEquals(Object actualAmount, String expectedAmount) {
        BigDecimal actual = new BigDecimal(actualAmount.toString());
        BigDecimal expected = new BigDecimal(expectedAmount);
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
