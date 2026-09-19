package com.clearing.netting.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack acceptance test for OPEN amount revision:
 * operator-only editing, validation on the wire, audit trail, state guards,
 * and re-netting using the revised amounts. Runs on in-memory H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObligationAmountRevisionIntegrationTest {

    private static final String TRADE_DATE = "2026-09-30";
    private static final AtomicInteger DAY_COUNTER = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String operatorToken;
    private String viewerToken;
    private String memberA;
    private String memberB;
    private String memberC;
    private String obligationAb;
    private String settleDate;

    @BeforeEach
    void setUp() throws Exception {
        // unique settle date per test so the shared Spring context/H2 data does not leak between tests
        settleDate = LocalDate.of(2027, 1, DAY_COUNTER.getAndIncrement()).toString();
        operatorToken = login("operator", "op123456");
        viewerToken = login("viewer", "view123456");
        memberA = createMember("Alpha Bank");
        memberB = createMember("Beta Securities");
        memberC = createMember("Gamma Clearing");

        // A -> B 100000 ; B -> C 60000 ; C -> A 40000
        obligationAb = createObligation(memberA, memberB, "100000.00000000");
        createObligation(memberB, memberC, "60000.00000000");
        createObligation(memberC, memberA, "40000.00000000");
    }

    @Test
    void reviseOpenAmountListReflectsAndAuditTrailPersisted() throws Exception {
        // 1) operator revises 100000 -> 120000
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"120000.00000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amount").value(120000.00000000));

        // 2) list immediately reflects the new amount
        MvcResult listResult = mockMvc.perform(get("/api/obligations")
                        .param("settleDate", settleDate)
                        .param("currency", "USD")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode revised = findById(list, obligationAb);
        assertAmountEquals("120000.00000000", revised.get("amount"));

        // 3) audit trail: old/new/operator/time
        MvcResult revResult = mockMvc.perform(get("/api/obligations/{id}/revisions", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode revisions = objectMapper.readTree(revResult.getResponse().getContentAsString());
        assertEquals(1, revisions.size());
        JsonNode rev = revisions.get(0);
        assertAmountEquals("100000.00000000", rev.get("oldAmount"));
        assertAmountEquals("120000.00000000", rev.get("newAmount"));
        assertEquals("operator", rev.get("operator").asText());
        assertNotNull(rev.get("revisedAt").asText());
        assertEquals(obligationAb, rev.get("obligationId").asText());
    }

    @Test
    void rejectsInvalidAmountsAtApiLayer() throws Exception {
        // zero
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest());
        // negative
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest());
        // missing
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        // malformed number format
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        // amount must remain unchanged
        MvcResult listResult = mockMvc.perform(get("/api/obligations")
                        .param("settleDate", settleDate)
                        .param("currency", "USD")
                        .header("Authorization", "Bearer " + operatorToken))
                .andReturn();
        assertAmountEquals("100000.00000000",
                findById(objectMapper.readTree(listResult.getResponse().getContentAsString()), obligationAb)
                        .get("amount"));
    }

    @Test
    void viewerCannotEditEvenByDirectApiCall() throws Exception {
        // viewer: no edit rights -> 403
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // no token -> 401
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nettedAndSettledCannotBeRevisedAndNettingUsesNewAmount() throws Exception {
        // revise 100000 -> 120000, then net
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"120000.00000000\"}"))
                .andExpect(status().isOk());

        MvcResult runResult = mockMvc.perform(post("/api/netting-runs")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settleDate\":\"" + settleDate + "\",\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode run = objectMapper.readTree(runResult.getResponse().getContentAsString());
        // hand calculation with revised amounts:
        // A: -120000 + 40000 = -80000 ; B: +120000 - 60000 = +60000 ; C: +60000 - 40000 = +20000
        JsonNode positions = run.get("positions");
        assertEquals(3, positions.size());
        assertAmountEquals("-80000.00000000", netOf(positions, memberA));
        assertAmountEquals("60000.00000000", netOf(positions, memberB));
        assertAmountEquals("20000.00000000", netOf(positions, memberC));
        assertEquals(0, run.get("sumNetAmount").decimalValue().signum());
        String runId = run.get("run").get("runId").asText();

        // NETTED obligation cannot be revised -> 409
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // settle the run -> SETTLED, still cannot revise -> 409
        mockMvc.perform(post("/api/netting-runs/{id}/settle", runId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/obligations/{id}/amount", obligationAb)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // ---- helpers ----

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createMember(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/members")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("memberId").asText();
    }

    private String createObligation(String payer, String payee, String amount) throws Exception {
        String body = String.format(
                "{\"payerMemberId\":\"%s\",\"payeeMemberId\":\"%s\",\"currency\":\"USD\","
                        + "\"amount\":\"%s\",\"tradeDate\":\"%s\",\"settleDate\":\"%s\"}",
                payer, payee, amount, TRADE_DATE, settleDate);
        MvcResult result = mockMvc.perform(post("/api/obligations")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("obligationId").asText();
    }

    private void assertAmountEquals(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()),
                () -> "expected " + expected + " but was " + actual);
    }

    private JsonNode findById(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.get("obligationId").asText())) {
                return node;
            }
        }
        throw new AssertionError("obligation not found in response: " + id);
    }

    private JsonNode netOf(JsonNode positions, String memberId) {
        for (JsonNode p : positions) {
            if (memberId.equals(p.get("memberId").asText())) {
                return p.get("netAmount");
            }
        }
        throw new AssertionError("position not found for member: " + memberId);
    }
}
