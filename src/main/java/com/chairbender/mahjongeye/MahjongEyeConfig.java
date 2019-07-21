package com.chairbender.mahjongeye;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Data
@ConfigurationProperties(prefix = "mahjong-eye")
public class MahjongEyeConfig {
    private Path standardDir;
}
