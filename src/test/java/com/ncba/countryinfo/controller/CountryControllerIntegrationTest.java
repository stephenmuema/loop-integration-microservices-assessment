package com.ncba.countryinfo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncba.countryinfo.client.SoapCountryClient;
import com.ncba.countryinfo.dto.CountryNameRequest;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.exception.SoapIntegrationException;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.soap.TCountryInfo;
import com.ncba.countryinfo.soap.TLanguage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end web-layer tests: real Spring context + H2, SOAP client mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CountryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CountryInfoRepository repository;

    @MockBean
    private SoapCountryClient soapClient;

    @BeforeEach
    void cleanDb() {
        repository.deleteAll();
    }

    private TCountryInfo kenyaSoap() {
        TLanguage swahili = new TLanguage();
        swahili.setIsoCode("swa");
        swahili.setName("Swahili");
        TCountryInfo info = new TCountryInfo();
        info.setIsoCode("KE");
        info.setName("Kenya");
        info.setCapitalCity("Nairobi");
        info.setPhoneCode("254");
        info.setContinentCode("AF");
        info.setCurrencyISOCode("KES");
        info.setCountryFlag("http://flags/kenya.jpg");
        info.setLanguages(List.of(swahili));
        return info;
    }

    private String body(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void postCreatesCountryFromSoap() throws Exception {
        when(soapClient.getIsoCode("Kenya")).thenReturn("KE");
        when(soapClient.getFullCountryInfo("KE")).thenReturn(kenyaSoap());

        mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new CountryNameRequest("kenya"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Kenya")))
                .andExpect(jsonPath("$.isoCode", is("KE")))
                .andExpect(jsonPath("$.capitalCity", is("Nairobi")))
                .andExpect(jsonPath("$.languages", hasSize(1)))
                .andExpect(jsonPath("$.languages[0].name", is("Swahili")));
    }

    @Test
    void postUpsertsOnRepeatedIsoCode() throws Exception {
        when(soapClient.getIsoCode("Kenya")).thenReturn("KE");
        when(soapClient.getFullCountryInfo("KE")).thenReturn(kenyaSoap());

        mockMvc.perform(post("/api/countries").contentType(MediaType.APPLICATION_JSON)
                .content(body(new CountryNameRequest("kenya")))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/countries").contentType(MediaType.APPLICATION_JSON)
                .content(body(new CountryNameRequest("KENYA")))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void postWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new CountryNameRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("name")));
    }

    @Test
    void postReturns502WhenSoapFails() throws Exception {
        when(soapClient.getIsoCode("Kenya"))
                .thenThrow(new SoapIntegrationException("upstream unavailable"));

        mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new CountryNameRequest("kenya"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", is(502)));
    }

    @Test
    void postUnknownCountryReturns404() throws Exception {
        when(soapClient.getIsoCode("Wakanda")).thenReturn("No country found by that name");

        mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new CountryNameRequest("wakanda"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        String missingId = "00000000-0000-0000-0000-000000000000";
        mockMvc.perform(get("/api/countries/" + missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/api/countries/" + missingId)));
    }

    @Test
    void createdIdIsOpaqueUuidNotSequential() throws Exception {
        when(soapClient.getIsoCode("Kenya")).thenReturn("KE");
        when(soapClient.getFullCountryInfo("KE")).thenReturn(kenyaSoap());

        String created = mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new CountryNameRequest("kenya"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();
        // Must be a UUID, never a guessable sequential integer.
        java.util.UUID.fromString(id);
        org.assertj.core.api.Assertions.assertThat(id).doesNotMatch("\\d+");
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        when(soapClient.getIsoCode("Kenya")).thenReturn("KE");
        when(soapClient.getFullCountryInfo("KE")).thenReturn(kenyaSoap());

        // Create
        String created = mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new CountryNameRequest("kenya"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        // Read by id
        mockMvc.perform(get("/api/countries/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isoCode", is("KE")));

        // Update
        CountryUpdateRequest update = new CountryUpdateRequest();
        update.setName("republic of kenya");
        update.setCapitalCity("Nairobi City");
        mockMvc.perform(put("/api/countries/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Republic Of Kenya")))
                .andExpect(jsonPath("$.capitalCity", is("Nairobi City")));

        // Delete
        mockMvc.perform(delete("/api/countries/" + id)).andExpect(status().isNoContent());

        // Confirm gone
        mockMvc.perform(get("/api/countries/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void updateMissingReturns404() throws Exception {
        CountryUpdateRequest update = new CountryUpdateRequest();
        update.setName("Kenya");
        mockMvc.perform(put("/api/countries/11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(update)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        mockMvc.perform(delete("/api/countries/22222222-2222-2222-2222-222222222222"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithBlankNameReturns400() throws Exception {
        when(soapClient.getIsoCode(eq("Kenya"))).thenReturn("KE");
        when(soapClient.getFullCountryInfo("KE")).thenReturn(kenyaSoap());
        String created = mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new CountryNameRequest("kenya"))))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        CountryUpdateRequest update = new CountryUpdateRequest();
        update.setName("");
        mockMvc.perform(put("/api/countries/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(update)))
                .andExpect(status().isBadRequest());
    }
}
