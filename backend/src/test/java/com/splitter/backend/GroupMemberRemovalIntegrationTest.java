package com.splitter.backend;

import static org.assertj.core.api.Assertions.assertThat;

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
public class GroupMemberRemovalIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void groupMemberRemovalFlowShouldRespectDebtPendingSettlementsAndAccessRules() throws Exception {
        RestTemplate client = new RestTemplate();

        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });

        String base = "http://localhost:" + port;

        String adminUsername = "remove_admin_" + System.currentTimeMillis() + "@example.com";
        String memberUsername = "remove_member_" + System.currentTimeMillis() + "@example.com";
        String outsiderUsername = "remove_outsider_" + System.currentTimeMillis() + "@example.com";

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
                base + "/api/groups?name=RemovalTestGroup",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> groupMap = mapper.readValue(groupResp.getBody(), new TypeReference<>() {
        });

        UUID groupId = UUID.fromString(groupMap.get("id").toString());

        // ---------- ADMIN ADDS MEMBER ----------
        Map<String, Object> addMemberRequest = Map.of("username", memberUsername);

        ResponseEntity<String> addMemberResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members",
                new HttpEntity<>(mapper.writeValueAsString(addMemberRequest), adminHeaders),
                String.class);

        assertThat(addMemberResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- NON-ADMIN CANNOT REMOVE ----------
        ResponseEntity<String> nonAdminRemoveResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members/" + admin.getId() + "/remove",
                new HttpEntity<>("", memberHeaders),
                String.class);

        assertThat(nonAdminRemoveResp.getStatusCode().value()).isEqualTo(403);

        // ---------- ADMIN CREATES EXPENSE ----------
        // Admin pays 30.00 for admin + member, so member owes admin 15.00
        Map<String, Object> expenseRequest = Map.of(
                "groupId", groupId,
                "description", "Hotel",
                "amount", "30.00",
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
                "note", "Partial payment");

        ResponseEntity<String> settlementResp = client.postForEntity(
                base + "/api/settlements",
                new HttpEntity<>(mapper.writeValueAsString(settlementRequest), memberHeaders),
                String.class);

        assertThat(settlementResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> settlementMap = mapper.readValue(settlementResp.getBody(), new TypeReference<>() {
        });

        UUID settlementId = UUID.fromString(settlementMap.get("id").toString());

        // ---------- PREVIEW SHOWS OUTSTANDING DEBT AND PENDING SETTLEMENT ----------
        ResponseEntity<String> previewWithPendingResp = client.exchange(
                base + "/api/groups/" + groupId + "/members/" + member.getId() + "/removal-preview",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class);

        assertThat(previewWithPendingResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> previewWithPending = mapper.readValue(previewWithPendingResp.getBody(),
                new TypeReference<>() {
                });

        assertThat(previewWithPending.get("pendingSettlementCount").toString()).isEqualTo("1");

        List<Map<String, Object>> userOwesWithPending = (List<Map<String, Object>>) previewWithPending.get("userOwes");

        assertThat(userOwesWithPending).hasSize(1);
        assertThat(userOwesWithPending.get(0).get("fromUserId").toString()).isEqualTo(member.getId().toString());
        assertThat(userOwesWithPending.get(0).get("toUserId").toString()).isEqualTo(admin.getId().toString());

        // ---------- ADMIN CANNOT REMOVE WHILE PENDING SETTLEMENT EXISTS ----------
        ResponseEntity<String> removeWithPendingResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members/" + member.getId() + "/remove",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(removeWithPendingResp.getStatusCode().value()).isEqualTo(409);

        // ---------- REJECT PENDING SETTLEMENT ----------
        ResponseEntity<String> rejectResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/reject",
                new HttpEntity<>(mapper.writeValueAsString(Map.of("reason", "Not received")), adminHeaders),
                String.class);

        assertThat(rejectResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- REMOVAL BLOCKED BECAUSE OUTSTANDING BALANCE EXISTS ----------
        ResponseEntity<String> removeBlockedResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members/" + member.getId() + "/remove",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(removeBlockedResp.getStatusCode().value()).isEqualTo(409);

        // ---------- MEMBER FULLY SETTLES OUTSTANDING BALANCE ----------
        Map<String, Object> fullSettlementRequest = Map.of(
                "groupId", groupId,
                "toUserId", admin.getId(),
                "amount", "15.00",
                "note", "Full payment");

        ResponseEntity<String> fullSettlementResp = client.postForEntity(
                base + "/api/settlements",
                new HttpEntity<>(mapper.writeValueAsString(fullSettlementRequest), memberHeaders),
                String.class);

        assertThat(fullSettlementResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> fullSettlementMap = mapper.readValue(fullSettlementResp.getBody(), new TypeReference<>() {
        });

        UUID fullSettlementId = UUID.fromString(fullSettlementMap.get("id").toString());

        // ---------- ADMIN CONFIRMS FULL SETTLEMENT ----------
        ResponseEntity<String> confirmResp = client.postForEntity(
                base + "/api/settlements/" + fullSettlementId + "/confirm",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(confirmResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- REMOVAL SUCCEEDS AFTER BALANCE SETTLED ----------
        ResponseEntity<String> removeSuccessResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members/" + member.getId() + "/remove",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(removeSuccessResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> removalResult = mapper.readValue(removeSuccessResp.getBody(),
                new TypeReference<>() {
                });

        assertThat(removalResult.get("userId").toString()).isEqualTo(member.getId().toString());

        // ---------- REMOVED MEMBER CAN NO LONGER VIEW MEMBERS ----------
        ResponseEntity<String> removedMemberMembersResp = client.exchange(
                base + "/api/groups/" + groupId + "/members",
                HttpMethod.GET,
                new HttpEntity<>(memberHeaders),
                String.class);

        assertThat(removedMemberMembersResp.getStatusCode().value()).isEqualTo(403);

        // ---------- REMOVED MEMBER CAN NO LONGER VIEW EXPENSES ----------
        ResponseEntity<String> removedMemberExpensesResp = client.exchange(
                base + "/api/groups/" + groupId + "/expenses",
                HttpMethod.GET,
                new HttpEntity<>(memberHeaders),
                String.class);

        assertThat(removedMemberExpensesResp.getStatusCode().value()).isEqualTo(403);

        // ---------- REMOVED MEMBER CAN NO LONGER VIEW EVENTS ----------
        ResponseEntity<String> removedMemberEventsResp = client.exchange(
                base + "/api/groups/" + groupId + "/events",
                HttpMethod.GET,
                new HttpEntity<>(memberHeaders),
                String.class);

        assertThat(removedMemberEventsResp.getStatusCode().value()).isEqualTo(403);

        // ---------- OUTSIDER CANNOT PREVIEW REMOVAL ----------
        ResponseEntity<String> outsiderPreviewResp = client.exchange(
                base + "/api/groups/" + groupId + "/members/" + member.getId() + "/removal-preview",
                HttpMethod.GET,
                new HttpEntity<>(outsiderHeaders),
                String.class);

        assertThat(outsiderPreviewResp.getStatusCode().value()).isEqualTo(403);

        // ---------- REMOVAL CREATES GROUP EVENT ----------
        ResponseEntity<String> eventsResp = client.exchange(
                base + "/api/groups/" + groupId + "/events",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class);

        assertThat(eventsResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> events = mapper.readValue(eventsResp.getBody(), new TypeReference<>() {
        });

        assertThat(events.stream().map(event -> event.get("type").toString()))
                .contains("MEMBER_REMOVED");
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
}
