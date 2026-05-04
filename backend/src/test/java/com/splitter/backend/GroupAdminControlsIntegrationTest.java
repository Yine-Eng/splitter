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
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class GroupAdminControlsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void adminCanManageRoles() throws Exception {
        GroupAdminControlFixture fixture = createFixture();

        ResponseEntity<String> renameResp = exchange(
                fixture.client(),
                fixture.base() + "/api/groups/" + fixture.groupId() + "/rename",
                HttpMethod.PATCH,
                Map.of("name", "Renamed Group"),
                fixture.creatorHeaders());

        assertThat(renameResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> renameMap = mapper.readValue(renameResp.getBody(), new TypeReference<>() {
        });

        assertThat(renameMap.get("groupName").toString()).isEqualTo("Renamed Group");

        ResponseEntity<String> nonAdminRenameResp = exchange(
                fixture.client(),
                fixture.base() + "/api/groups/" + fixture.groupId() + "/rename",
                HttpMethod.PATCH,
                Map.of("name", "Bad Rename"),
                fixture.memberHeaders());

        assertThat(nonAdminRenameResp.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> outsiderRenameResp = exchange(
                fixture.client(),
                fixture.base() + "/api/groups/" + fixture.groupId() + "/rename",
                HttpMethod.PATCH,
                Map.of("name", "Outsider Rename"),
                fixture.outsiderHeaders());

        assertThat(outsiderRenameResp.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> promoteResp = fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/members/" + fixture.member().getId()
                        + "/promote",
                new HttpEntity<>("", fixture.creatorHeaders()),
                String.class);

        assertThat(promoteResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> promoteMap = mapper.readValue(promoteResp.getBody(), new TypeReference<>() {
        });

        assertThat(promoteMap.get("targetRole").toString()).isEqualTo("ADMIN");

        ResponseEntity<String> promotedAdminRenameResp = exchange(
                fixture.client(),
                fixture.base() + "/api/groups/" + fixture.groupId() + "/rename",
                HttpMethod.PATCH,
                Map.of("name", "Renamed By Promoted Admin"),
                fixture.memberHeaders());

        assertThat(promotedAdminRenameResp.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> demoteCreatorResp = fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/members/" + fixture.creator().getId()
                        + "/demote",
                new HttpEntity<>("", fixture.memberHeaders()),
                String.class);

        assertThat(demoteCreatorResp.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<String> demoteMemberResp = fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/members/" + fixture.member().getId()
                        + "/demote",
                new HttpEntity<>("", fixture.creatorHeaders()),
                String.class);

        assertThat(demoteMemberResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> demoteMap = mapper.readValue(demoteMemberResp.getBody(), new TypeReference<>() {
        });

        assertThat(demoteMap.get("targetRole").toString()).isEqualTo("MEMBER");
    }

    @Test
    void archivedGroupBlocksWrites() throws Exception {
        GroupAdminControlFixture fixture = createFixture();

        ResponseEntity<String> memberArchiveResp = fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/archive",
                new HttpEntity<>("", fixture.memberHeaders()),
                String.class);

        assertThat(memberArchiveResp.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> archiveResp = fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/archive",
                new HttpEntity<>("", fixture.creatorHeaders()),
                String.class);

        assertThat(archiveResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> archiveMap = mapper.readValue(archiveResp.getBody(), new TypeReference<>() {
        });

        assertThat(archiveMap.get("archived").toString()).isEqualTo("true");

        String newMemberUsername = "blocked_new_member_" + System.currentTimeMillis() + "@example.com";
        signupAndSignin(fixture.client(), fixture.base(), newMemberUsername, createJsonHeaders());

        ResponseEntity<String> addAfterArchiveResp = fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/members",
                new HttpEntity<>(mapper.writeValueAsString(Map.of("username", newMemberUsername)),
                        fixture.creatorHeaders()),
                String.class);

        assertThat(addAfterArchiveResp.getStatusCode().value()).isEqualTo(409);

        Map<String, Object> expenseRequest = Map.of(
                "groupId", fixture.groupId(),
                "description", "Blocked Expense",
                "amount", "20.00",
                "paidByUserId", fixture.creator().getId(),
                "participants", List.of(fixture.creator().getId(), fixture.member().getId()));

        ResponseEntity<String> expenseAfterArchiveResp = fixture.client().postForEntity(
                fixture.base() + "/api/expenses",
                new HttpEntity<>(mapper.writeValueAsString(expenseRequest), fixture.creatorHeaders()),
                String.class);

        assertThat(expenseAfterArchiveResp.getStatusCode().value()).isEqualTo(409);

        Map<String, Object> settlementRequest = Map.of(
                "groupId", fixture.groupId(),
                "toUserId", fixture.creator().getId(),
                "amount", "5.00",
                "note", "Blocked settlement");

        ResponseEntity<String> settlementAfterArchiveResp = fixture.client().postForEntity(
                fixture.base() + "/api/settlements",
                new HttpEntity<>(mapper.writeValueAsString(settlementRequest), fixture.memberHeaders()),
                String.class);

        assertThat(settlementAfterArchiveResp.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void adminActionsAreRecordedAsEvents() throws Exception {
        GroupAdminControlFixture fixture = createFixture();

        exchange(
                fixture.client(),
                fixture.base() + "/api/groups/" + fixture.groupId() + "/rename",
                HttpMethod.PATCH,
                Map.of("name", "Renamed Group"),
                fixture.creatorHeaders());

        fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/members/" + fixture.member().getId()
                        + "/promote",
                new HttpEntity<>("", fixture.creatorHeaders()),
                String.class);

        fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/members/" + fixture.member().getId()
                        + "/demote",
                new HttpEntity<>("", fixture.creatorHeaders()),
                String.class);

        fixture.client().postForEntity(
                fixture.base() + "/api/groups/" + fixture.groupId() + "/archive",
                new HttpEntity<>("", fixture.creatorHeaders()),
                String.class);

        List<Map<String, Object>> events = getEvents(fixture.client(), fixture.base(), fixture.groupId(),
                fixture.creatorHeaders());

        assertThat(events.stream().map(event -> event.get("type").toString()))
                .contains("GROUP_RENAMED", "MEMBER_PROMOTED", "MEMBER_DEMOTED", "GROUP_ARCHIVED");
    }

    private ResponseEntity<String> exchange(
            RestTemplate client,
            String url,
            HttpMethod method,
            Map<String, Object> body,
            HttpHeaders headers) throws Exception {
        return client.exchange(
                url,
                method,
                new HttpEntity<>(mapper.writeValueAsString(body), headers),
                String.class);
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

    private HttpHeaders createJsonHeaders() {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        return jsonHeaders;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private RestTemplate createRestTemplate() {
        RestTemplate client = new RestTemplate();
        client.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        client.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        return client;
    }

    private GroupAdminControlFixture createFixture() throws Exception {
        RestTemplate client = createRestTemplate();
        String base = "http://localhost:" + port;

        String creatorUsername = "admin_creator_" + System.currentTimeMillis() + "@example.com";
        String memberUsername = "admin_member_" + System.currentTimeMillis() + "@example.com";
        String outsiderUsername = "admin_outsider_" + System.currentTimeMillis() + "@example.com";

        HttpHeaders jsonHeaders = createJsonHeaders();

        String creatorToken = signupAndSignin(client, base, creatorUsername, jsonHeaders);
        String memberToken = signupAndSignin(client, base, memberUsername, jsonHeaders);
        String outsiderToken = signupAndSignin(client, base, outsiderUsername, jsonHeaders);

        HttpHeaders creatorHeaders = authHeaders(creatorToken);
        HttpHeaders memberHeaders = authHeaders(memberToken);
        HttpHeaders outsiderHeaders = authHeaders(outsiderToken);

        User creator = userRepository.findByUsername(creatorUsername).orElseThrow();
        User member = userRepository.findByUsername(memberUsername).orElseThrow();

        ResponseEntity<String> groupResp = client.postForEntity(
                base + "/api/groups?name=AdminControlGroup",
                new HttpEntity<>("", creatorHeaders),
                String.class);

        assertThat(groupResp.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> groupMap = mapper.readValue(groupResp.getBody(), new TypeReference<>() {
        });

        UUID groupId = UUID.fromString(groupMap.get("id").toString());

        ResponseEntity<String> addMemberResp = client.postForEntity(
                base + "/api/groups/" + groupId + "/members",
                new HttpEntity<>(mapper.writeValueAsString(Map.of("username", memberUsername)), creatorHeaders),
                String.class);

        assertThat(addMemberResp.getStatusCode().is2xxSuccessful()).isTrue();

        return new GroupAdminControlFixture(
                client,
                base,
                groupId,
                creator,
                member,
                creatorHeaders,
                memberHeaders,
                outsiderHeaders);
    }

    private record GroupAdminControlFixture(
            RestTemplate client,
            String base,
            UUID groupId,
            User creator,
            User member,
            HttpHeaders creatorHeaders,
            HttpHeaders memberHeaders,
            HttpHeaders outsiderHeaders) {
    }
}
