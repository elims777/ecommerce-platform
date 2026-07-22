package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.CreatePriceListRequest;
import ru.rfsnab.productservice.dto.PriceListResponse;
import ru.rfsnab.productservice.service.PriceListService;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/price-lists")
@RequiredArgsConstructor
public class PriceListController {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PriceListService priceListService;

    /**
     * Запросить формирование прайс-листа по списку категорий
     */
    @PostMapping
    public ResponseEntity<PriceListResponse> create(
            @Valid @RequestBody CreatePriceListRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Client-Type") String clientType
    ) {
        PriceListResponse response = priceListService.create(userId, clientType, request.categoryIds());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Получить список своих запросов на прайс-лист
     */
    @GetMapping
    public ResponseEntity<List<PriceListResponse>> getMyRequests(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(priceListService.getMyRequests(userId));
    }

    /**
     * Скачать сформированный прайс-лист
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) {
        ResponseInputStream<GetObjectResponse> stream = priceListService.download(userId, id);
        String filename = "price-list-" + LocalDate.now().format(FILE_DATE_FORMAT) + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(new InputStreamResource(stream));
    }
}
