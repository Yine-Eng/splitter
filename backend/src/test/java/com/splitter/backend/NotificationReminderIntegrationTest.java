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
public class NotificationReminderIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void notificationReminderFlowShouldRespectDebtSettlementAndRateLimitRules() throws Exception {
        RestTemplate client = new RestTemplate();

        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });

        String base = "http://localhost:" + port;

        String adminUsername = "notify_admin_" + System.currentTimeMillis() + "@example.com";
        String memberUsername = "notify_member_" + System.currentTimeMillis() + "@example.com";
        String outsiderUsername = "notify_outsider_" + System.currentTimeMillis() + "@example.com";

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
                base + "/api/groups?name=NotificationTestGroup",
                new HttpEntity<>("", adminHeaders),
                String.class);

        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> groupMap = mapper.readValue(groupResp.getBody(), new TypeReference<>() {
        });

        UUID groupId = UUID.fromString(groupMap.get("id").toString());

        // ---------- ADD MEMBER ----------
        Map<String, Object> addMemberRequest = Map.of("username", memberUsername);

        ResponseEntity<String> addMemberResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members",
                new HttpEntity<>(mapper.writeValueAsString(addMemberRequest), adminHeaders),
                String.class);

        assertThat(addMemberResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- CREATE EXPENSE ----------
        // Admin pays 40.00 for both users, so member owes admin 20.00
        Map<String, Object> expenseRequest = Map.of(
                "groupId", groupId,
                "description", "Dinner",
                "amount", "40.00",
                "paidByUserId", admin.getId(),
                "participants", List.of(admin.getId(), member.getId()));

        ResponseEntity<String> expenseResp = client.postForEntity(
                base + "/api/expenses",
                new HttpEntity<>(mapper.writeValueAsString(expenseRequest), adminHeaders),
                String.class);

        assertThat(expenseResp.getStatusCode().is2xxSuccessful()).isTrue();

        // ---------- ADMIN CAN SEND DEBT REMINDER TO MEMBER ----------
        Map<String, Object> debtReminderRequest = Map.of(
                "recipientUserId", member.getId());

        ResponseEntity<String> debtReminderResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/reminders/debt",
                new HttpEntity<>(mapper.writeValueAsString(debtReminderRequest), adminHeaders),
                String.class);

        assertThat(debtReminderResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> debtReminder = mapper.readValue(debtReminderResp.getBody(), new TypeReference<>() {
        });

        UUID debtNotificationId = UUID.fromString(debtReminder.get("id").toString());

        assertThat(debtReminder.get("type").toString()).isEqualTo("DEBT_REMINDER");
        assertThat(debtReminder.get("senderUserId").toString()).isEqualTo(admin.getId().toString());
        assertThat(debtReminder.get("recipientUserId").toString()).isEqualTo(member.getId().toString());
        assertAmountEquals(debtReminder.get("amount"), "20.00");
        assertThat(debtReminder.get("readByRecipient").toString()).isEqualTo("false");

        // ---------- DUPLICATE DEBT REMINDER SAME DAY IS RATE LIMITED ----------
        ResponseEntity<String> duplicateDebtReminderResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/reminders/debt",
                new HttpEntity<>(mapper.writeValueAsString(debtReminderRequest), adminHeaders),
                String.class);

        assertThat(duplicateDebtReminderResp.getStatusCode().value()).isEqualTo(429);

        // ---------- MEMBER CAN VIEW NOTIFICATIONS ----------
        ResponseEntity<String> memberNotificationsResp = client.exchange(
                base + "/api/notifications",
                HttpMethod.GET,
                new HttpEntity<>(memberHeaders),
                String.class);

        assertThat(memberNotificationsResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> memberNotifications = mapper.readValue(memberNotificationsResp.getBody(),
                new TypeReference<>() {
                });

        assertThat(memberNotifications.stream().map(n -> n.get("type").toString()))
                .contains("DEBT_REMINDER");

        // ---------- MEMBER CAN MARK NOTIFICATION AS READ ----------
        ResponseEntity<String> readResp = client.postForEntity(
                base + "/api/notifications/" + debtNotificationId + "/read",
                new HttpEntity<>("", memberHeaders),
                String.class);

        assertThat(readResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> readNotification = mapper.readValue(readResp.getBody(), new TypeReference<>() {
        });

        assertThat(readNotification.get("readByRecipient").toString()).isEqualTo("true");
        assertThat(readNotification.get("readAt")).isNotNull();

        // ---------- OUTSIDER CANNOT MARK MEMBER'S NOTIFICATION AS READ ----------
        ResponseEntity<String> outsiderReadResp = client.postForEntity(
                base + "/api/notifications/" + debtNotificationId + "/read",
                new HttpEntity<>("", outsiderHeaders),
                String.class);

        assertThat(outsiderReadResp.getStatusCode().value()).isEqualTo(403);

        // ---------- MEMBER CANNOT REMIND ADMIN BECAUSE ADMIN DOES NOT OWE MEMBER
        // ----------
        Map<String, Object> invalidDebtReminderRequest = Map.of(
                "recipientUserId", admin.getId());

        ResponseEntity<String> invalidDebtReminderResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/reminders/debt",
                new HttpEntity<>(mapper.writeValueAsString(invalidDebtReminderRequest), memberHeaders),
                String.class);

        assertThat(invalidDebtReminderResp.getStatusCode().value()).isEqualTo(400);

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

        // ---------- SETTLEMENT SENDER CAN REMIND RECIPIENT ----------
        ResponseEntity<String> settlementReminderResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/remind",
                new HttpEntity<>("", memberHeaders),
                String.class);

        assertThat(settlementReminderResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> settlementReminder = mapper.readValue(settlementReminderResp.getBody(),
                new TypeReference<>() {
                });

        assertThat(settlementReminder.get("type").toString()).isEqualTo("SETTLEMENT_CONFIRMATION_REMINDER");
        assertThat(settlementReminder.get("senderUserId").toString()).isEqualTo(member.getId().toString());
        assertThat(settlementReminder.get("recipientUserId").toString()).isEqualTo(admin.getId().toString());
        assertThat(settlementReminder.get("settlementId").toString()).isEqualTo(settlementId.toString());
        assertAmountEquals(settlementReminder.get("amount"), "5.00");

        // ---------- DUPLICATE SETTLEMENT REMINDER SAME DAY IS RATE LIMITED ----------
        ResponseEntity<String> duplicateSettlementReminderResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/remind",
                new HttpEntity<>("", memberHeaders),
                String.class);

        assertThat(duplicateSettlementReminderResp.getStatusCode().value()).isEqualTo(429);

        // ---------- NON-SENDER CANNOT REMIND SETTLEMENT RECIPIENT ----------
        ResponseEntity<String> outsiderSettlementReminderResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/remind",
                new HttpEntity<>("", outsiderHeaders),
                String.class);

        assertThat(outsiderSettlementReminderResp.getStatusCode().value()).isEqualTo(403);

        // ---------- RECIPIENT SEES SETTLEMENT REMINDER ----------
        ResponseEntity<String> adminNotificationsResp = client.exchange(
                base + "/api/notifications",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                String.class);

        assertThat(adminNotificationsResp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> adminNotifications = mapper.readValue(adminNotificationsResp.getBody(),
                new TypeReference<>() {
                });

        assertThat(adminNotifications.stream().map(n -> n.get("type").toString()))
                .contains("SETTLEMENT_CONFIRMATION_REMINDER");
    }

    @Test
    void settlementReminderIsBlockedOnArchivedGroup() throws Exception {
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });

        String base = "http://localhost:" + port;

        String adminUsername = "archived_remind_admin_" + System.currentTimeMillis() + "@example.com";
        String memberUsername = "archived_remind_member_" + System.currentTimeMillis() + "@example.com";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        String adminToken = signupAndSignin(client, base, adminUsername, jsonHeaders);
        String memberToken = signupAndSignin(client, base, memberUsername, jsonHeaders);

        HttpHeaders adminHeaders = authHeaders(adminToken);
        HttpHeaders memberHeaders = authHeaders(memberToken);

        User admin = userRepository.findByUsername(adminUsername).orElseThrow();
        User member = userRepository.findByUsername(memberUsername).orElseThrow();

        // Create group
        ResponseEntity<String> groupResp = client.postForEntity(
                base + "/api/groups?name=ArchivedReminderGroup",
                new HttpEntity<>("", adminHeaders),
                String.class);
        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();
        UUID groupId = UUID.fromString(mapper.readValue(groupResp.getBody(), new TypeReference<Map<String, Object>>() {
        }).get("id").toString());

        // Add member
        client.postForEntity(
                base + "/api/groups/" + groupId + "/members",
                new HttpEntity<>(mapper.writeValueAsString(Map.of("username", memberUsername)), adminHeaders),
                String.class);

        // Create expense so member owes admin
        Map<String, Object> expenseRequest = Map.of(
                "groupId", groupId,
                "description", "Dinner",
                "amount", "30.00",
                "paidByUserId", admin.getId(),
                "participants", List.of(admin.getId(), member.getId()));
        client.postForEntity(
                base + "/api/expenses",
                new HttpEntity<>(mapper.writeValueAsString(expenseRequest), adminHeaders),
                String.class);

        // Member creates pending settlement
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
        UUID settlementId = UUID.fromString(
                mapper.readValue(settlementResp.getBody(), new TypeReference<Map<String, Object>>() {
                }).get("id").toString());

        // Archive the group
        ResponseEntity<String> archiveResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/archive",
                new HttpEntity<>("", adminHeaders),
                String.class);
        assertThat(archiveResp.getStatusCode().is2xxSuccessful()).isTrue();

        // Settlement reminder on archived group must return 409
        ResponseEntity<String> reminderResp = client.postForEntity(
                base + "/api/settlements/" + settlementId + "/remind",
                new HttpEntity<>("", memberHeaders),
                String.class);
        assertThat(reminderResp.getStatusCode().value()).isEqualTo(409);
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

    private void assertAmountEquals(Object amountValue, String expected) {
        BigDecimal actual = new BigDecimal(amountValue.toString());
        BigDecimal expectedBd = new BigDecimal(expected);
        assertThat(actual).isEqualByComparingTo(expectedBd);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
