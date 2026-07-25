package com.gym.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImageController {

    @GetMapping("/group_class/{fileName:.+}")
    public ResponseEntity<Resource> getGroupClassImage(@PathVariable String fileName) {
        Resource resource = new ClassPathResource("static/group_class/" + fileName);
        if (resource.exists()) {
            return ResponseEntity.ok()
                    .contentType(getMediaType(fileName))
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/trainer/{fileName:.+}")
    public ResponseEntity<Resource> getTrainerImage(@PathVariable String fileName) {
        Resource resource = new ClassPathResource("static/trainer/" + fileName);
        if (resource.exists()) {
            return ResponseEntity.ok()
                    .contentType(getMediaType(fileName))
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    private MediaType getMediaType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"))
            return MediaType.IMAGE_JPEG;
        if (fileName.endsWith(".png"))
            return MediaType.IMAGE_PNG;
        if (fileName.endsWith(".gif"))
            return MediaType.IMAGE_GIF;
        if (fileName.endsWith(".webp"))
            return MediaType.valueOf("image/webp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
