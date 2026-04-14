package com.rods.backtestingstrategies.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/server")
public class MonitorController {

    @GetMapping("/ping")
    public ResponseEntity<String> pingServer() {
        return ResponseEntity.ok("SERVER AWAKE :)");
    }

}
