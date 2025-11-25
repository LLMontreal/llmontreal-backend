package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.service.LogDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LogController {

    private final LogDownloadService logDownloadService;

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        log.info("Request to download logs. StartDate: {}, EndDate: {}", startDate, endDate);

        byte[] csvData = logDownloadService.generateLogsCsv(startDate, endDate);

        String filename = "api_logs_" + Instant.now().toString().replaceAll(":", "-") + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        log.info("Logs downloaded successfully. Filename: {}", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }
}

