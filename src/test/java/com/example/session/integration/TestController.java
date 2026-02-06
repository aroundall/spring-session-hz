package com.example.session.integration;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test controller with logon and do-trans endpoints.
 */
@RestController
public class TestController {

    /**
     * Login endpoint - accepts any username/password and creates a session.
     * Always succeeds and returns the session ID in the USESSIONID cookie.
     */
    @PostMapping("/logon")
    public ResponseEntity<Map<String, Object>> logon(
            @RequestBody LoginRequest request,
            HttpSession session) {

        // Store user info in session - this is used by SessionAuthenticationFilter
        // to restore authentication on subsequent requests
        session.setAttribute("username", request.username());
        session.setAttribute("loginTime", System.currentTimeMillis());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Logged in successfully");
        response.put("username", request.username());
        response.put("sessionId", session.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Transaction endpoint - requires valid session.
     * Returns a simple response to confirm the session is working.
     */
    @GetMapping("/do-trans")
    public ResponseEntity<Map<String, Object>> doTransaction(HttpSession session) {
        String username = (String) session.getAttribute("username");
        Long loginTime = (Long) session.getAttribute("loginTime");

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) session.getAttribute("state");
        if (state == null) {
            state = new HashMap<>();
            state.put("history", new ArrayList<Long>());
            session.setAttribute("state", state);
        }

        @SuppressWarnings("unchecked")
        List<Long> history = (List<Long>) state.get("history");
        if (history == null) {
            history = new ArrayList<>();
            state.put("history", history);
        }

        // Intentionally mutate the nested collection without calling session.setAttribute again.
        history.add(System.currentTimeMillis());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Hello, " + username + "! Transaction completed.");
        response.put("sessionId", session.getId());
        response.put("loginTime", loginTime);
        response.put("currentTime", System.currentTimeMillis());
        response.put("historySize", history.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Login request DTO.
     */
    public record LoginRequest(String username, String password) {
    }
}
