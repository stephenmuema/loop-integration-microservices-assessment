package com.ncba.countryinfo.controller;

import com.ncba.countryinfo.dto.CountryNameRequest;
import com.ncba.countryinfo.dto.CountryResponse;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.service.CountryService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for country information.
 *
 * <p>Exposes the SOAP-backed ingestion endpoint plus full CRUD over the
 * persisted records under {@code /api/countries}.</p>
 */
@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private static final Logger log = LoggerFactory.getLogger(CountryController.class);

    private final CountryService countryService;

    public CountryController(CountryService countryService) {
        this.countryService = countryService;
    }

    /**
     * Ingests a country by name. Resolves the ISO code and full detail via SOAP
     * and upserts the record.
     *
     * @param request body carrying the (non-blank) country name
     * @return the persisted country with HTTP 201 Created
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CountryResponse create(@Valid @RequestBody CountryNameRequest request) {
        log.info("POST /api/countries name='{}'", request.getName());
        return countryService.ingestByName(request.getName());
    }

    /**
     * Fetches all persisted countries.
     *
     * @return list of countries with HTTP 200 OK
     */
    @GetMapping
    public List<CountryResponse> getAll() {
        log.info("GET /api/countries");
        return countryService.getAll();
    }

    /**
     * Fetches a single country by its opaque public id (UUID).
     *
     * @param id opaque public identifier
     * @return the country with HTTP 200 OK, or 404 if not found
     */
    @GetMapping("/{id}")
    public CountryResponse getById(@PathVariable String id) {
        log.info("GET /api/countries/{}", id);
        return countryService.getById(id);
    }

    /**
     * Updates the mutable fields of a country.
     *
     * @param id      opaque public identifier
     * @param request validated update payload
     * @return the updated country with HTTP 200 OK, 400 on validation failure,
     *         or 404 if not found
     */
    @PutMapping("/{id}")
    public CountryResponse update(@PathVariable String id, @Valid @RequestBody CountryUpdateRequest request) {
        log.info("PUT /api/countries/{}", id);
        return countryService.update(id, request);
    }

    /**
     * Deletes a country by its opaque public id (UUID).
     *
     * @param id opaque public identifier
     * @return HTTP 204 No Content, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        log.info("DELETE /api/countries/{}", id);
        countryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
