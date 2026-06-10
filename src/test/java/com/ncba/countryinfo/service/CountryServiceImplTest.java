package com.ncba.countryinfo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ncba.countryinfo.client.SoapCountryClient;
import com.ncba.countryinfo.dto.CountryResponse;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.dto.LanguageDto;
import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.model.CountryInfo;
import com.ncba.countryinfo.model.Language;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.soap.TCountryInfo;
import com.ncba.countryinfo.soap.TLanguage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CountryServiceImplTest {

    @Mock
    private SoapCountryClient soapClient;
    @Mock
    private CountryInfoRepository repository;

    private CountryServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CountryServiceImpl(soapClient, repository);
    }

    private TCountryInfo sampleSoapInfo() {
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

    @Test
    void ingestByNameInsertsNewRecord() {
        when(soapClient.getIsoCode("Kenya")).thenReturn("KE");
        when(soapClient.getFullCountryInfo("KE")).thenReturn(sampleSoapInfo());
        when(repository.findByIsoCode("KE")).thenReturn(Optional.empty());
        when(repository.save(any(CountryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

        CountryResponse response = service.ingestByName("kenya");

        assertThat(response.getName()).isEqualTo("Kenya");
        assertThat(response.getIsoCode()).isEqualTo("KE");
        assertThat(response.getCapitalCity()).isEqualTo("Nairobi");
        assertThat(response.getLanguages()).hasSize(1);
        assertThat(response.getLanguages().get(0).getName()).isEqualTo("Swahili");

        ArgumentCaptor<CountryInfo> captor = ArgumentCaptor.forClass(CountryInfo.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLanguages()).hasSize(1);
    }

    @Test
    void ingestByNameUpdatesExistingRecord() {
        CountryInfo existing = new CountryInfo();
        existing.setId(7L);
        existing.setPublicId("existing-uuid");
        existing.setIsoCode("KE");
        existing.addLanguage(new Language("old", "Old"));

        when(soapClient.getIsoCode("Kenya")).thenReturn("KE");
        when(soapClient.getFullCountryInfo("KE")).thenReturn(sampleSoapInfo());
        when(repository.findByIsoCode("KE")).thenReturn(Optional.of(existing));
        when(repository.save(any(CountryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

        CountryResponse response = service.ingestByName("KENYA");

        // Public id is preserved across the upsert; sequential PK is never exposed.
        assertThat(response.getId()).isEqualTo("existing-uuid");
        assertThat(response.getLanguages()).hasSize(1);
        assertThat(response.getLanguages().get(0).getIsoCode()).isEqualTo("swa");
    }

    @Test
    void ingestByNameThrowsWhenIsoCodeMissing() {
        when(soapClient.getIsoCode("Atlantis")).thenReturn("");

        assertThatThrownBy(() -> service.ingestByName("atlantis"))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("No ISO code");

        verify(soapClient, never()).getFullCountryInfo(any());
        verify(repository, never()).save(any());
    }

    @Test
    void ingestByNameThrowsWhenIsoCodeIsNotFoundSentinel() {
        // Upstream returns a textual sentinel instead of a real ISO code.
        when(soapClient.getIsoCode("Wakanda")).thenReturn("No country found by that name");

        assertThatThrownBy(() -> service.ingestByName("wakanda"))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("No ISO code");

        verify(soapClient, never()).getFullCountryInfo(any());
        verify(repository, never()).save(any());
    }

    @Test
    void ingestByNameThrowsWhenFullInfoHasBlankIsoCode() {
        when(soapClient.getIsoCode("Wakanda")).thenReturn("WK");
        TCountryInfo sentinel = new TCountryInfo();
        sentinel.setIsoCode("");
        sentinel.setName("Country not found in the database");
        when(soapClient.getFullCountryInfo("WK")).thenReturn(sentinel);

        assertThatThrownBy(() -> service.ingestByName("wakanda"))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("could not be resolved");

        verify(repository, never()).save(any());
    }

    @Test
    void getAllReturnsMappedList() {
        CountryInfo entity = new CountryInfo();
        entity.setIsoCode("KE");
        entity.setName("Kenya");
        when(repository.findAll()).thenReturn(List.of(entity));

        List<CountryResponse> all = service.getAll();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getIsoCode()).isEqualTo("KE");
    }

    @Test
    void getByIdReturnsRecord() {
        CountryInfo entity = new CountryInfo();
        entity.setId(1L);
        entity.setPublicId("pub-1");
        entity.setIsoCode("KE");
        when(repository.findByPublicId("pub-1")).thenReturn(Optional.of(entity));

        assertThat(service.getById("pub-1").getId()).isEqualTo("pub-1");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(repository.findByPublicId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void updateModifiesFieldsAndLanguages() {
        CountryInfo entity = new CountryInfo();
        entity.setId(1L);
        entity.setPublicId("pub-1");
        entity.setIsoCode("KE");
        entity.addLanguage(new Language("old", "Old"));
        when(repository.findByPublicId("pub-1")).thenReturn(Optional.of(entity));
        when(repository.save(any(CountryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

        CountryUpdateRequest request = new CountryUpdateRequest();
        request.setName("kenya");
        request.setCapitalCity("Nairobi");
        request.setLanguages(List.of(new LanguageDto("eng", "English")));

        CountryResponse response = service.update("pub-1", request);

        assertThat(response.getName()).isEqualTo("Kenya");
        assertThat(response.getCapitalCity()).isEqualTo("Nairobi");
        assertThat(response.getLanguages()).hasSize(1);
        assertThat(response.getLanguages().get(0).getIsoCode()).isEqualTo("eng");
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repository.findByPublicId("missing")).thenReturn(Optional.empty());
        CountryUpdateRequest request = new CountryUpdateRequest();
        request.setName("Kenya");

        assertThatThrownBy(() -> service.update("missing", request))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void deleteRemovesWhenPresent() {
        CountryInfo entity = new CountryInfo();
        entity.setPublicId("pub-1");
        when(repository.findByPublicId("pub-1")).thenReturn(Optional.of(entity));

        service.delete("pub-1");

        verify(repository).delete(entity);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.findByPublicId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(CountryNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
