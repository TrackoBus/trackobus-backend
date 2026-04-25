package Group16.TrackoBus.backend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Group16.TrackoBus.backend.utils.BusIdUtil;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/tracking")
public class BusIdController {

    @PostMapping("/start-trip")
    public ResponseEntity<String> getMethodName() {
        return new ResponseEntity<>(BusIdUtil.generateUniqueBusId(), org.springframework.http.HttpStatus.OK);
    }

}
