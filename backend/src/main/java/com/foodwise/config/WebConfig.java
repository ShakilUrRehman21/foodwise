package com.foodwise.config;

import com.foodwise.common.enums.DbEnum;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

/** Lets query/path parameters use the same lowercase enum values as JSON (e.g. {@code ?location=fridge}). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new LenientEnumConverterFactory());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class LenientEnumConverterFactory implements ConverterFactory<String, Enum> {
        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> {
                String value = source.trim();
                if (value.isEmpty()) {
                    return null;
                }
                if (DbEnum.class.isAssignableFrom(targetType)) {
                    return (T) DbEnum.fromDb((Class) targetType, value);
                }
                return (T) Enum.valueOf(targetType, value.toUpperCase(Locale.ROOT).replace('-', '_'));
            };
        }
    }
}
