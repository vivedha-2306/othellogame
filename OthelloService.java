package com.example.othello.service;

import com.example.othello.model.GameState;
import com.example.othello.util.GoogleSheetsUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Service
public class OthelloService {

    private static final String JUDGE0_API_URL = "https://judge0-ce.p.rapidapi.com/submissions";

    @Value("${rapidapi.key:YOUR_RAPIDAPI_KEY_HERE}")
    private String rapidApiKey;

    @Value("${rapidapi.host:judge0-ce.p.rapidapi.com}")
    private String rapidApiHost;

    @Value("${google.sheet.id}")
    private String spreadsheetId;

    private static final String DEFAULT_CODING_QUESTION = "Write a C program to reverse a string";
    private static final String CODING_QUESTION = DEFAULT_CODING_QUESTION;

    private volatile String uploadedQuestion = null;
    private volatile List<String> uploadedTestCases = new ArrayList<>();

    private final Random random = new Random();
    private GameState currentState = new GameState();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------- Duration tracking ----------------
    private LocalDateTime gameStartTime = null;
    private LocalDateTime gameEndTime = null;
    private double lastCodeSubmitDurationMin = 0.0;

    // Code writing time tracking (frontend should call markCodeStart() when user focuses editor)
    private LocalDateTime codeStartTime = null;
    private LocalDateTime codeEndTime = null;
    private boolean isCodeStarted = false;

    public OthelloService() {
        System.out.println("✅ OthelloService initialized.");
    }

    public GameState getState() {
        return currentState;
    }

    // ---------------- Gameplay Logic ----------------
    public GameState makeMove(int row, int col) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);

        if (row < 0 || col < 0 || row > 7 || col > 7) return currentState;

        if (applyMove(row, col, currentState.getCurrentPlayer())) {
            if (currentState.getCurrentPlayer() == 1) {
                String player = currentState.getBlackPlayerName() != null ? currentState.getBlackPlayerName() : "Black";
                GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                        player,
                        String.valueOf(col + 1),
                        String.valueOf(row + 1),
                        "",
                        "",
                        timestamp
                ));
            }
            switchPlayer();
        }

        checkGameOver();
        return currentState;
    }

    public GameState makeAIMove() {
        int bestRow = -1, bestCol = -1, maxFlips = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int flips = countFlippableDiscs(r, c, 2);
                if (flips > maxFlips) {
                    maxFlips = flips;
                    bestRow = r;
                    bestCol = c;
                }
            }
        }
        if (bestRow != -1) {
            applyMove(bestRow, bestCol, 2);
            currentState.setLastMove(new int[]{bestRow, bestCol});
            currentState.getMoveHistory().add("White (AI): (" + (bestRow + 1) + ", " + (bestCol + 1) + ")");
        } else {
            currentState.getMoveHistory().add("White (AI) has no valid moves and passes.");
        }

        switchPlayer();
        checkGameOver();
        return currentState;
    }

    public GameState startNewGame(GameState state) {
        currentState = state;
        currentState.setBlackPlayerName(state.getBlackPlayerName());
        initializeBoard(currentState.getBoard());
        currentState.setCurrentPlayer(1);
        currentState.setMoveHistory(new ArrayList<>());
        currentState.setLastMove(null);
        currentState.setWinner(null);

        this.gameStartTime = LocalDateTime.now();
        this.gameEndTime = null;
        this.lastCodeSubmitDurationMin = 0.0;
        this.codeStartTime = null;
        this.codeEndTime = null;
        this.isCodeStarted = false;

        String timestamp = gameStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                "=== NEW GAME STARTED ===", "", "", "", "", timestamp
        ));

        return currentState;
    }

    public GameState resetGame() {
        currentState = new GameState();
        return currentState;
    }

    // ---------------- Code Writing Start ----------------
    /**
     * Call this from frontend when the user focuses or starts typing in the code editor.
     */
    public void markCodeStart() {
        if (!isCodeStarted) {
            codeStartTime = LocalDateTime.now();
            isCodeStarted = true;
            System.out.println("🟢 Code writing started at: " + codeStartTime);
        }
    }

    // ---------------- Code Submission ----------------
    public Map<String, Object> submitCode(String code) {
        Map<String, Object> result = new HashMap<>();

        if (currentState.getCurrentPlayer() != 1) {
            result.put("success", false);
            result.put("error", "Only Black Player can submit code!");
            return result;
        }

        long startTime = System.currentTimeMillis();

        try {
            if (code == null || code.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "Code cannot be empty!");
                return result;
            }

            // Mark code end time & compute code-writing duration (minutes)
            codeEndTime = LocalDateTime.now();
            if (codeStartTime != null) {
                Duration d = Duration.between(codeStartTime, codeEndTime);
                lastCodeSubmitDurationMin = d.toSeconds() / 60.0;
            }

            String encodedSource = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
            String encodedStdin = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));

            Map<String, Object> submission = new HashMap<>();
            submission.put("source_code", encodedSource);
            submission.put("language_id", 50);
            submission.put("stdin", encodedStdin);

            if (rapidApiKey == null || rapidApiKey.isEmpty() || rapidApiKey.equals("YOUR_RAPIDAPI_KEY_HERE")) {
                result.put("success", false);
                result.put("error", "RapidAPI key not configured!");
                return result;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-RapidAPI-Key", rapidApiKey);
            headers.set("X-RapidAPI-Host", rapidApiHost);

            String jsonBody = objectMapper.writeValueAsString(submission);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    JUDGE0_API_URL + "?base64_encoded=true&wait=true",
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                boolean isSuccess = false;

                // Status extraction (robust)
                Object statusField = body.get("status");
                if (statusField instanceof Map) {
                    Object idObj = ((Map<?, ?>) statusField).get("id");
                    if (idObj != null && idObj.toString().equals("3")) isSuccess = true;
                } else if (statusField != null && statusField.toString().equals("3")) {
                    isSuccess = true;
                }

                String stdout = decodeBase64Safe(body.get("stdout"));
                String stderr = decodeBase64Safe(body.get("stderr"));
                String compileOutput = decodeBase64Safe(body.get("compile_output"));

                result.put("success", isSuccess);
                result.put("stdout", stdout);
                result.put("stderr", stderr);
                result.put("compile_output", compileOutput);

                // On successful execution, log code and code-writing duration to sheet
                if (isSuccess) {
                    logCodeToSheet(code);
                    if (lastCodeSubmitDurationMin > 0) {
                        String player = currentState.getBlackPlayerName() != null ? currentState.getBlackPlayerName() : "Black";
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                                player, "", "", "Code Writing Duration (min)",
                                String.format("%.2f", lastCodeSubmitDurationMin), timestamp
                        ));
                    }
                    System.out.println("✅ Code executed successfully and logged to Google Sheet");
                } else {
                    System.out.println("❌ Code error, not logged (Judge0 status not accepted)");
                }
            } else {
                result.put("success", false);
                result.put("error", "Judge0 API responded with: " + response.getStatusCode());
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Error: " + e.getMessage());
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        // Add the actual submission+roundtrip time to lastCodeSubmitDurationMin (keeps both typing and submit time combined)
        lastCodeSubmitDurationMin += (endTime - startTime) / 1000.0 / 60.0;
        return result;
    }

    // ---------------- Question Upload ----------------
    public boolean uploadQuestionFile(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            List<String> lines = new ArrayList<>();
            String ln;
            while ((ln = reader.readLine()) != null) {
                if (!ln.trim().isEmpty()) lines.add(ln.trim());
            }
            if (lines.isEmpty()) return false;

            String question = lines.get(0);
            List<String> tests = new ArrayList<>();
            for (int i = 1; i < lines.size() && tests.size() < 5; i++) {
                tests.add(lines.get(i));
            }

            this.uploadedQuestion = question;
            this.uploadedTestCases = tests;
            System.out.println("✅ Uploaded question: " + question + " (testcases: " + tests.size() + ")");
            return true;
        } catch (IOException e) {
            System.err.println("❌ Failed to parse uploaded file: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getActiveQuestion() {
        Map<String, Object> resp = new HashMap<>();
        if (uploadedQuestion != null && !uploadedQuestion.isBlank()) {
            resp.put("question", uploadedQuestion);
            resp.put("testCases", uploadedTestCases);
        } else {
            resp.put("question", CODING_QUESTION);
            resp.put("testCases", Collections.emptyList());
        }
        return resp;
    }

    // ---------------- Game Over Logic ----------------
    /**
     * NOTE: per your request this method treats a situation where one player has no valid moves
     * as an immediate win for the opposing player (i.e. if blackHasMove==false -> white wins).
     * Additionally, when a winner is determined we log total game duration and code duration to sheet.
     */
    private void checkGameOver() {
        // 🛑 If the game already has a winner, stop checking/logging again
        if (currentState.getWinner() != null) {
            return;
        }

        boolean blackHasMove = hasValidMove(1);
        boolean whiteHasMove = hasValidMove(2);

        // Case 1: neither can move → game over
        if (!blackHasMove && !whiteHasMove) {
            int blackCount = 0, whiteCount = 0;
            for (int[] row : currentState.getBoard()) {
                for (int cell : row) {
                    if (cell == 1) blackCount++;
                    else if (cell == 2) whiteCount++;
                }
            }

            if (blackCount > whiteCount) {
                currentState.setWinner(1);
                currentState.getMoveHistory().add("🏆 Black Wins! (" + blackCount + " vs " + whiteCount + ")");
            } else if (whiteCount > blackCount) {
                currentState.setWinner(2);
                currentState.getMoveHistory().add("🏆 White Wins! (" + whiteCount + " vs " + blackCount + ")");
            } else {
                currentState.setWinner(0);
                currentState.getMoveHistory().add("🤝 It's a Draw!");
            }

            // Record game end time and durations once
            this.gameEndTime = LocalDateTime.now();

            if (gameStartTime != null) {
                long seconds = Duration.between(gameStartTime, gameEndTime).getSeconds();
                double minutes = seconds / 60.0;

                String player = currentState.getBlackPlayerName() != null ? currentState.getBlackPlayerName() : "Black";
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // 🧾 Log total game duration once
                GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                        player, "", "", "Total Game Duration (min)",
                        String.format("%.2f", minutes), timestamp
                ));

                // 🧾 Log total code submission duration once (if available)
                if (lastCodeSubmitDurationMin > 0) {
                    GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                            player, "", "", "Total Code Submission Duration (min)",
                            String.format("%.2f", lastCodeSubmitDurationMin), timestamp
                    ));
                }
            }
        }

        // Case 2: only black has no move → pass turn to white
        else if (!blackHasMove) {
            currentState.getMoveHistory().add("⚠️ Black has no valid moves — turn passes to White.");
            currentState.setCurrentPlayer(2);
        }

        // Case 3: only white has no move → pass turn to black
        else if (!whiteHasMove) {
            currentState.getMoveHistory().add("⚠️ White has no valid moves — turn passes to Black.");
            currentState.setCurrentPlayer(1);
        }
    }



    /**
     * Called when a winner has been set: records game end time, logs total game duration (minutes)
     * and also logs last code submission duration (minutes) if available.
     */
    private void finalizeAndLogGameDurations() {
        this.gameEndTime = LocalDateTime.now();

        if (gameStartTime != null) {
            long seconds = Duration.between(gameStartTime, gameEndTime).getSeconds();
            double minutes = seconds / 60.0;
            String player = currentState.getBlackPlayerName() != null ? currentState.getBlackPlayerName() : "Black";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                    player, "", "", "Total Game Duration (min)",
                    String.format("%.2f", minutes), timestamp
            ));

            if (lastCodeSubmitDurationMin > 0) {
                GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                        player, "", "", "Total Code Submission Duration (min)",
                        String.format("%.2f", lastCodeSubmitDurationMin), timestamp
                ));
            }
        }
    }

    // ---------------- Helper Methods ----------------
    private String decodeBase64Safe(Object data) {
        if (data == null) return "";
        try {
            byte[] dec = Base64.getDecoder().decode(data.toString());
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return data.toString();
        }
    }

    /**
     * Log the code run to sheet. Uses the active uploadedQuestion if present, otherwise the default.
     * Columns: PlayerName | moveRow | moveColumn | question | submittedCode | Timestamp
     */
    private void logCodeToSheet(String code) {
        String player = currentState.getBlackPlayerName() != null ? currentState.getBlackPlayerName() : "Unknown";
        String questionForLog = (uploadedQuestion != null && !uploadedQuestion.isBlank()) ? uploadedQuestion : CODING_QUESTION;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        GoogleSheetsUtil.appendLogRow(spreadsheetId, Arrays.asList(
                player, "", "", questionForLog, code, timestamp
        ));
    }

    private boolean applyMove(int row, int col, int player) {
        int[][] board = currentState.getBoard();
        if (board[row][col] != 0) return false;

        boolean valid = false;
        int opponent = (player == 1) ? 2 : 1;
        int[][] dirs = {
                {-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}
        };

        for (int[] d : dirs) {
            List<int[]> toFlip = new ArrayList<>();
            int r = row + d[0], c = col + d[1];
            while (r>=0 && r<8 && c>=0 && c<8 && board[r][c]==opponent) {
                toFlip.add(new int[]{r,c});
                r+=d[0]; c+=d[1];
            }
            if (r>=0 && r<8 && c>=0 && c<8 && board[r][c]==player && !toFlip.isEmpty()) {
                valid = true;
                for (int[] pos : toFlip) board[pos[0]][pos[1]]=player;
            }
        }

        if (valid) board[row][col]=player;
        currentState.setBoard(board);
        return valid;
    }

    private int countFlippableDiscs(int row,int col,int player){
        int[][] board=currentState.getBoard();
        if(board[row][col]!=0)return 0;
        int opponent=(player==1)?2:1,total=0;
        int[][] dirs={{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for(int[]d:dirs){
            int r=row+d[0],c=col+d[1],flips=0;
            while(r>=0&&r<8&&c>=0&&c<8&&board[r][c]==opponent){
                flips++;r+=d[0];c+=d[1];
            }
            if(r>=0&&r<8&&c>=0&&c<8&&board[r][c]==player&&flips>0)total+=flips;
        }
        return total;
    }

    private void switchPlayer(){currentState.setCurrentPlayer(currentState.getCurrentPlayer()==1?2:1);}
    private boolean hasValidMove(int p){for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(isValidMove(r,c,p))return true;return false;}
    private boolean isValidMove(int r,int c,int p){
        int[][]b=currentState.getBoard();
        if(b[r][c]!=0)return false;
        int o=(p==1)?2:1;
        int[][]d={{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for(int[]dir:d){
            int x=r+dir[0],y=c+dir[1];boolean f=false;
            while(x>=0&&x<8&&y>=0&&y<8&&b[x][y]==o){f=true;x+=dir[0];y+=dir[1];}
            if(f&&x>=0&&x<8&&y>=0&&y<8&&b[x][y]==p)return true;
        }
        return false;
    }
    private void initializeBoard(int[][]b){for(int i=0;i<8;i++)Arrays.fill(b[i],0);b[3][3]=2;b[3][4]=1;b[4][3]=1;b[4][4]=2;}
    public String getCodingQuestion(){return CODING_QUESTION;}
}


