package com.splitter.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class GroupMembershipAccessIntegrationTest {

        @LocalServerPort
        private int port;

        private final ObjectMapper mapper = new ObjectMapper();

        @Test
        void groupMembershipAndAccessFlow() throws Exception {
                RestTemplate client = new RestTemplate();

                client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
                        @Override
                        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                                return false;
                        }
                });

                String base = "http://localhost:" + port;

                String adminUsername = "group_admin_" + System.currentTimeMillis() + "@example.com";
                String memberUsername = "group_member_" + System.currentTimeMillis() + "@example.com";
                String outsiderUsername = "group_outsider_" + System.currentTimeMillis() + "@example.com";

                HttpHeaders jsonHeaders = new HttpHeaders();
                jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

                String adminToken = signupAndSignin(client, base, adminUsername, jsonHeaders);
                String memberToken = signupAndSignin(client, base, memberUsername, jsonHeaders);
                String outsiderToken = signupAndSignin(client, base, outsiderUsername, jsonHeaders);

                HttpHeaders adminHeaders = authHeaders(adminToken);
                HttpHeaders memberHeaders = authHeaders(memberToken);
                HttpHeaders outsiderHeaders = authHeaders(outsiderToken);

                // ---------- CREATE GROUP AS ADMIN ----------
                ResponseEntity<String> groupResp = client.postForEntity(
                                base + "/api/groups?name=AccessTestGroup",
                                new HttpEntity<>("", adminHeaders),
                                String.class);

                assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

                Map<String, Object> groupMap = mapper.readValue(groupResp.getBody(), new TypeReference<>() {
                });

                UUID groupId = UUID.fromString(groupMap.get("id").toString());

                // ---------- ADMIN CAN ADD MEMBER ----------
                Map<String, Object> addMemberRequest = Map.of(
                                "username", memberUsername);

                ResponseEntity<String> addMemberResp = client.postForEntity(
                                base + "/api/groups/" + groupId + "/members",
                                new HttpEntity<>(mapper.writeValueAsString(addMemberRequest), adminHeaders),
                                String.class);

                assertThat(addMemberResp.getStatusCode().is2xxSuccessful()).isTrue();

                Map<String, Object> addedMemberMap = mapper.readValue(addMemberResp.getBody(), new TypeReference<>() {
                });

                assertThat(addedMemberMap.get("username").toString()).isEqualTo(memberUsername);
                assertThat(addedMemberMap.get("role").toString()).isEqualTo("MEMBER");

                // ---------- DUPLICATE MEMBER IS REJECTED ----------
                ResponseEntity<String> duplicateMemberResp = client.postForEntity(
                                base + "/api/groups/" + groupId + "/members",
                                new HttpEntity<>(mapper.writeValueAsString(addMemberRequest), adminHeaders),
                                String.class);

                assertThat(duplicateMemberResp.getStatusCode().value()).isEqualTo(400);

                // ---------- NON-ADMIN MEMBER CANNOT ADD ANOTHER MEMBER ----------
                Map<String, Object> addOutsiderRequest = Map.of(
                                "username", outsiderUsername);

                ResponseEntity<String> nonAdminAddResp = client.postForEntity(
                                base + "/api/groups/" + groupId + "/members",
                                new HttpEntity<>(mapper.writeValueAsString(addOutsiderRequest), memberHeaders),
                                String.class);

                assertThat(nonAdminAddResp.getStatusCode().value()).isEqualTo(403);

                // ---------- MEMBER CAN VIEW GROUP MEMBERS ----------
                ResponseEntity<String> memberListResp = client.exchange(
                                base + "/api/groups/" + groupId + "/members",
                                HttpMethod.GET,
                                new HttpEntity<>(memberHeaders),
                                String.class);

                assertThat(memberListResp.getStatusCode().is2xxSuccessful()).isTrue();

                List<Map<String, Object>> members = mapper.readValue(memberListResp.getBody(), new TypeReference<>() {
                });

                assertThat(members).hasSize(2);
                assertThat(members.stream().map(m -> m.get("username").toString()))
                                .contains(adminUsername, memberUsername);

                // ---------- OUTSIDER CANNOT VIEW GROUP MEMBERS ----------
                ResponseEntity<String> outsiderMembersResp = client.exchange(
                                base + "/api/groups/" + groupId + "/members",
                                HttpMethod.GET,
                                new HttpEntity<>(outsiderHeaders),
                                String.class);

                assertThat(outsiderMembersResp.getStatusCode().value()).isEqualTo(403);

                // ---------- ADMIN CREATES EXPENSE ----------
                // Use the first member (admin) as the payer
                Long adminId = Long.valueOf(members.get(0).get("userId").toString());

                Map<String, Object> expenseRequest = Map.of(
                                "groupId", groupId,
                                "description", "Groceries",
                                "amount", "24.00",
                                "paidByUserId", adminId,
                                "participants", List.of(
                                                adminId,
                                                Long.valueOf(members.get(1).get("userId").toString())));

                ResponseEntity<String> expenseResp = client.postForEntity(
                                base + "/api/expenses",
                                new HttpEntity<>(mapper.writeValueAsString(expenseRequest), adminHeaders),
                                String.class);

                assertThat(expenseResp.getStatusCode().is2xxSuccessful()).isTrue();

                // ---------- MEMBER CAN VIEW GROUP EXPENSES ----------
                ResponseEntity<String> memberExpensesResp = client.exchange(
                                base + "/api/groups/" + groupId + "/expenses",
                                HttpMethod.GET,
                                new HttpEntity<>(memberHeaders),
                                String.class);

                assertThat(memberExpensesResp.getStatusCode().is2xxSuccessful()).isTrue();

                List<Map<String, Object>> expenses = mapper.readValue(memberExpensesResp.getBody(),
                                new TypeReference<>() {
                                });

                assertThat(expenses).hasSize(1);
                assertThat(expenses.get(0).get("description").toString()).isEqualTo("Groceries");
                assertAmountEquals(expenses.get(0).get("amount"), "24.00");

                // ---------- OUTSIDER CANNOT VIEW GROUP EXPENSES ----------
                ResponseEntity<String> outsiderExpensesResp = client.exchange(
                                base + "/api/groups/" + groupId + "/expenses",
                                HttpMethod.GET,
                                new HttpEntity<>(outsiderHeaders),
                                String.class);

                assertThat(outsiderExpensesResp.getStatusCode().value()).isEqualTo(403);

                // ---------- USER ONLY SEES THEIR OWN GROUPS ----------
                ResponseEntity<String> adminGroupsResp = client.exchange(
                                base + "/api/groups",
                                HttpMethod.GET,
                                new HttpEntity<>(adminHeaders),
                                String.class);

                assertThat(adminGroupsResp.getStatusCode().is2xxSuccessful()).isTrue();

                List<Map<String, Object>> adminGroups = mapper.readValue(adminGroupsResp.getBody(),
                                new TypeReference<>() {
                                });

                assertThat(adminGroups.stream().map(g -> g.get("groupId").toString()))
                                .contains(groupId.toString());

                ResponseEntity<String> outsiderGroupsResp = client.exchange(
                                base + "/api/groups",
                                HttpMethod.GET,
                                new HttpEntity<>(outsiderHeaders),
                                String.class);

                assertThat(outsiderGroupsResp.getStatusCode().is2xxSuccessful()).isTrue();

                List<Map<String, Object>> outsiderGroups = mapper.readValue(outsiderGroupsResp.getBody(),
                                new TypeReference<>() {
                                });

                assertThat(outsiderGroups.stream().map(g -> g.get("groupId").toString()))
                                .doesNotContain(groupId.toString());
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

                ResponseEntity<String> signupResp = client.postForEntity(base + "/api/auth/signup", signupReq,
                                String.class);

                assertThat(signupResp.getStatusCode().is2xxSuccessful()).isTrue();

                ResponseEntity<String> signinResp = client.postForEntity(base + "/api/auth/signin", signupReq,
                                String.class);

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

        private void assertAmountEquals(Object actualAmount, String expectedAmount) {
                BigDecimal actual = new BigDecimal(actualAmount.toString());
                BigDecimal expected = new BigDecimal(expectedAmount);
                assertThat(actual).isEqualByComparingTo(expected);
        }
}
