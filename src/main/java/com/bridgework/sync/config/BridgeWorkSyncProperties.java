package com.bridgework.sync.config;

import com.bridgework.sync.entity.PublicDataSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridgework.sync")
public class BridgeWorkSyncProperties {

    @NotBlank
    private String cron = "0 0 */1 * * *";

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(20);

    private String kricStationCodeFilePath;
    private String naverGeocodeApiKeyId;
    private String naverGeocodeApiKey;

    @Valid
    private List<SourceConfig> sources = new ArrayList<>();

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getKricStationCodeFilePath() {
        return kricStationCodeFilePath;
    }

    public void setKricStationCodeFilePath(String kricStationCodeFilePath) {
        this.kricStationCodeFilePath = kricStationCodeFilePath;
    }

    public String getNaverGeocodeApiKeyId() {
        return naverGeocodeApiKeyId;
    }

    public void setNaverGeocodeApiKeyId(String naverGeocodeApiKeyId) {
        this.naverGeocodeApiKeyId = naverGeocodeApiKeyId;
    }

    public String getNaverGeocodeApiKey() {
        return naverGeocodeApiKey;
    }

    public void setNaverGeocodeApiKey(String naverGeocodeApiKey) {
        this.naverGeocodeApiKey = naverGeocodeApiKey;
    }

    public List<SourceConfig> getSources() {
        return sources;
    }

    public void setSources(List<SourceConfig> sources) {
        this.sources = sources;
    }

    public static class SourceConfig {

        private boolean enabled;

        @NotNull
        private PublicDataSourceType sourceType;

        @NotBlank
        private String baseUrl;

        private String serviceKey;

        @Min(1)
        private int pageSize = 200;

        @Min(1)
        private int maxPages = 20;

        @NotBlank
        private String itemsJsonPointer = "/response/body/items/item";

        @NotBlank
        private String totalCountJsonPointer = "/response/body/totalCount";

        private String itemIdField;

        private Map<String, String> queryParams = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public PublicDataSourceType getSourceType() {
            return sourceType;
        }

        public void setSourceType(PublicDataSourceType sourceType) {
            this.sourceType = sourceType;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }

        public String getItemsJsonPointer() {
            return itemsJsonPointer;
        }

        public void setItemsJsonPointer(String itemsJsonPointer) {
            this.itemsJsonPointer = itemsJsonPointer;
        }

        public String getTotalCountJsonPointer() {
            return totalCountJsonPointer;
        }

        public void setTotalCountJsonPointer(String totalCountJsonPointer) {
            this.totalCountJsonPointer = totalCountJsonPointer;
        }

        public String getItemIdField() {
            return itemIdField;
        }

        public void setItemIdField(String itemIdField) {
            this.itemIdField = itemIdField;
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }

        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
        }
    }
}
