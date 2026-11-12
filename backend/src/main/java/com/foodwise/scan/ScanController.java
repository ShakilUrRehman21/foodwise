package com.foodwise.scan;

import com.foodwise.common.ApiException;
import com.foodwise.common.ApiResponse;
import com.foodwise.common.enums.ScanType;
import com.foodwise.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchens/{kitchenId}/scans")
public class ScanController {

    private final ScanService scans;

    public ScanController(ScanService scans) {
        this.scans = scans;
    }

    /** Upload + process. Returns the session with candidates (status pending) or a failed session with a reason. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScanService.ScanSessionDto> upload(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                          @RequestPart("file") MultipartFile file,
                                                          @RequestParam(name = "scanType", defaultValue = "receipt") ScanType scanType)
            throws IOException {
        if (file.isEmpty()) {
            throw ApiException.validation("file", "Please choose a photo to scan.");
        }
        return ApiResponse.ok(scans.process(kitchenId, user.id(), file.getBytes(), scanType));
    }

    @GetMapping
    public ApiResponse<List<ScanService.ScanSessionDto>> history(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                                 @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(scans.history(kitchenId, user.id(), limit));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<ScanService.ScanSessionDto> get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                       @PathVariable UUID sessionId) {
        return ApiResponse.ok(scans.get(kitchenId, user.id(), sessionId));
    }

    @GetMapping("/{sessionId}/image")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                        @PathVariable UUID sessionId) {
        ScanService.ImageData img = scans.image(kitchenId, user.id(), sessionId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.noStore()).body(img.bytes());
    }

    @PostMapping("/{sessionId}/confirm")
    public ApiResponse<ScanService.ConfirmResult> confirm(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                          @PathVariable UUID sessionId, @Valid @RequestBody ScanService.ConfirmRequest req) {
        ScanService.ConfirmResult result = scans.confirm(kitchenId, user.id(), sessionId, req);
        return ApiResponse.ok(result, "Added " + result.created().size() + " items to your kitchen.");
    }

    @PostMapping("/{sessionId}/reject")
    public ApiResponse<ScanService.ScanSessionDto> reject(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID kitchenId,
                                                          @PathVariable UUID sessionId) {
        return ApiResponse.ok(scans.reject(kitchenId, user.id(), sessionId), "Scan discarded.");
    }
}
