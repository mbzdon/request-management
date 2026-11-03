package pl.bzdon.requestmanagement.endpoint.pagination;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "request-management.pagination")
public record PaginationProperties(
    @DefaultValue("10") int defaultSize,
    @DefaultValue("100") int maxSize) {

    public PaginationProperties {
        if (defaultSize < 1 || maxSize < defaultSize) {
            throw new IllegalArgumentException("Require 1 <= default-size <= max-size");
        }
    }

    public int resolve(Integer requestedSize) {
        return requestedSize == null ? defaultSize : Math.min(requestedSize, maxSize);
    }
}
