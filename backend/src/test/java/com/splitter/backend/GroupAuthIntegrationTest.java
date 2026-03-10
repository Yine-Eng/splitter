package com.splitter.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = com.splitter.backend.SplitterBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class GroupAuthIntegrationTest {

        @LocalServerPort
        private int port;

        private ObjectMapper mapper = new ObjectMapper();

        

        @Test
        void signupSigninCreateGroupFlow() throws Exception {
                var signup = Map.of("username", "itestuser", "password", "TestPass1!");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> signupReq = new HttpEntity<>(mapper.writeValueAsString(signup), headers);

                                RestTemplate plain = new RestTemplate();
                                plain.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
                                        @Override
                                        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                                                return false;
                                        }
                                });

                                String base = "http://localhost:" + port;

                                // signup
                                ResponseEntity<String> signupResp = plain.postForEntity(base + "/api/auth/signup", signupReq, String.class);
                assertThat(signupResp.getStatusCode().is2xxSuccessful()).isTrue();

                // signin
                                ResponseEntity<String> signinResp = plain.postForEntity(base + "/api/auth/signin", signupReq, String.class);
                assertThat(signinResp.getStatusCode().is2xxSuccessful()).isTrue();

                Map<String, Object> signinMap = mapper.readValue(signinResp.getBody(), new TypeReference<>() {});
                String token = signinMap.get("token").toString();
                System.out.println("TEST TOKEN: " + token);

                // create group with token
                HttpHeaders authHeaders = new HttpHeaders();
                authHeaders.set("Authorization", "Bearer " + token);
                authHeaders.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<String> createResp = plain.postForEntity(base + "/api/groups?name=ITestGroup", new HttpEntity<>("", authHeaders), String.class);
                assertThat(createResp.getStatusCode().is2xxSuccessful()).isTrue();

                Map<String, Object> groupMap = mapper.readValue(createResp.getBody(), new TypeReference<>() {});
                assertThat(groupMap.get("name")).isEqualTo("ITestGroup");

                // unauthenticated should be 4xx
                ResponseEntity<String> noAuthResp = plain.postForEntity(base + "/api/groups?name=NoAuth", new HttpEntity<>("", new HttpHeaders()), String.class);
                assertThat(noAuthResp.getStatusCode().is4xxClientError()).isTrue();
        }
}
