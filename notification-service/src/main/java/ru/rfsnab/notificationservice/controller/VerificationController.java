package ru.rfsnab.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.notificationservice.models.VerificationResponse;
import ru.rfsnab.notificationservice.service.EmailVerificationTokenService;

@Slf4j
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final EmailVerificationTokenService tokenService;

    @PostMapping("/verify")
    public ResponseEntity<VerificationResponse> verify(@RequestParam String token){
        log.info("Verifying token: {}", token);

        VerificationResponse response = tokenService.verifyToken(token);

        if(response.isValid()){
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
