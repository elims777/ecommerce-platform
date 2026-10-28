package ru.rfsnab.userservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.enums.LinkStatus;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.services.LegalEntityService;
import ru.rfsnab.userservice.services.UserService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доступ к данным юрлица, статусу привязки и service-to-service эндпоинтам.
 * Поднимает MethodSecurityTestConfig, чтобы реально сработали @PreAuthorize.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MethodSecurityTestConfig.class)
@ActiveProfiles("test")
@DisplayName("Доступ к юрлицам и внутренним эндпоинтам")
class LegalEntityAccessTest {

    private static final long LEGAL_ID = 5L;
    private static final long LINKED_USER_ID = 10L;
    private static final long UNLINKED_USER_ID = 11L;
    private static final String LEGAL_URL = "/api/v1/legal-entities/" + LEGAL_ID;
    private static final String LINK_STATUS_URL = "/api/v1/legal-entities/link-status/" + LINKED_USER_ID;
    private static final String B2B_AUTHORITY = "CLIENT_B2B";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LegalEntityService legalEntityService;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        when(legalEntityService.getById(LEGAL_ID)).thenReturn(LegalEntity.builder()
                .id(LEGAL_ID).inn("1234567890").ogrn("1234567890123")
                .fullName("ООО Ромашка").director("Иванов Иван Иванович")
                .phone("+79001112233").email("company@example.com")
                .legalCity("Сыктывкар").legalStreet("Октябрьский проспект").legalBuilding("1")
                .verificationStatus(VerificationStatus.VERIFIED)
                .emailVerified(true).createdAt(LocalDateTime.now())
                .build());
        when(legalEntityService.getAddresses(LEGAL_ID)).thenReturn(List.of());
        when(legalEntityService.isLinkConfirmed(LINKED_USER_ID, LEGAL_ID)).thenReturn(true);
        when(legalEntityService.isLinkConfirmed(UNLINKED_USER_ID, LEGAL_ID)).thenReturn(false);
    }

    @Test
    @DisplayName("аноним получает 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "6", authorities = {"ROLE_USER", B2B_AUTHORITY})
    @DisplayName("чужое юрлицо (B2B с другим id) -> 403")
    void foreignB2bIsForbidden() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "11", authorities = "ROLE_USER")
    @DisplayName("B2C без подтверждённой привязки -> 403")
    void unlinkedB2cIsForbidden() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "6", authorities = {"ROLE_USER", B2B_AUTHORITY})
    @DisplayName("PATCH юрлица и POST/DELETE банковских счетов чужого B2B -> 403")
    void foreignB2bCannotModifyLegalEntity() throws Exception {
        assertModificationsForbidden();
    }

    @Test
    @WithMockUser(username = "11", authorities = "ROLE_USER")
    @DisplayName("PATCH юрлица и POST/DELETE банковских счетов B2C без привязки -> 403")
    void unlinkedB2cCannotModifyLegalEntity() throws Exception {
        assertModificationsForbidden();
    }

    private void assertModificationsForbidden() throws Exception {
        mockMvc.perform(patch(LEGAL_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"ООО Ромашка\",\"director\":\"Иванов Иван Иванович\","
                                + "\"phone\":\"+79001112233\",\"legalCity\":\"Сыктывкар\","
                                + "\"legalStreet\":\"Октябрьский проспект\",\"legalBuilding\":\"1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(LEGAL_URL + "/bank-accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Банк\",\"bik\":\"044525225\","
                                + "\"correspondentAccount\":\"30101810400000000225\","
                                + "\"settlementAccount\":\"40702810400000000001\",\"primary\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(LEGAL_URL + "/bank-accounts/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "5", authorities = "ROLE_USER")
    @DisplayName("B2C с тем же числовым id, что у юрлица, но без привязки -> 403")
    void b2cWithSameIdAsLegalEntityIsForbidden() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "5", authorities = {"ROLE_USER", B2B_AUTHORITY})
    @DisplayName("само юрлицо (B2B, id = id юрлица) -> 200")
    void ownerIsAllowed() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isOk());
        mockMvc.perform(get(LEGAL_URL + "/addresses")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("B2C с подтверждённой привязкой -> 200")
    void linkedB2cIsAllowed() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "1", authorities = "ROLE_ADMIN")
    @DisplayName("ADMIN -> 200")
    void adminIsAllowed() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "internal-service", authorities = "ROLE_INTERNAL")
    @DisplayName("INTERNAL -> 200")
    void internalIsAllowed() throws Exception {
        mockMvc.perform(get(LEGAL_URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "11", authorities = "ROLE_USER")
    @DisplayName("link-status чужого пользователя -> 403")
    void foreignLinkStatusIsForbidden() throws Exception {
        mockMvc.perform(get(LINK_STATUS_URL)).andExpect(status().isForbidden());
        mockMvc.perform(get(LINK_STATUS_URL + "/" + LEGAL_ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "10", authorities = {"ROLE_USER", B2B_AUTHORITY})
    @DisplayName("link-status: B2B с id, совпадающим с userId, -> 403")
    void b2bLinkStatusIsForbidden() throws Exception {
        mockMvc.perform(get(LINK_STATUS_URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("link-status себя -> 200")
    void ownLinkStatusIsAllowed() throws Exception {
        mockMvc.perform(get(LINK_STATUS_URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("link-status PENDING-привязки содержит fullName и inn юрлица")
    void pendingLinkStatusContainsCompanyNameAndInn() throws Exception {
        LegalEntity legal = LegalEntity.builder().id(LEGAL_ID).inn("1234567890").fullName("ООО Ромашка").build();
        when(legalEntityService.getAllLinksForUser(LINKED_USER_ID)).thenReturn(List.of(
                UserLegalEntity.builder().legalEntity(legal).linkStatus(LinkStatus.PENDING).build()));

        mockMvc.perform(get(LINK_STATUS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false))
                .andExpect(jsonPath("$.legalEntityId").value(LEGAL_ID))
                .andExpect(jsonPath("$.fullName").value("ООО Ромашка"))
                .andExpect(jsonPath("$.inn").value("1234567890"));
    }

    @Test
    @WithMockUser(username = "internal-service", authorities = "ROLE_INTERNAL")
    @DisplayName("link-status для INTERNAL -> 200")
    void internalLinkStatusIsAllowed() throws Exception {
        mockMvc.perform(get(LINK_STATUS_URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("/v1/users/inactive и /inactivity-email-sent обычному пользователю -> 403")
    void inactiveEndpointsForbiddenForUser() throws Exception {
        mockMvc.perform(get("/v1/users/inactive")).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/users/10/inactivity-email-sent")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "internal-service", authorities = "ROLE_INTERNAL")
    @DisplayName("/v1/users/inactive для INTERNAL -> 200")
    void inactiveEndpointAllowedForInternal() throws Exception {
        when(userService.findInactiveUsers(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/users/inactive")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("PUT password юрлица обычному пользователю -> 403")
    void setPasswordForbiddenForUser() throws Exception {
        mockMvc.perform(put(LEGAL_URL + "/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordHash\":\"hash\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "internal-service", authorities = "ROLE_INTERNAL")
    @DisplayName("PUT password юрлица для INTERNAL -> 200")
    void setPasswordAllowedForInternal() throws Exception {
        mockMvc.perform(put(LEGAL_URL + "/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordHash\":\"hash\"}"))
                .andExpect(status().isOk());
    }
}
