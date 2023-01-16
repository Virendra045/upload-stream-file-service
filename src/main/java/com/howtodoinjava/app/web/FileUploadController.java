package com.howtodoinjava.app.web;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.http.ResponseEntity.ok;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.howtodoinjava.app.model.FileUploadCommand;

import java.util.List;
import java.util.Map;

import com.howtodoinjava.app.model.Student;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.FormPartEvent;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@SuppressWarnings("unused")
public class FileUploadController {

    @Autowired
    private ObjectMapper mapper;

    @PostMapping(value = "simple-form-upload-mvc", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> handleFileUploadForm(@RequestPart("name") String name,
                                                                    @RequestPart("file") MultipartFile file) {

        log.info("handling request parts: {}, {}", name, file);
        var result = Map.of(
                "name", name,
                "filename", file.getOriginalFilename()
        );
        return ok().body(result);
    }

    @PostMapping(value = "simple-form-upload", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> handleFileUploadForm(FileUploadCommand form) {

        log.info("uploading form data: {}", form);

        var result = Map.of(
                "name", form.getName(),
                "filename", form.getFile().filename()
        );
        return ok().body(result);
    }

    @PostMapping(value = "upload-with-request-parts", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> handleRequestParts(@RequestPart("name") String name,
                                                                  @RequestPart("file") FilePart file) {
        log.info("handling request parts: {}, {}", name, file);
        var result = Map.of(
                "name", name,
                "filename", file.filename()
        );
        return ok().body(result);
    }

    @PostMapping(value = "upload-with-multi-value-map", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Mono<List<String>>> handleMultiValueMap(@RequestBody Mono<MultiValueMap<String, Part>> parts) {
        log.debug("handling multi values: {}", parts);
        var partNames = parts.map
                        (p -> p.keySet().stream().map(key -> p.getFirst(key).name()).toList())
                .log();
        return ok().body(partNames);
    }

    @PostMapping("upload-file-with-part-events")
    public ResponseEntity<Flux<String>> handlePartsEvents(@RequestBody Flux<PartEvent> allPartsEvents) {

        var result = allPartsEvents.windowUntil(PartEvent::isLast)
                .concatMap(p -> {
                            return p.switchOnFirst((signal, partEvents) -> {
                                        if (signal.hasValue()) {
                                            PartEvent event = signal.get();
                                            if (event instanceof FormPartEvent formEvent) {
                                                String value = formEvent.value();
                                                Student student = null;
                                                try {
                                                    student = new ObjectMapper().readValue(value, Student.class);
                                                } catch (JsonProcessingException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                log.info("form value : {} === {}", value, student.toString());
                                                return Mono.just(value + "\n");
                                            } else if (event instanceof FilePartEvent fileEvent) {
                                                String filename = fileEvent.filename();
                                                log.info("upload file name : {}", filename);
                                                Flux<DataBuffer> contents = partEvents.map(PartEvent::content);
                                                var fileBytes = DataBufferUtils.join(contents)
                                                        .map(dataBuffer -> {
                                                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                                            dataBuffer.read(bytes);
                                                            log.info("chunks size =={}", bytes);
                                                            DataBufferUtils.release(dataBuffer);
                                                            return bytes;
                                                        });
                                                return fileBytes
                                                        .map(d -> {
                                                            log.info("file size =={}", d.length);
                                                            return filename;
                                                        });
                                            }
                                            return Mono.error(new RuntimeException("Unexpected event: " + event));
                                        }
                                        log.info("return default flux");
                                        return Flux.empty();
                                    }
                            );
                        }
                );

        return ok().body(result);
    }
}
