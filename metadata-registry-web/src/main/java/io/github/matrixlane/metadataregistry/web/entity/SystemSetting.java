package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_setting")
@Getter
@Setter
@NoArgsConstructor
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 128)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 1024)
    private String value;

    public SystemSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

