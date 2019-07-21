package com.chairbender.mahjongeye;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Allows converting an app.properties / app.yml string value to a Path
 */
@Component
@ConfigurationPropertiesBinding
public class StringToPathConverter implements Converter<String, Path> {

    @Override
    public Path convert(@NonNull String pathAsString) {
        return Paths.get(pathAsString);
    }
}
