package com.example.othello.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private int[][] board;
    private int currentPlayer;
    private int[] lastMove;
    private Integer winner;
    private String blackPlayerName;
    private List<String> moveHistory = new ArrayList<>();

    // Coding sandbox fields
    private String currentQuestion;
    private String currentAnswer;

    public GameState() {
        board = new int[8][8];
        board[3][3] = 2;
        board[3][4] = 1;
        board[4][3] = 1;
        board[4][4] = 2;
        currentPlayer = 1;
    }

    public int[][] getBoard() {
        return board;
    }
    public void setBoard(int[][] board) {
        this.board = board;
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }
    public void setCurrentPlayer(int currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public int[] getLastMove() {
        return lastMove;
    }
    public void setLastMove(int[] lastMove) {
        this.lastMove = lastMove;
    }

    public Integer getWinner() {
        return winner;
    }
    public void setWinner(Integer winner) {
        this.winner = winner;
    }

    public String getBlackPlayerName() {
        return blackPlayerName;
    }
    public void setBlackPlayerName(String blackPlayerName) {
        this.blackPlayerName = blackPlayerName;
    }

    public List<String> getMoveHistory() {
        return moveHistory;
    }
    public void setMoveHistory(List<String> moveHistory) {
        this.moveHistory = moveHistory;
    }


}

