package ru.rfsnab.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rfsnab.notificationservice.service.EmailService;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    @MockitoBean
    private EmailService emailService;

    @Test
    void contextLoads() {
    }

}
