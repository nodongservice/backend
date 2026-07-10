package com.bridgework.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return crypto().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return crypto().decrypt(dbData);
    }

    private PersonalDataEncryptionService crypto() {
        return ApplicationContextProvider.getBean(PersonalDataEncryptionService.class);
    }
}
