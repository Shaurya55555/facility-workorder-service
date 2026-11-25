package com.shaurya.workorders.asset;

import com.shaurya.workorders.PageResponse;
import com.shaurya.workorders.asset.AssetDtos.AssetRequest;
import com.shaurya.workorders.asset.AssetDtos.AssetResponse;
import com.shaurya.workorders.error.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetRepository assets;

    public AssetController(AssetRepository assets) {
        this.assets = assets;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AssetResponse> list(@PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(assets.findAll(pageable), AssetResponse::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AssetResponse get(@PathVariable Long id) {
        return AssetResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public AssetResponse create(@Valid @RequestBody AssetRequest req) {
        return AssetResponse.from(assets.save(new Asset(req.name(), req.assetType(), req.location())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody AssetRequest req) {
        Asset asset = find(id);
        asset.update(req.name(), req.assetType(), req.location());   // dirty checking flushes on commit
        return AssetResponse.from(asset);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(@PathVariable Long id) {
        assets.delete(find(id));   // FK violation (asset still has work orders) -> 409 via GlobalExceptionHandler
        assets.flush();            // force the FK check inside the transaction so the handler sees it
    }

    private Asset find(Long id) {
        return assets.findById(id).orElseThrow(() -> new NotFoundException("Asset " + id + " not found"));
    }
}
