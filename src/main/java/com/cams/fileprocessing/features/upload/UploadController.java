package com.cams.fileprocessing.features.upload;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }



    @PostMapping
    public Mono<UploadResponse> requestUploadUrl(@RequestBody UploadRequest request) {
        return uploadService.createUploadUrl(request);
    }
}
