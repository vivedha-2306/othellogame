package com.example.othello.controller;

import com.example.othello.model.GameState;
import com.example.othello.service.OthelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.HashMap;


@RestController
@RequestMapping("/api/othello")
@CrossOrigin(origins = "*")
public class GameController {

    @Autowired
    private OthelloService service;

    @GetMapping("/state")
    public GameState getState() {
        return service.getState();
    }
    @PostMapping("/move")
    public GameState makeMove(
            @RequestParam(required = false) Integer row,
            @RequestParam(required = false) Integer col,
            @RequestParam(defaultValue = "false") boolean ai) {

        if (ai) {
            // Let AI make a move
            return service.makeAIMove();
        } else {
            // Let human make a move
            return service.makeMove(row, col);
        }
    }

    @PostMapping("/start")
    public GameState startGame(@RequestParam String playerName) {
        GameState state = new GameState();
        state.setBlackPlayerName(playerName);
        return service.startNewGame(state);
    }


    @PostMapping("/submitCode")
    public Map<String, Object> submitCode(@RequestBody Map<String, String> payload) {
        String code = payload.getOrDefault("code", "");
        return service.submitCode(code);
    }


    @GetMapping("/codingQuestion")
    public Map<String, Object> getCodingQuestion() {
        return service.getActiveQuestion();
    }


    @PostMapping("/reset")
    public GameState resetGame() {
        return service.resetGame();
    }

    // ---------------- Admin endpoints (kept inside same controller) ----------------

    // Hardcoded admin credentials (as requested)
    private static final String ADMIN_EMAIL = "admin@gmail.com";
    private static final String ADMIN_PASSWORD = "admin123";

    @PostMapping("/admin/login")
    public Map<String, Object> adminLogin(@RequestBody Map<String, String> payload) {
        String email = payload.getOrDefault("email", "");
        String password = payload.getOrDefault("password", "");
        Map<String, Object> resp = new HashMap<>();
        if (ADMIN_EMAIL.equals(email) && ADMIN_PASSWORD.equals(password)) {
            resp.put("success", true);
            resp.put("message", "Login successful");
        } else {
            resp.put("success", false);
            resp.put("message", "Invalid credentials");
        }
        return resp;
    }

    @PostMapping("/admin/upload")
    public Map<String, Object> adminUpload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> resp = new HashMap<>();
        try {
            if (file == null || file.isEmpty()) {
                resp.put("success", false);
                resp.put("message", "File is empty");
                return resp;
            }
            boolean ok = service.uploadQuestionFile(file);
            resp.put("success", ok);
            resp.put("message", ok ? "Question uploaded" : "Failed to parse file");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "Error: " + e.getMessage());
        }
        return resp;
    }

    @GetMapping("/admin/question")
    public Map<String, Object> adminGetQuestion() {
        return service.getActiveQuestion();
    }




}






