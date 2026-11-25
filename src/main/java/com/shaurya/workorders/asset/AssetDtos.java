package com.shaurya.workorders.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record AssetRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String assetType,
            @NotBlank @Size(max = 160) String location) {
    }

    public record AssetResponse(Long id, String name, String assetType, String location, Instant createdAt) {
        public static AssetResponse from(Asset a) {
            return new AssetResponse(a.getId(), a.getName(), a.getAssetType(), a.getLocation(), a.getCreatedAt());
        }
    }
}
