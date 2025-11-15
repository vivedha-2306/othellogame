package com.example.othello.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

public class GoogleSheetsUtil {

    private static Sheets sheetsService;
    private static final String APPLICATION_NAME = "Othello Game Logger";
    private static final String SERVICE_ACCOUNT_KEY = "/credentials.json"; // put your JSON in resources folder
    private static final String SHEET_NAME = "Logs"; // tab name in your spreadsheet

    private static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        if (sheetsService == null) {
            try (InputStream in = GoogleSheetsUtil.class.getResourceAsStream(SERVICE_ACCOUNT_KEY)) {
                if (in == null) {
                    throw new IOException("❌ Service account file not found at " + SERVICE_ACCOUNT_KEY);
                }

                sheetsService = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        JacksonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(
                                ServiceAccountCredentials.fromStream(in)
                                        .createScoped(List.of(SheetsScopes.SPREADSHEETS))
                        )
                )
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        }
        return sheetsService;
    }

    public static void appendLogRow(String spreadsheetId, List<Object> rowValues) {
        try {
            Sheets service = getSheetsService();
            ValueRange appendBody = new ValueRange().setValues(Arrays.asList(rowValues));
            service.spreadsheets().values()
                    .append(spreadsheetId, SHEET_NAME + "!A:F", appendBody)
                    .setValueInputOption("RAW")
                    .execute();
            System.out.println("✅ Row appended successfully to Google Sheet");
        } catch (IOException | GeneralSecurityException e) {
            System.err.println("❌ Failed to append to Google Sheet: " + e.getMessage());
        }
    }
}



