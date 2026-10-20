package ru.rfsnab.orderservice.delivery.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import ru.rfsnab.orderservice.delivery.client.CarrierClient;
import ru.rfsnab.orderservice.delivery.client.TerminalData;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.mapper.CarrierMapper;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierSyncReport.SkippedItem;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierSyncReport;
import ru.rfsnab.orderservice.delivery.models.dto.event.CarrierSyncEvent;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.SyncStatus;
import ru.rfsnab.orderservice.delivery.models.entity.enums.SyncTrigger;
import ru.rfsnab.orderservice.delivery.repository.CarrierRepository;
import ru.rfsnab.orderservice.delivery.repository.CarrierTerminalRepository;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Синхронизация справочника терминалов ТК (спек 6.2): ночью, при старте для пустых ТК и по кнопке в админке.
 * Каждая ТК — в своей транзакции; при ошибке или пустом ответе данные ТК не трогаются.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierSyncService {

    /** Сколько пропущенных записей попадает в отчёт, остальные — счётчиком. */
    public static final int MAX_SKIPPED_ITEMS = 50;

    /** Ночной запуск: 23:00 UTC = 02:00 МСК. */
    static final String NIGHTLY_CRON = "0 0 23 * * *";

    /** Если валидных точек меньше этой доли от активных, деактивация пропускается. */
    static final double SUSPICIOUS_RESPONSE_RATIO = 0.5;

    private static final double MAX_LATITUDE = 90.0;
    private static final double MAX_LONGITUDE = 180.0;

    private final List<CarrierClient> clients;
    private final CarrierRepository carrierRepository;
    private final CarrierTerminalRepository terminalRepository;
    private final DeliveryService deliveryService;
    private final OrderKafkaProducer kafkaProducer;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final CarrierMapper carrierMapper;

    /**
     * Ручная синхронизация одной ТК (кнопка в админке). Ошибка ТК не бросается, а возвращается отчётом ERROR.
     */
    public CarrierSyncReport syncManually(CarrierCode code) {
        Carrier carrier = carrierRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("carriers not initialized: " + code));
        CarrierSyncReport report = sync(carrier);
        publish(SyncTrigger.MANUAL, List.of(report));
        return report;
    }

    /**
     * Ночная синхронизация всех ТК, 02:00 МСК; итог — одним событием. Ненастроенные ТК в событие не попадают
     * (иначе менеджеру каждую ночь приходило бы письмо о ТК без ключа); если отчётов не осталось — события нет.
     */
    @Scheduled(cron = NIGHTLY_CRON, zone = "UTC")
    public void syncAllNightly() {
        List<CarrierSyncReport> reports = carrierRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::sync).filter(r -> r.status() != SyncStatus.NOT_CONFIGURED).toList();
        if (!reports.isEmpty()) {
            publish(SyncTrigger.SCHEDULED, reports);
        }
    }

    /** После старта сервиса синхронизирует настроенные ТК, у которых справочник пуст. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncEmptyOnStartup() {
        List<CarrierSyncReport> reports = carrierRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(c -> clientFor(c.getCode()).isConfigured() && !terminalRepository.existsByCarrierCode(c.getCode()))
                .map(this::sync).toList();
        if (!reports.isEmpty()) {
            publish(SyncTrigger.STARTUP, reports);
        }
    }

    /** Одна ТК за раз: ручной запуск не должен пересечься с ночным. */
    private synchronized CarrierSyncReport sync(Carrier carrier) {
        Instant started = Instant.now();
        CarrierCode code = carrier.getCode();
        CarrierClient client = clientFor(code);
        if (!client.isConfigured()) {
            CarrierSyncReport notConfigured =
                    withoutCounters(carrier, SyncStatus.NOT_CONFIGURED, "Ключ API не настроен", 0);
            transactionTemplate.executeWithoutResult(s -> storeReport(code, notConfigured, false));
            return notConfigured;
        }
        CarrierSyncReport report;
        try {
            Optional<List<TerminalData>> fetched = client.fetchTerminals();
            if (fetched.isEmpty()) {
                CarrierSyncReport unchanged = withoutCounters(carrier, SyncStatus.OK,
                        "Справочник не изменился с прошлой загрузки", elapsedMs(started));
                transactionTemplate.executeWithoutResult(s -> storeReport(code, unchanged, true));
                report = unchanged;
            } else if (fetched.get().isEmpty()) {
                throw new CarrierApiException("ТК вернула пустой список точек");
            } else {
                report = transactionTemplate.execute(s -> persist(code, fetched.get(), started));
            }
        } catch (RuntimeException e) {
            // иначе справочник, который не удалось сохранить, больше не будет загружен (hash уже запомнен)
            client.forgetFetchState();
            logFailure(code, e);
            CarrierSyncReport failed =
                    withoutCounters(carrier, SyncStatus.ERROR, describe(e), elapsedMs(started));
            try {
                transactionTemplate.executeWithoutResult(s -> storeReport(code, failed, false));
            } catch (RuntimeException storeFailure) {
                // недоступная БД не должна прерывать проход по остальным ТК и старт приложения
                log.error("Не удалось сохранить отчёт об ошибке синхронизации {}", code, storeFailure);
            }
            report = failed;
        }
        deliveryService.evictCache(code);
        return report;
    }

    /** Сохраняет точки и итог ТК; вызывается внутри транзакции. */
    private CarrierSyncReport persist(CarrierCode code, List<TerminalData> data, Instant started) {
        Carrier carrier = carrierRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("carriers not initialized: " + code));
        List<CarrierTerminal> existing = terminalRepository.findAllByCarrierCode(code);
        Map<String, CarrierTerminal> byExternalId = existing.stream()
                .collect(Collectors.toMap(CarrierTerminal::getExternalId, Function.identity()));
        long activeBefore = existing.stream().filter(CarrierTerminal::isActive).count();

        Instant now = Instant.now();
        Set<String> seen = new HashSet<>();
        List<SkippedItem> skipped = new ArrayList<>();
        List<CarrierTerminal> added = new ArrayList<>();
        int updated = 0;
        for (TerminalData point : data) {
            String reason = invalidReason(point);
            if (reason == null && !seen.add(point.externalId())) {
                reason = "Повтор ID в ответе ТК";
            }
            if (reason != null) {
                skipped.add(new SkippedItem(point.externalId(), reason));
                continue;
            }
            CarrierTerminal candidate = carrierMapper.toTerminal(point, code, now);
            CarrierTerminal current = byExternalId.get(point.externalId());
            if (current == null) {
                added.add(candidate);
            } else if (differs(current, candidate) || !current.isActive()) {
                carrierMapper.updateTerminal(current, candidate);
                current.setActive(true);
                current.setSyncedAt(now);
                updated++;
            }
        }
        if (seen.isEmpty()) {
            throw new CarrierApiException("Среди " + data.size() + " полученных точек нет ни одной валидной");
        }

        int deactivated = 0;
        String warning = null;
        if (activeBefore > 0 && seen.size() < activeBefore * SUSPICIOUS_RESPONSE_RATIO) {
            warning = "Подозрительный ответ ТК: получено " + seen.size() + " из " + activeBefore
                    + ", деактивация пропущена";
            log.warn("{}: {}", code, warning);
        } else {
            for (CarrierTerminal terminal : existing) {
                if (terminal.isActive() && !seen.contains(terminal.getExternalId())) {
                    terminal.setActive(false);
                    terminal.setSyncedAt(now);
                    deactivated++;
                }
            }
        }
        terminalRepository.saveAll(added);
        long activeNow = terminalRepository.countByCarrierCodeAndActiveTrue(code);

        SyncStatus status = warning != null || !skipped.isEmpty() ? SyncStatus.WARNING : SyncStatus.OK;
        CarrierSyncReport report = new CarrierSyncReport(code, carrier.getName(), status, data.size(), added.size(),
                updated, deactivated, skipped.size(), skipped.stream().limit(MAX_SKIPPED_ITEMS).toList(),
                Math.max(0, skipped.size() - MAX_SKIPPED_ITEMS), warning, elapsedMs(started), Instant.now());
        carrier.setActiveTerminalsCount((int) activeNow);
        carrier.setTerminalsSyncedAt(now);
        carrier.setLastSyncReport(toJson(report));
        carrierRepository.save(carrier);
        return report;
    }

    /** Записывает итог в carriers.last_sync_report; вызывается внутри транзакции. */
    private void storeReport(CarrierCode code, CarrierSyncReport report, boolean markSynced) {
        Carrier carrier = carrierRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("carriers not initialized: " + code));
        carrier.setLastSyncReport(toJson(report));
        if (markSynced) {
            carrier.setTerminalsSyncedAt(Instant.now());
        }
        carrierRepository.save(carrier);
    }

    private static String invalidReason(TerminalData point) {
        if (!StringUtils.hasText(point.externalId())) {
            return "Нет ID точки";
        }
        if (!StringUtils.hasText(point.city())) {
            return "Нет города";
        }
        if (!StringUtils.hasText(point.address())) {
            return "Нет адреса";
        }
        if (point.latitude() == null || point.longitude() == null) {
            return "Нет координат";
        }
        if (!(Math.abs(point.latitude()) <= MAX_LATITUDE) || !(Math.abs(point.longitude()) <= MAX_LONGITUDE)) {
            return "Координаты вне диапазона";
        }
        return null;
    }

    /** Отличается ли содержимое точек (без id, active и syncedAt); координаты сравниваются без учёта scale. */
    private static boolean differs(CarrierTerminal a, CarrierTerminal b) {
        return a.getPointType() != b.getPointType()
                || !Objects.equals(a.getCity(), b.getCity())
                || !Objects.equals(a.getName(), b.getName())
                || !Objects.equals(a.getAddress(), b.getAddress())
                || a.getLatitude().compareTo(b.getLatitude()) != 0
                || a.getLongitude().compareTo(b.getLongitude()) != 0
                || !Objects.equals(a.getWorkingHours(), b.getWorkingHours())
                || !Objects.equals(a.getPhone(), b.getPhone());
    }

    /** Отчёт без счётчиков — для ERROR, NOT_CONFIGURED и «справочник не изменился». */
    private CarrierSyncReport withoutCounters(Carrier carrier, SyncStatus status, String message, long durationMs) {
        return new CarrierSyncReport(carrier.getCode(), carrier.getName(), status, 0, 0, 0, 0, 0,
                List.of(), 0, message, durationMs, Instant.now());
    }

    private CarrierClient clientFor(CarrierCode code) {
        return clients.stream().filter(c -> c.code() == code).findFirst()
                .orElseThrow(() -> new IllegalStateException("No CarrierClient for " + code));
    }

    private void publish(SyncTrigger trigger, List<CarrierSyncReport> reports) {
        try {
            kafkaProducer.sendCarrierSyncCompleted(new CarrierSyncEvent(CarrierSyncEvent.TYPE, trigger, reports));
        } catch (RuntimeException e) {
            log.error("Не удалось отправить отчёт синхронизации терминалов в Kafka: {}", e.getMessage());
        }
    }

    private String toJson(CarrierSyncReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать отчёт синхронизации", e);
        }
    }

    private static long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }

    /** Текст ошибки для отчёта и логов: без URL запроса и ключей, которые бывают в сообщениях клиентов. */
    private static String describe(RuntimeException e) {
        if (e instanceof CarrierApiException) {
            return e.getMessage();
        }
        if (e instanceof RestClientResponseException response) {
            return "ТК ответила HTTP " + response.getStatusCode().value();
        }
        if (e instanceof ResourceAccessException) {
            return "ТК недоступна: таймаут или ошибка соединения";
        }
        return "Ошибка синхронизации: " + e.getClass().getSimpleName();
    }

    private static void logFailure(CarrierCode code, RuntimeException e) {
        boolean known = e instanceof CarrierApiException || e instanceof RestClientResponseException
                || e instanceof ResourceAccessException;
        if (known) {
            log.error("Синхронизация терминалов {} не удалась: {}", code, describe(e));
        } else {
            log.error("Синхронизация терминалов {} не удалась", code, e);
        }
    }
}
