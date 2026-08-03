package com.datavault.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "datavault.storage.type=local")
public class FileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;

    @BeforeEach
    public void setupAuthToken() throws Exception {
        Map<String, String> loginRequest = Map.of(
            "username", "admin",
            "password", "password123"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        this.jwtToken = (String) responseMap.get("token");
    }

    @Test
    public void testCompleteFileUploadDownloadStreamAndDeleteLifecycle() throws Exception {
        byte[] fileContent = "Hello DataVault High Performance NIO Streaming Storage System!".getBytes();
        String fileName = "integration_test.txt";

        // 1. Upload File
        MvcResult uploadResult = mockMvc.perform(post("/api/v1/files/upload")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .header("X-File-Name", fileName)
                .contentType(MediaType.TEXT_PLAIN_VALUE)
                .content(fileContent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fileName").value(fileName))
                .andExpect(jsonPath("$.sha256Checksum").exists())
                .andReturn();

        Map<?, ?> uploadResponseMap = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), Map.class);
        String fileId = (String) uploadResponseMap.get("id");
        String expectedChecksum = (String) uploadResponseMap.get("sha256Checksum");

        // 2. Download File
        mockMvc.perform(get("/api/v1/files/download/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + expectedChecksum + "\""))
                .andExpect(content().bytes(fileContent));

        // 3. Stream Range Request (HTTP 206)
        mockMvc.perform(get("/api/v1/files/stream/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .header(HttpHeaders.RANGE, "bytes=0-10"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-10/" + fileContent.length))
                .andExpect(content().bytes("Hello DataV".getBytes()));

        // 4. List Files
        mockMvc.perform(get("/api/v1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists());

        // 5. Delete File
        mockMvc.perform(delete("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        // 6. Verify Deleted
        mockMvc.perform(get("/api/v1/files/download/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }
}
